package co.nayan.canvas.sandbox.utils

import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.canvas.sandbox.models.FilteredAnnotations
import timber.log.Timber
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object CropMatcher {

    fun matchAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        imageWidth: Int,
        imageHeight: Int,
        annotationVariationThreshold: Int
    ): Boolean {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        val maxCoordinate = max(max(imageWidth, imageHeight), 100)
        val threshold = (annotationVariationThreshold * maxCoordinate / 100)
        Timber.e("Threshold --> $threshold")
        if (list1.size == list2.size) {
            for (bb1 in list1) {
                val bb2 = list2.find { similarBoundingBoxes(bb1, it, threshold) } ?: return false
                list2.remove(bb2)
            }
            return true
        }
        return false
    }

    private fun filterCorrectAnnotations(
        userAnnotation: List<AnnotationData>,
        correctAnnotation: List<AnnotationData>,
        imageWidth: Int,
        imageHeight: Int,
        annotationVariationThreshold: Int
    ): List<AnnotationData> {
        val list1 = userAnnotation.toMutableList()
        val list2 = correctAnnotation.toMutableList()

        val maxCoordinate = max(max(imageWidth, imageHeight), 100)
        val threshold = (annotationVariationThreshold * maxCoordinate / 100)
        with(list1.iterator()) {
            forEach { bb1 ->
                list2.find { bb2 -> similarBoundingBoxes(bb1, bb2, threshold) }?.let {
                    list2.remove(it)
                } ?: run { remove() }
            }
        }

        return list1
    }

    private fun filterMissingAnnotations(
        userAnnotation: List<AnnotationData>,
        correctAnnotation: List<AnnotationData>,
        imageWidth: Int,
        imageHeight: Int,
        annotationVariationThreshold: Int
    ): List<AnnotationData> {
        val list1 = userAnnotation.toMutableList()
        val list2 = correctAnnotation.toMutableList()

        val maxCoordinate = max(max(imageWidth, imageHeight), 100)
        val threshold = (annotationVariationThreshold * maxCoordinate / 100)
        with(list2.iterator()) {
            forEach { bb1 ->
                list1.find { bb2 -> similarBoundingBoxes(bb1, bb2, threshold) }?.let { remove() }
            }
        }

        return list2
    }

    private fun filterInCorrectAnnotations(
        userAnnotation: List<AnnotationData>,
        correctAnnotation: List<AnnotationData>,
        imageWidth: Int,
        imageHeight: Int,
        annotationVariationThreshold: Int
    ): List<AnnotationData> {
        val list1 = userAnnotation.toMutableList()
        val list2 = correctAnnotation.toMutableList()

        val maxCoordinate = max(max(imageWidth, imageHeight), 100)
        val threshold = (annotationVariationThreshold * maxCoordinate / 100)
        with(list1.iterator()) {
            forEach { bb1 ->
                list2.find { bb2 -> similarBoundingBoxes(bb1, bb2, threshold) }?.let { remove() }
            }
        }

        return list1
    }

    private fun similarBoundingBoxes(
        bb1: AnnotationData, bb2: AnnotationData, threshold: Int
    ): Boolean {
        if (bb1.type == bb2.type &&
            (bb1.input ?: "").equals(bb2.input ?: "", ignoreCase = true) &&
            bb1.tags.hasSameTags(bb2.tags)
        ) {
            val list1 = bb1.points?.toMutableList() ?: mutableListOf()
            val list2 = bb2.points?.toMutableList() ?: mutableListOf()
            for (p1 in list1) {
                val p2 = list2.find { p2 -> similarPoints(p1, p2, threshold) } ?: return false
                list2.remove(p2)
            }
            return true
        }
        return false
    }

    private fun similarPoints(p1: List<Float>, p2: List<Float>, threshold: Int): Boolean {
        return p1.distance(p2) <= threshold
    }

    private fun List<Float>.distance(point: List<Float>): Float =
        sqrt((point[1] - this[1]).pow(2) + (point[0] - this[0]).pow(2))

    fun filteredAnnotations(
        userAnnotation: List<AnnotationData>,
        correctAnnotation: List<AnnotationData>,
        imageWidth: Int,
        imageHeight: Int,
        annotationVariationThreshold: Int
    ): FilteredAnnotations {
        val correctAnnotations =
            filterCorrectAnnotations(
                userAnnotation,
                correctAnnotation,
                imageWidth,
                imageHeight,
                annotationVariationThreshold
            )
        val incorrectAnnotations =
            filterInCorrectAnnotations(
                userAnnotation,
                correctAnnotation,
                imageWidth,
                imageHeight,
                annotationVariationThreshold
            )
        val missingAnnotations =
            filterMissingAnnotations(
                userAnnotation,
                correctAnnotation,
                imageWidth,
                imageHeight,
                annotationVariationThreshold
            )

        return FilteredAnnotations(correctAnnotations, incorrectAnnotations, missingAnnotations)
    }

    private fun List<String>?.hasSameTags(toMatch: List<String>?): Boolean {
        val list1 = this ?: emptyList()
        val list2 = toMatch ?: emptyList()
        if (list1.isEmpty() && list2.isEmpty()) return true

        list1.forEach {
            if (list2.contains(it).not()) {
                return false
            }
        }

        return true
    }
}