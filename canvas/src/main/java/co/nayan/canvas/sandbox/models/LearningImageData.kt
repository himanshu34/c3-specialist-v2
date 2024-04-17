package co.nayan.canvas.sandbox.models

import android.os.Parcelable
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.Record
import kotlinx.parcelize.Parcelize

@Parcelize
data class LearningImageData(
    val filteredAnnotations: FilteredAnnotations?,
    var filteredAnswers: FilteredAnswers?,
    val record: Record?
) : Parcelable

@Parcelize
data class FilteredAnswers(
    val correctAnswer: String,
    val userAnswer: String
) : Parcelable {
    fun isEmpty(): Boolean {
        return correctAnswer.isEmpty() && userAnswer.isEmpty()
    }
}

@Parcelize
data class FilteredAnnotations(
    val correctAnnotations: List<AnnotationData>,
    val incorrectAnnotations: List<AnnotationData>,
    val missingAnnotations: List<AnnotationData>
) : Parcelable