/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/
package co.nayan.imageprocessing.tflite

import android.graphics.RectF
import co.nayan.imageprocessing.classifiers.ImageUtils
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
import java.util.*

class TFLiteOCRDetectionModel {

    private val alphabets = "0123456789abcdefghijklmnopqrstuvwxyz"

    fun recognizeTexts(config: InputConfigDetector): ArrayList<Recognition> {
        synchronized(this) {
            val data = config.bitmap
            val recognitionInterpreter = getInterpreter(config)
            val recognitionResult = ByteBuffer.allocateDirect(config.numberOfDetection * 8)
            recognitionResult.order(ByteOrder.nativeOrder())

            val recognitionTensorImage = if (config.imageMean != null && config.imageStd != null) {
                ImageUtils.bitmapToTensorImageForRecognition(
                    data,
                    config.inputWidth,
                    config.inputHeight,
                    config.imageMean,
                    config.imageStd
                )
            } else null

            recognitionResult.rewind()
            val aiStartTime = System.currentTimeMillis()
            recognitionInterpreter?.run(recognitionTensorImage?.buffer, recognitionResult)
            Timber.e("AI Process time: ${(System.currentTimeMillis() - aiStartTime)} MS")

            var recognizedText = ""
            for (k in 0 until config.numberOfDetection) {
                val alphabetIndex = recognitionResult.getInt(k * 8)
                if (alphabetIndex in alphabets.indices)
                    recognizedText += alphabets[alphabetIndex]
            }

            val recognitions = ArrayList<Recognition>()
            recognitions.add(
                Recognition(
                    recognizedText,
                    recognizedText,
                    0.100F,
                    RectF()
                )
            )

            return recognitions
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelFile: File): MappedByteBuffer? {
        val startOffset: Long = 0
        val declaredLength = modelFile.length()
        val stream = FileInputStream(modelFile)
        val fileChannel = stream.channel
        val buffer = try {
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            fileChannel.close()
            stream.close()
        }

        return buffer
    }

    @Throws(IOException::class)
    private fun getInterpreter(config: InputConfigDetector): Interpreter? {
        val modelFile = config.modelMappedByteBuffer ?: loadModelFile(config.modelFile)
        val options = Interpreter.Options()
        options.setNumThreads(4)
        options.setUseXNNPACK(true)
        return modelFile?.let { Interpreter(it, options) }
    }
}
