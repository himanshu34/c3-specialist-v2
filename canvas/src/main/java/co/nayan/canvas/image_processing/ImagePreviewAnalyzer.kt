package co.nayan.canvas.image_processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.canvas.image_processing.utils.ImageUtils
import co.nayan.canvas.image_processing.utils.transformedObjectsRect
import co.nayan.imageprocessing.classifiers.Recognition
import co.nayan.imageprocessing.config.ImageProcessingType.LP
import co.nayan.imageprocessing.config.ImageProcessingType.OCR
import co.nayan.imageprocessing.config.ImageProcessingType.YOLO
import co.nayan.imageprocessing.model.InputConfigDetector
import co.nayan.imageprocessing.tflite.TFLiteLPDetectionAPIModel
import co.nayan.imageprocessing.tflite.TFLiteOCRDetectionModel
import co.nayan.imageprocessing.tflite.TFLiteObjectDetectionModel
import co.nayan.imageprocessing.tflite.YoloDetectorModel
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import javax.inject.Inject

class ImagePreviewAnalyzer @Inject constructor(@ApplicationContext private val context: Context) {

    private lateinit var cameraAiModel: CameraAIModel
    private lateinit var croppedBitmap: Bitmap
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = Matrix()
    private var listener: ObjectOfInterestListener? = null

    private var yoloDetectionModel: YoloDetectorModel? = null
    private var detectionModel: TFLiteObjectDetectionModel? = null
    private var lpModel: TFLiteLPDetectionAPIModel? = null
    private var ocrModel: TFLiteOCRDetectionModel? = null
    private val modelFileByteBuffer = HashMap<String, MappedByteBuffer>()
    private val analyzerJob = SupervisorJob()
    private val analyzerScope = CoroutineScope(Dispatchers.IO + analyzerJob)

    fun analyze(bitmap: Bitmap) = analyzerScope.launch {
        try {
            if (::cameraAiModel.isInitialized.not()) return@launch

            val fileName = "${cameraAiModel.name?.replace(" ", "_")}.tflite"
            val modelFile = context.getFileStreamPath(fileName)
            if (modelFile.exists().not()) return@launch
            if (cameraAiModel.width > 0 && cameraAiModel.height > 0) {
                croppedBitmap = Bitmap.createBitmap(
                    cameraAiModel.width,
                    cameraAiModel.height,
                    Bitmap.Config.ARGB_8888
                )

                frameToCropTransform = ImageUtils.getTransformationMatrix(
                    bitmap.width,
                    bitmap.height,
                    cameraAiModel.width,
                    cameraAiModel.height,
                    APPLY_ROTATION,
                    MAINTAIN_ASPECT
                )

                frameToCropTransform?.invert(cropToFrameTransform)
                processImage(modelFile, bitmap)
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            Timber.e(e)
            return@launch
        }
    }

    private suspend fun processImage(
        modelFile: File,
        bitmap: Bitmap
    ) = withContext(Dispatchers.IO) {
        val rgbFrameBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, false)
        val canvas = Canvas(croppedBitmap)
        frameToCropTransform?.let { canvas.drawBitmap(rgbFrameBitmap, it, null) }
        cameraAiModel.let {
            val modelMappedByteBuffer = try {
                if (modelFileByteBuffer.containsKey(modelFile.name))
                    modelFileByteBuffer[modelFile.name]
                else loadModelFile(modelFile)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            val config = getInputConfig(bitmap, it, modelFile, modelMappedByteBuffer)

            val processStartTime = System.currentTimeMillis()
            val processedResults = runAIModel(it, config)
            val timeRequiredToExecute = "${System.currentTimeMillis() - processStartTime} MS"
            Timber.e("results size: ${processedResults?.size} Time: " + timeRequiredToExecute)
            Timber.d("Time required to execute: $timeRequiredToExecute")
            processedResults?.let { results ->
                val resultLabels = results.map { result -> result.title }
                val filteredRules = cameraAiModel.cameraAiModelRules?.filter { rule ->
                    resultLabels.contains(rule.label)
                } ?: mutableListOf()
                val validObjects = arrayListOf<Pair<List<RectF>, String?>>()
                filteredRules.map { rule ->
                    val pair = when (cameraAiModel.category) {
                        OCR -> {
                            val ocrRect = results.filter { r -> r.confidence >= rule.confidence }
                                .filter { t ->
                                    val label = rule.label?.lowercase(Locale.getDefault())?.trim()
                                    ((t.title.lowercase(Locale.getDefault()).trim() == label)
                                            && t.title.length > 3)
                                }.transformedObjectsRect(cropToFrameTransform)
                            Pair(ocrRect, rule.label)
                        }

                        LP -> {
                            val rect = results.filter { r -> r.confidence >= rule.confidence }
                                .filter { t ->
                                    val label = rule.label?.lowercase(Locale.getDefault())?.trim()
                                    t.title.lowercase(Locale.getDefault()).trim() == label
                                }.transformedObjectsRect(cropToFrameTransform)
                            Pair(rect, rule.label)
                        }

                        else -> {
                            val objectRect = results.filter { r -> r.confidence >= rule.confidence }
                                .filter { t ->
                                    val label = rule.label?.lowercase(Locale.getDefault())?.trim()
                                    t.title.lowercase(Locale.getDefault()).trim() == label
                                }.filter { l ->
                                    val rect = l.location
                                    val width = bitmap.width * rect.width() / config.inputWidth
                                    val height = bitmap.height * rect.height() / config.inputHeight

                                    (height < (bitmap.height * 0.97) &&
                                            width < (bitmap.width * 0.97) &&
                                            height >= (rule.size?.height ?: 0)
                                            && width >= (rule.size?.width ?: 0))
                                }.transformedObjectsRect(cropToFrameTransform)
                            Pair(objectRect, rule.label)
                        }
                    }

                    if (pair.first.isEmpty()) return@map
                    else validObjects.add(pair)
                }

                listener?.onObjectDetected(validObjects)
            }
        }
    }

    private fun runAIModel(
        cameraAIModel: CameraAIModel,
        config: InputConfigDetector
    ): List<Recognition>? {
        return when (cameraAIModel.category) {
            YOLO -> {
                if (yoloDetectionModel == null) yoloDetectionModel = YoloDetectorModel()
                yoloDetectionModel!!.recognizeObject(config)
            }

            LP -> {
                if (lpModel == null) lpModel = TFLiteLPDetectionAPIModel()
                lpModel!!.recognizeLP(config)
            }

            OCR -> {
                if (ocrModel == null) ocrModel = TFLiteOCRDetectionModel()
                ocrModel!!.recognizeTexts(config)
            }

            else -> {
                if (detectionModel == null) detectionModel = TFLiteObjectDetectionModel()
                detectionModel!!.recognizeObject(config)
            }
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelFile: File): MappedByteBuffer? {
        val startOffset: Long = 0
        val declaredLength = modelFile.length()
        val stream = FileInputStream(modelFile)
        val fileChannel = stream.channel
        val buffer = try {
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)?.let {
                modelFileByteBuffer.put(modelFile.name, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
            null
        } finally {
            fileChannel.close()
            stream.close()
        }

        return buffer
    }

    private fun getInputConfig(
        bitmap: Bitmap,
        cameraAIModel: CameraAIModel,
        modelFile: File,
        modelMappedByteBuffer: MappedByteBuffer?
    ): InputConfigDetector {
        val minConfidenceCameraRule =
            cameraAIModel.cameraAiModelRules?.minByOrNull { it.confidence }
        return InputConfigDetector(
            bitmap,
            cameraAIModel.width,
            cameraAIModel.height,
            modelFile,
            cameraAIModel.labelArray,
            cameraAIModel.isQuantised,
            cameraAIModel.numberOfDetection,
            cameraAIModel.mean,
            cameraAIModel.standardDeviation,
            minConfidenceCameraRule?.confidence ?: 0.1f,
            modelMappedByteBuffer
        )
    }

    fun setObjectOfInterestListener(toSet: ObjectOfInterestListener) {
        listener = toSet
    }

    fun setCameraAIModel(cameraAiModel: CameraAIModel) {
        this.cameraAiModel = cameraAiModel
    }

    companion object {
        private const val MAINTAIN_ASPECT = false
        private const val APPLY_ROTATION = 0
    }
}

interface ObjectOfInterestListener {
    fun onObjectDetected(validObjects: ArrayList<Pair<List<RectF>, String?>>)
}