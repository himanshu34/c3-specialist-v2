package com.nayan.nayancamv2.ai

import android.graphics.Bitmap
import co.nayan.c3v2.core.models.driver_module.AIWorkFlowModel
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.c3v2.core.models.CameraAIModelRule
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
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayList

class ModelConfigManager {

    private var yoloDetectionModel: YoloDetectorModel? = null
    private var detectionModel: TFLiteObjectDetectionModel? = null
    private var lpModel: TFLiteLPDetectionAPIModel? = null
    private var ocrModel: TFLiteOCRDetectionModel? = null
    private val modelFileByteBuffer = HashMap<String, MappedByteBuffer>()

    fun getModelFile(file: File): MappedByteBuffer? {
        return modelFileByteBuffer[file.name] ?: loadModelFile(file)
    }

    @Throws(IOException::class)
    fun loadModelFile(modelFile: File): MappedByteBuffer? {
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

    fun runAIModel(aiModel: CameraAIModel, config: InputConfigDetector): List<Recognition> {
        return when (aiModel.category) {
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

    fun getInputConfig(
        bitmap: Bitmap,
        aiModel: CameraAIModel,
        modelFile: File,
        modelMappedByteBuffer: MappedByteBuffer?
    ): InputConfigDetector {
        val minConfidenceCameraRule = aiModel.cameraAiModelRules?.minByOrNull { it.confidence }
        return InputConfigDetector(
            bitmap,
            aiModel.width,
            aiModel.height,
            modelFile,
            aiModel.labelArray,
            aiModel.isQuantised,
            aiModel.numberOfDetection,
            aiModel.mean,
            aiModel.standardDeviation,
            minConfidenceCameraRule?.confidence ?: 0.1f,
            modelMappedByteBuffer
        )
    }

    fun getAIModelRules(
        workFlowList: List<AIWorkFlowModel>,
        workFlowIndex: Int,
        modelId: Int
    ): MutableList<CameraAIModelRule> {
        return workFlowList[workFlowIndex].let { aiWorkflow ->
            aiWorkflow.cameraAIModels.find {
                it.id == modelId
            }?.cameraAiModelRules ?: ArrayList()
        }
    }
}
