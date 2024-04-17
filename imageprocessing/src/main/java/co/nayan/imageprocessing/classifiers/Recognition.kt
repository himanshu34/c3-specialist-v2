package co.nayan.imageprocessing.classifiers

import android.graphics.RectF

/**
 * An immutable result returned by a Classifier describing what was recognized.
 */
data class Recognition @JvmOverloads constructor(
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    var id: String,
    /**
     * Display name for the recognition.
     */
    var title: String,
    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     */
    var confidence: Float,
    /**
     * Optional location within the source image for the location of the recognized object.
     */
    var location: RectF,

    var detectedClass: Int? = 0
) {
    override fun toString(): String {
        val resultString = StringBuilder()
        if (id != null) resultString.append(id).append(" ")
        if (title != null) resultString.append(title).append(" ")
        if (confidence != null)
            resultString.append(String.format("(%.1f%%) ", confidence * 100.0f)).append(" ")
        if (location != null) resultString.append(location).append(" ")
        return resultString.toString().trim { it <= ' ' }
    }
}