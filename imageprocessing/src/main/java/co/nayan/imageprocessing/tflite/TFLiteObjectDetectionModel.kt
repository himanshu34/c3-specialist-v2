package co.nayan.imageprocessing.tflite

import android.graphics.Bitmap
import android.graphics.RectF
import co.nayan.imageprocessing.classifiers.ObjectDetector
import co.nayan.imageprocessing.classifiers.Recognition
import co.nayan.imageprocessing.model.InputConfigDetector
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteObjectDetectionModel : ObjectDetector {
    private lateinit var intValues: IntArray
    private var imgData: ByteBuffer? = null
    private var tfLite: Interpreter? = null

    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private lateinit var outputLocations: Array<Array<FloatArray>>

    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private lateinit var outputClasses: Array<FloatArray>

    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private lateinit var outputScores: Array<FloatArray>

    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private lateinit var numDetections: FloatArray
    private val tfliteMap = HashMap<String, Interpreter>()
    override fun recognizeObject(inputConfig: InputConfigDetector): List<Recognition> {
        synchronized(this) {
            val recognitions = ArrayList<Recognition>()
            try {
                // Initialize Object Detection Model
                if (tfliteMap.containsKey(inputConfig.modelFile.name)) tfLite =
                    tfliteMap[inputConfig.modelFile.name] else initializeObjectDetectionModel(
                    inputConfig
                )
                // Pre-allocate buffers.
                val numBytesPerChannel: Int = if (inputConfig.isQuantised) {
                    1 // Quantized
                } else {
                    4 // Floating point
                }
                imgData =
                    ByteBuffer.allocateDirect(inputConfig.inputWidth * inputConfig.inputHeight * 3 * numBytesPerChannel)
                imgData?.order(ByteOrder.nativeOrder())
                intValues = IntArray(inputConfig.inputWidth * inputConfig.inputHeight)
                outputLocations =
                    Array(1) { Array(inputConfig.numberOfDetection) { FloatArray(4) } }
                outputClasses = Array(1) { FloatArray(inputConfig.numberOfDetection) }
                outputScores = Array(1) { FloatArray(inputConfig.numberOfDetection) }
                numDetections = FloatArray(1)
                val scaledBitmap = Bitmap.createScaledBitmap(
                    inputConfig.bitmap,
                    inputConfig.inputWidth,
                    inputConfig.inputHeight,
                    false
                )
                scaledBitmap.getPixels(
                    intValues, 0, scaledBitmap.width,
                    0, 0, scaledBitmap.width, scaledBitmap.height
                )
                imgData?.rewind()
                for (i in 0 until inputConfig.inputWidth) {
                    for (j in 0 until inputConfig.inputWidth) {
                        val pixelValue = intValues[i * inputConfig.inputWidth + j]
                        if (inputConfig.isQuantised) {
                            // Quantized model
                            imgData?.put((pixelValue shr 16 and 0xFF).toByte())
                            imgData?.put((pixelValue shr 8 and 0xFF).toByte())
                            imgData?.put((pixelValue and 0xFF).toByte())
                        } else { // Float model
                            imgData?.putFloat(((pixelValue shr 16 and 0xFF) - inputConfig.imageMean!!) / inputConfig.imageStd!!)
                            imgData?.putFloat(((pixelValue shr 8 and 0xFF) - inputConfig.imageMean!!) / inputConfig.imageStd!!)
                            imgData?.putFloat(((pixelValue and 0xFF) - inputConfig.imageMean!!) / inputConfig.imageStd!!)
                        }
                    }
                }
                outputLocations =
                    Array(1) { Array(inputConfig.numberOfDetection) { FloatArray(4) } }
                outputClasses = Array(1) { FloatArray(inputConfig.numberOfDetection) }
                outputScores = Array(1) { FloatArray(inputConfig.numberOfDetection) }
                numDetections = FloatArray(1)
                val inputArray = arrayOf<Any?>(imgData)
                val outputMap: MutableMap<Int, Any> = HashMap()
                outputMap[0] = outputLocations
                outputMap[1] = outputClasses
                outputMap[2] = outputScores
                outputMap[3] = numDetections
                val aiStartTime = System.currentTimeMillis()
                tfLite!!.runForMultipleInputsOutputs(arrayOf(inputArray), outputMap)
                Timber.e("AI Process time: " + (System.currentTimeMillis() - aiStartTime) + " MS")

                // Show the best detections.
                // after scaling them back to the input size.
                // You need to use the number of detections from the output and not the NUM_DETECTONS variable
                // declared on top
                // because on some models, they don't always output the same total number of detections
                // For example, your model's NUM_DETECTIONS = 20, but sometimes it only outputs 16 predictions
                // If you don't use the output's numDetections, you'll get nonsensical data
                val numDetectionsOutput =
                    inputConfig.numberOfDetection.coerceAtMost(numDetections[0].toInt()) // cast from float to integer, use min for safety
                for (i in 0 until numDetectionsOutput) {
                    try {
                        val left: Float =
                            if (outputLocations[0][i][1] > 0) outputLocations[0][i][1] * inputConfig.inputWidth else 0F
                        val top: Float =
                            if (outputLocations[0][i][0] > 0) outputLocations[0][i][0] * inputConfig.inputWidth else 0F
                        val right: Float =
                            if (outputLocations[0][i][3] > 0) outputLocations[0][i][3] * inputConfig.inputWidth else 0F
                        val bottom: Float =
                            if (outputLocations[0][i][2] > 0) outputLocations[0][i][2] * inputConfig.inputWidth else 0F
                        val recognition = Recognition(
                            i.toString(),
                            getClassText(inputConfig, outputClasses[0][i]),
                            outputScores[0][i],
                            RectF(left, top, right, bottom)
                        )
                        recognitions.add(recognition)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (exception: Exception) {
                Timber.e("Exception occurred %s", exception.localizedMessage)
            }
            return recognitions
        }
    }

    private fun getClassText(config: InputConfigDetector, index: Float): String {
        return if (config.labelArray.size > index) {
            config.labelArray[index.toInt()]
        } else {
            index.toString()
        }
    }

    private fun initializeObjectDetectionModel(inputConfig: InputConfigDetector) {
        try {
            val modelFile: MappedByteBuffer? = inputConfig.modelMappedByteBuffer ?: loadModelFile(inputConfig.modelFile)
            val options = Interpreter.Options()
            options.setNumThreads(NUM_THREADS)
            options.setUseXNNPACK(true)
            tfLite = Interpreter(modelFile!!, options)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
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

    override fun close() {}

    companion object {
        // Number of threads in the java app
        private const val NUM_THREADS = 4
    }
}