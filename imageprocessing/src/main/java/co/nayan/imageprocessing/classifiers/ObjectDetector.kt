package co.nayan.imageprocessing.classifiers

import co.nayan.imageprocessing.model.InputConfigDetector

interface ObjectDetector {
    fun recognizeObject(inputConfig: InputConfigDetector): List<Recognition>?

    fun close()
}