package co.nayan.canvas.sandbox.utils

import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.canvas.sandbox.models.FilteredAnnotations
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object DragSplitMatcher {

    private const val DISTANCE_THRESHOLD = 30
    private const val SEGMENT_THRESHOLD = 0.25

    fun matchAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): Boolean {
        val threshold = annotationVariationThreshold?.let { it * 6 } ?: run { DISTANCE_THRESHOLD }
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        if (list1.size == list2.size) {
            for (bb1 in list1) {
                val bb2 = list2.find {
                    comparePaths(bb1, it, threshold) &&
                            compareSegments(bb1, it) &&
                            compareInputs(bb1, it)
                } ?: return false
                list2.remove(bb2)
            }
            return true
        }
        return false
    }

    private fun comparePaths(
        path1: AnnotationData,
        path2: AnnotationData,
        threshold: Int
    ): Boolean {
        val points1 = path1.points?.toMutableList() ?: mutableListOf()
        val points2 = path2.points?.toMutableList() ?: mutableListOf()

        points1.forEach { point1 ->
            val point2 = points2.find { point2 -> distance(point1, point2) < threshold }
                ?: return false
            points2.remove(point2)
        }

        return true
    }

    private fun compareSegments(path1: AnnotationData, path2: AnnotationData): Boolean {
        val segmentRatioList1 = path1.segmentRatioList ?: mutableListOf()
        val segmentRatioList2 = path2.segmentRatioList ?: mutableListOf()

        if (segmentRatioList1.size == segmentRatioList2.size) {
            segmentRatioList1.forEachIndexed { index, value ->
                if (abs(segmentRatioList2[index] - value) > SEGMENT_THRESHOLD) {
                    return false
                }
            }
        } else {
            return false
        }

        return true
    }

    private fun compareInputs(path1: AnnotationData, path2: AnnotationData): Boolean {
        val inputList1 = path1.inputList ?: mutableListOf()
        val inputList2 = path2.inputList ?: mutableListOf()

        if (inputList1.size == inputList2.size) {
            inputList1.forEachIndexed { index, value ->
                if (value != inputList2[index]) {
                    return false
                }
            }
        } else {
            return false
        }

        return true
    }

    private fun distance(p1: List<Float>, p2: List<Float>): Float =
        sqrt((p2[1] - p1[1]).pow(2) + (p2[0] - p1[0]).pow(2))

    private fun filterCorrectAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): List<AnnotationData> {
        val threshold = annotationVariationThreshold?.let { it * 6 } ?: run { DISTANCE_THRESHOLD }
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list1.iterator()) {
            forEach {
                val path2 = list2.find { path ->
                    comparePaths(it, path, threshold) &&
                            compareSegments(it, path) &&
                            compareInputs(it, path)
                }
                if (path2 == null) remove()
                else list2.remove(path2)
            }
        }

        return list1
    }

    private fun filterMissingAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): List<AnnotationData> {
        val threshold = annotationVariationThreshold?.let { it * 6 } ?: run { DISTANCE_THRESHOLD }
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list2.iterator()) {
            forEach {
                val path2 = list1.find { path ->
                    comparePaths(it, path, threshold) &&
                            compareSegments(it, path) &&
                            compareInputs(it, path)
                }
                if (path2 != null) remove()
            }
        }
        return list2
    }

    private fun filterInCorrectAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): List<AnnotationData> {
        val threshold = annotationVariationThreshold?.let { it * 6 } ?: run { DISTANCE_THRESHOLD }
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list1.iterator()) {
            forEach {
                val path2 = list2.find { path ->
                    comparePaths(it, path, threshold) &&
                            compareSegments(it, path) &&
                            compareInputs(it, path)
                }
                if (path2 != null) remove()
            }
        }
        return list1
    }

    fun filteredAnnotations(
        userAnnotation: List<AnnotationData>,
        correctAnnotation: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): FilteredAnnotations {
        val correctAnnotations =
            filterCorrectAnnotations(
                userAnnotation,
                correctAnnotation,
                annotationVariationThreshold
            )
        val incorrectAnnotations =
            filterInCorrectAnnotations(
                userAnnotation,
                correctAnnotation,
                annotationVariationThreshold
            )
        val missingAnnotations =
            filterMissingAnnotations(
                userAnnotation,
                correctAnnotation,
                annotationVariationThreshold
            )

        return FilteredAnnotations(correctAnnotations, incorrectAnnotations, missingAnnotations)
    }
}