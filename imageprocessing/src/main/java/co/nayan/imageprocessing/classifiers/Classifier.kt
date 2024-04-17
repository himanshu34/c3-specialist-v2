package co.nayan.imageprocessing.classifiers

import android.graphics.Bitmap
import co.nayan.imageprocessing.model.InputConfigDetector

/**
 * Generic interface for interacting with different recognition engines.
 */
interface Classifier {
    fun recognizeImage(bitmap: Bitmap?): List<Recognition>?
    fun recognizeLP(inputConfigDetector: InputConfigDetector): List<Recognition>?

    fun enableStatLogging(debug: Boolean)
    fun getStatString(): String?
}