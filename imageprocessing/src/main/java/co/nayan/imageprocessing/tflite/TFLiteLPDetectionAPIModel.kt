package co.nayan.imageprocessing.tflite

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Trace
import co.nayan.imageprocessing.classifiers.Classifier
import co.nayan.imageprocessing.classifiers.Recognition
import co.nayan.imageprocessing.model.InputConfigDetector
import co.nayan.imageprocessing.model.LpProbs
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.Objects
import kotlin.math.pow

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * where you can find the training code.
 *
 *
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
class TFLiteLPDetectionAPIModel : Classifier {
    private var isModelQuantized = false

    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private lateinit var outputLocations: Array<Array<Array<FloatArray>>>
    private var tfLite: Interpreter? = null

    /**
     * Memory-map the model file in Assets.
     */
    private fun initializeLPDetectionModel(inputConfig: InputConfigDetector) {
        try {
            val modelFile: MappedByteBuffer? =
                inputConfig.modelMappedByteBuffer ?: loadModelFile(inputConfig.modelFile)
            val options = Interpreter.Options()
            options.setNumThreads(NUM_THREADS)
            options.setUseXNNPACK(true)
            tfLite = Interpreter(modelFile!!, options)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        isModelQuantized = inputConfig.isQuantised
        outputLocations = Array(1) { Array(27) { Array(41) { FloatArray(8) } } }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelFile: File): MappedByteBuffer? {
        val startOffset: Long = 0
        val declaredLength = modelFile.length()
        val stream = FileInputStream(modelFile)
        val fileChannel = stream.channel
        var buffer: MappedByteBuffer? = null
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            fileChannel.close()
            stream.close()
        }
        return buffer
    }

    private fun getSelectedLabels(lpProbs: ArrayList<LpProbs>): ArrayList<LpProbs> {
        val selectedLabels = ArrayList<LpProbs>()
        for (label in lpProbs) {
            var nonOverlap = true
            for (selectedLabel in selectedLabels) {
                if (iouLabels(label, selectedLabel) > 0.1.toFloat()) {
                    nonOverlap = false
                    break
                }
            }
            if (nonOverlap) {
                selectedLabels.add(label)
            }
        }
        return selectedLabels
    }

    private fun iouLabels(label: LpProbs, selectedLabel: LpProbs): Float {
        return iou(
            label.tlX, label.tlY, label.brX, label.brY,
            selectedLabel.tlX, selectedLabel.tlY, selectedLabel.brX, selectedLabel.brY
        )
    }

    private fun iou(
        labelTlX: Float,
        labelTlY: Float,
        labelBrX: Float,
        labelBrY: Float,
        selectedLabelTlX: Float,
        selectedLabelTlY: Float,
        selectedLabelBrX: Float,
        selectedLabelBrY: Float
    ): Float {
        val w1 = labelBrX - labelTlX
        val w2 = selectedLabelBrX - selectedLabelTlX
        val h1 = labelBrY - labelTlY
        val h2 = selectedLabelBrY - selectedLabelTlY
        return if (w1 > 0 && w2 > 0 && h1 > 0 && h2 > 0) {
            val intersectionW =
                (selectedLabelBrX.coerceAtMost(labelBrX) - selectedLabelTlX.coerceAtLeast(
                    labelTlX
                )).toDouble().coerceAtLeast(0.0).toFloat()
            val intersectionH =
                (selectedLabelBrY.coerceAtMost(labelBrY) - selectedLabelTlY.coerceAtLeast(
                    labelTlY
                )).toDouble().coerceAtLeast(0.0).toFloat()
            val intersectionArea = intersectionH * intersectionW
            val area1 = w1 * h1
            val area2 = w2 * h2
            val unionArea = area1 + area2 - intersectionArea
            intersectionArea / unionArea
        } else {
            -1.0f
        }
    }

    override fun recognizeImage(bitmap: Bitmap?): List<Recognition>? {
        return null
    }

    override fun recognizeLP(inputConfigDetector: InputConfigDetector): List<Recognition> {
        synchronized(this) {
            val recognitions = ArrayList<Recognition>()
            val initStartTime = System.currentTimeMillis()
            try {
                if (tfLite == null) initializeLPDetectionModel(inputConfigDetector)
                Timber.e("init Process time: " + (System.currentTimeMillis() - initStartTime) + " MS")
                val preProcessStartTime = System.currentTimeMillis()
                val inputWidth = inputConfigDetector.bitmap.width
                val inputHeight = inputConfigDetector.bitmap.height
                val numBytesPerChannel = 4

                // Log this method so that it can be analyzed with systrace.
                Trace.beginSection("recognizeImage")
                Trace.beginSection("preprocessBitmap")
                // Preprocess the image data from 0-255 int to normalized float based
                // on the provided parameters.
                val ratio =
                    inputWidth.coerceAtLeast(inputHeight).toFloat() / inputWidth.coerceAtMost(
                        inputHeight
                    )
                val side = (ratio * 288).toInt()
                val boundDim = (side + side % 2.0.pow(4.0)).coerceAtMost(608.0).toInt()
                val minDimImg = inputWidth.coerceAtMost(inputHeight)
                val factor = boundDim.toFloat() / minDimImg
                val netStep = 2.0.pow(4.0).toInt()
                var w = (inputWidth * factor).toInt()
                var h = (inputHeight * factor).toInt()
                if (w % netStep != 0) w += netStep - w % netStep
                if (h % netStep != 0) h += netStep - h % netStep
                val array = IntArray(4)
                array[0] = 1
                array[1] = h
                array[2] = w
                array[3] = 3
                tfLite!!.resizeInput(0, array)
                val imgData = ByteBuffer.allocateDirect(w * h * 3 * numBytesPerChannel)
                imgData.order(ByteOrder.nativeOrder())
                imgData.rewind()
                val scaledBitmap =
                    Bitmap.createScaledBitmap(inputConfigDetector.bitmap, w, h, false)

                // Config values.
                val intValues = IntArray(w * h)
                scaledBitmap.getPixels(
                    intValues,
                    0,
                    scaledBitmap.width,
                    0,
                    0,
                    scaledBitmap.width,
                    scaledBitmap.height
                )
                for (i in 0 until w) {
                    for (j in 0 until h) {
                        val pixelValue = intValues[i * h + j]
                        if (isModelQuantized) {
                            // Quantized model
                            imgData.put((pixelValue shr 16 and 0xFF).toByte())
                            imgData.put((pixelValue shr 8 and 0xFF).toByte())
                            imgData.put((pixelValue and 0xFF).toByte())
                        } else { // Float model
                            imgData.putFloat((pixelValue shr 16 and 0xFF).toFloat() / 255)
                            imgData.putFloat((pixelValue shr 8 and 0xFF).toFloat() / 255)
                            imgData.putFloat((pixelValue and 0xFF).toFloat() / 255)
                        }
                    }
                }
                Trace.endSection() // preprocessBitmap
                // Copy the input data into TensorFlow.
                Trace.beginSection("feed")
                val size1 = 1
                val size2 = h / netStep
                val size3 = w / netStep
                val size4 = 8
                outputLocations =
                    Array(size1) { Array(size2) { Array(size3) { FloatArray(size4) } } }
                val inputArray = arrayOf<Any>(imgData)
                val outputMap: MutableMap<Int, Any> = HashMap()
                outputMap[0] = outputLocations
                Timber.e("Pre Process time: " + (System.currentTimeMillis() - preProcessStartTime) + " MS")
                Trace.endSection()
                // Run the inference call.
                Trace.beginSection("run")
                val aiStartTime = System.currentTimeMillis()
                tfLite!!.runForMultipleInputsOutputs(inputArray, outputMap)
                Timber.e("AI Process time: " + (System.currentTimeMillis() - aiStartTime) + " MS")
                Trace.endSection()
                val postProcessStartTime = System.currentTimeMillis()
                val probs = Array(size1) { Array(size2) { FloatArray(size3) } }
                for (i in 0 until size2) {
                    for (j in 0 until size3) {
                        probs[0][i][j] =
                            (Objects.requireNonNull(outputMap[0]) as Array<Array<Array<FloatArray>>>)[0][i][j][0]
                    }
                }
                val affines = Array(size1) { Array(size2) { Array(size3) { FloatArray(6) } } }
                for (i in 0 until size2) {
                    for (j in 0 until size3) {
                        for (k in 0..5) {
                            affines[0][i][j][k] = (Objects.requireNonNull(
                                outputMap[0]
                            ) as Array<Array<Array<FloatArray>>>)[0][i][j][k + 2]
                        }
                    }
                }
                val threshold = 0.5f
                val lpProbs = ArrayList<LpProbs>()
                for (i in 0 until size2) {
                    for (j in 0 until size3) {
                        if (probs[0][i][j] < threshold) {
                            continue
                        }
                        val a = Array(2) { FloatArray(3) }
                        a[0][0] = 0f.coerceAtLeast(affines[0][i][j][0])
                        a[0][1] = affines[0][i][j][1]
                        a[0][2] = affines[0][i][j][2]
                        a[1][0] = affines[0][i][j][3]
                        a[1][1] = 0f.coerceAtLeast(affines[0][i][j][4])
                        a[1][2] = affines[0][i][j][5]
                        val m = j + 0.5f
                        val n = i + 0.5f
                        val base = arrayOf(
                            floatArrayOf(-0.5f, 0.5f, 0.5f, -0.5f),
                            floatArrayOf(-0.5f, -0.5f, 0.5f, 0.5f),
                            floatArrayOf(1f, 1f, 1f, 1f)
                        )
                        val pts = Array(2) { FloatArray(4) }
                        pts[0][0] =
                            (a[0][0] * base[0][0] + a[0][1] * base[1][0] + a[0][2] * base[2][0] * 7.75f + m) / size3
                        pts[0][1] =
                            (a[0][0] * base[0][1] + a[0][1] * base[1][1] + a[0][2] * base[2][1] * 7.75f + m) / size3
                        pts[0][2] =
                            (a[0][0] * base[0][2] + a[0][1] * base[1][2] + a[0][2] * base[2][2] * 7.75f + m) / size3
                        pts[0][3] =
                            (a[0][0] * base[0][3] + a[0][1] * base[1][3] + a[0][2] * base[2][3] * 7.75f + m) / size3
                        pts[1][0] =
                            (a[1][0] * base[0][0] + a[1][1] * base[1][0] + a[1][2] * base[2][0] * 7.75f + n) / size2
                        pts[1][1] =
                            (a[1][0] * base[0][1] + a[1][1] * base[1][1] + a[1][2] * base[2][1] * 7.75f + n) / size2
                        pts[1][2] =
                            (a[1][0] * base[0][2] + a[1][1] * base[1][2] + a[1][2] * base[2][2] * 7.75f + n) / size2
                        pts[1][3] =
                            (a[1][0] * base[0][3] + a[1][1] * base[1][3] + a[1][2] * base[2][3] * 7.75f + n) / size2
                        var tlX = 100f
                        var tlY = 100f
                        var brX = -100f
                        var brY = -100f
                        for (k in 0..3) {
                            tlX = tlX.coerceAtMost(pts[0][k])
                            tlY = tlY.coerceAtMost(pts[1][k])
                            brX = brX.coerceAtLeast(pts[0][k])
                            brY = brY.coerceAtLeast(pts[1][k])
                        }
                        lpProbs.add(LpProbs(probs[0][i][j], tlX, tlY, brX, brY))

                        //  Log.d("points:", tlX + "," + tlY + "," + brX + "," + brY + ", (" + i + "," + j + ")");
                    }
                    // final_labels =
                }
                Collections.sort(lpProbs, SortByProbs())
                val selectedLabels = getSelectedLabels(lpProbs)
                for (i in selectedLabels.indices) {
                    try {
                        val detection = RectF(
                            selectedLabels[i].tlX * inputWidth,
                            selectedLabels[i].tlY * inputHeight,
                            selectedLabels[i].brX * inputWidth,
                            selectedLabels[i].brY * inputHeight
                        )
                        // SSD Mobilenet V1 Model assumes class 0 is background class
                        // in label file and class labels start from 1 to number_of_classes+1,
                        // while outputClasses correspond to class index from 0 to number_of_classes
                        recognitions.add(
                            Recognition(
                                i.toString(),
                                "LP",
                                selectedLabels[i].prob,
                                detection
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Trace.endSection()
                Timber.e("Post Process time: " + (System.currentTimeMillis() - postProcessStartTime) + " MS")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return recognitions
        }
    }

    override fun getStatString(): String {
        return ""
    }

    internal class SortByProbs : Comparator<LpProbs> {
        // Used for sorting in ascending order of roll name
        override fun compare(a: LpProbs, b: LpProbs): Int {
            return b.prob.compareTo(a.prob)
        }
    }

    override fun enableStatLogging(debug: Boolean) {}

    companion object {
        // Number of threads in the java app
        private const val NUM_THREADS = 4
    }
}