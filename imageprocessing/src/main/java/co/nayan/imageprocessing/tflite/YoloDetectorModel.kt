/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
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
import java.util.PriorityQueue

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
class YoloDetectorModel : ObjectDetector {
    // Config values.
    // Pre-allocated buffers.
    private lateinit var intValues: IntArray
    private var tfLite: Interpreter? = null
    private val tfliteMap = HashMap<String, Interpreter>()
    override fun recognizeObject(inputConfig: InputConfigDetector): List<Recognition> {
        synchronized(this) {
            var recognitions = ArrayList<Recognition>()
            try {
                // Initialize Yolo Detection Model
                if (tfliteMap.containsKey(inputConfig.modelFile.name)) tfLite =
                    tfliteMap[inputConfig.modelFile.name] else initializeYoloModel(inputConfig)
                // Pre-allocate buffers.
                val numBytesPerChannel: Int = if (inputConfig.isQuantised) {
                    1 // Quantized
                } else {
                    4 // Floating point
                }
                val imgData =
                    ByteBuffer.allocateDirect(inputConfig.inputWidth * inputConfig.inputHeight * 3 * numBytesPerChannel)
                imgData.order(ByteOrder.nativeOrder())
                intValues = IntArray(inputConfig.inputWidth * inputConfig.inputHeight)
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
                val byteBuffer = convertBitmapToByteBuffer(
                    scaledBitmap,
                    inputConfig.inputWidth,
                    inputConfig.inputHeight
                )
                Timber.e(byteBuffer.toString())

                val detections = getDetectionsForTiny(
                    byteBuffer,
                    scaledBitmap,
                    inputConfig.score,
                    inputConfig.labelArray
                )
                recognitions = nms(inputConfig.labelArray, detections)
            } catch (exception: Exception) {
                Timber.e("Exception occurred %s", exception.localizedMessage)
            }
            return recognitions
        }

    }

    // non maximum suppression
    private fun nms(labels: List<String?>, list: ArrayList<Recognition>): ArrayList<Recognition> {
        val nmsList = ArrayList<Recognition>()
        for (k in labels.indices) {
            // 1. find max confidence per class
            val pq = PriorityQueue(50) { lhs: Recognition?, rhs: Recognition? ->
                rhs!!.confidence.compareTo(lhs!!.confidence)
            }
            for (i in list.indices) {
                val (_, _, _, _, detectedClass) = list[i]
                if (detectedClass != null && detectedClass == k) {
                    pq.add(list[i])
                }
            }

            // 2.do non maximum suppression
            while (pq.size > 0) {
                //insert detection with max confidence
                val a = arrayOfNulls<Recognition>(pq.size)
                val detections = pq.toArray(a)
                val max = detections[0]!!
                nmsList.add(max)
                pq.clear()
                for (j in 1 until detections.size) {
                    val detection = detections[j]
                    val b = detection!!.location
                    if (boxIOU(max.location, b) < mNmsThresh) {
                        pq.add(detection)
                    }
                }
            }
        }
        return nmsList
    }

    private var mNmsThresh = 0.6f
    private fun boxIOU(a: RectF, b: RectF): Float {
        return boxIntersections(a, b) / boxUnion(a, b)
    }

    private fun boxIntersections(a: RectF, b: RectF): Float {
        val w = overlap(
            (a.left + a.right) / 2, a.right - a.left,
            (b.left + b.right) / 2, b.right - b.left
        )
        val h = overlap(
            (a.top + a.bottom) / 2, a.bottom - a.top,
            (b.top + b.bottom) / 2, b.bottom - b.top
        )
        return if (w < 0 || h < 0) 0F else w * h
    }

    private fun boxUnion(a: RectF, b: RectF): Float {
        val i = boxIntersections(a, b)
        return (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i
    }

    private fun overlap(x1: Float, w1: Float, x2: Float, w2: Float): Float {
        val l1 = x1 - w1 / 2
        val l2 = x2 - w2 / 2
        val left = l1.coerceAtLeast(l2)
        val r1 = x1 + w1 / 2
        val r2 = x2 + w2 / 2
        val right = r1.coerceAtMost(r2)
        return right - left
    }

    /**
     * Writes Image data into a `ByteBuffer`.
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * width * height * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(width * height)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until width) {
            for (j in 0 until height) {
                val `val` = intValues[pixel++]
                byteBuffer.putFloat((`val` shr 16 and 0xFF) / 255.0f)
                byteBuffer.putFloat((`val` shr 8 and 0xFF) / 255.0f)
                byteBuffer.putFloat((`val` and 0xFF) / 255.0f)
            }
        }
        return byteBuffer
    }

    /**
     * For yolov4-tiny, the situation would be a little different from the yolov4, it only has two
     * output. Both has three dimension. The first one is a tensor with dimension [1, 2535,4], containing all the bounding boxes.
     * The second one is a tensor with dimension [1, 2535, class_num], containing all the classes score.
     *
     * @param byteBuffer input ByteBuffer, which contains the image information
     * @param bitmap     pixel density used to resize the output images
     * @return an array list containing the recognitions
     */
    private fun getDetectionsForTiny(
        byteBuffer: ByteBuffer,
        bitmap: Bitmap,
        scoreThreshold: Float,
        labels: List<String>
    ): ArrayList<Recognition> {
        val detections = ArrayList<Recognition>()
        val outputMap: MutableMap<Int, Any> = HashMap()
        outputMap[0] = Array(1) {
            Array(
                OUTPUT_WIDTH_TINY[0]
            ) { FloatArray(4) }
        }
        outputMap[1] = Array(1) {
            Array(
                OUTPUT_WIDTH_TINY[1]
            ) { FloatArray(labels.size) }
        }
        val inputArray = arrayOf<Any>(byteBuffer)
        tfLite!!.runForMultipleInputsOutputs(inputArray, outputMap)
        val gridWidth = OUTPUT_WIDTH_TINY[0]
        val boxes = outputMap[0] as? Array<Array<FloatArray>>?
        val outScore = outputMap[1] as? Array<Array<FloatArray>>?
        for (i in 0 until gridWidth) {
            var maxClass = 0f
            var detectedClass = -1
            val classes = FloatArray(labels.size)
            System.arraycopy(
                (outScore?.get(0) ?: arrayOf(floatArrayOf()))[i],
                0,
                classes,
                0,
                labels.size
            )
            for (c in labels.indices) {
                if (classes[c] > maxClass) {
                    detectedClass = c
                    maxClass = classes[c]
                }
            }
            val score = maxClass
            if (score > scoreThreshold) {
                val xPos: Float = (boxes?.get(0) ?: arrayOf(floatArrayOf()))[i][0]
                val yPos = boxes!![0][i][1]
                val w = boxes[0][i][2]
                val h = boxes[0][i][3]
                val rectF = RectF(
                    0f.coerceAtLeast(xPos - w / 2),
                    0f.coerceAtLeast(yPos - h / 2),
                    (bitmap.width - 1).toFloat().coerceAtMost(xPos + w / 2),
                    (bitmap.height - 1).toFloat().coerceAtMost(yPos + h / 2)
                )
                detections.add(
                    Recognition(
                        "" + i,
                        labels[detectedClass],
                        score,
                        rectF,
                        detectedClass
                    )
                )
            }
        }
        return detections
    }

    private fun initializeYoloModel(inputConfig: InputConfigDetector) {
        try {
            val modelFile: MappedByteBuffer? =
                inputConfig.modelMappedByteBuffer ?: loadModelFile(inputConfig.modelFile)
            val options = Interpreter.Options()
            options.setNumThreads(NUM_THREADS)
            tfLite = Interpreter(modelFile!!, options)
            tfliteMap[inputConfig.modelFile.name] = tfLite!!
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

        // config yolov4 tiny
        private val OUTPUT_WIDTH_TINY = intArrayOf(2535, 2535)
        private const val BATCH_SIZE = 1
        private const val PIXEL_SIZE = 3
    }
}