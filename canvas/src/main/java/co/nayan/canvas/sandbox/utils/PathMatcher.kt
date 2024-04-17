package co.nayan.canvas.sandbox.utils

import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.canvas.sandbox.models.FilteredAnnotations
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object PathMatcher {

    fun matchAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        imageWidth: Int,
        imageHeight: Int,
        annotationVariationThreshold: Int?
    ): Boolean {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        val maxCoordinate = max(max(imageWidth, imageHeight), 100)
        val threshold = annotationVariationThreshold?.let {
            (it * maxCoordinate / 100)
        } ?: run { (5.0 * maxCoordinate / 100).toInt() }

        if (list1.size == 1 && list2.size == 1) {
            return comparePaths(list1[0], list2[0], threshold)
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

    private fun distance(p1: List<Float>, p2: List<Float>): Float =
        sqrt((p2[1] - p1[1]).pow(2) + (p2[0] - p1[0]).pow(2))

    private fun filterCorrectAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        threshold: Int
    ): List<AnnotationData> {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list1.iterator()) {
            forEach {
                val path2 = list2.find { path -> comparePaths(it, path, threshold) }
                if (path2 == null) {
                    remove()
                } else {
                    list2.remove(path2)
                }
            }
        }

        return list1
    }

    private fun filterMissingAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        threshold: Int
    ): List<AnnotationData> {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list2.iterator()) {
            forEach {
                val path2 = list1.find { path -> comparePaths(it, path, threshold) }
                if (path2 != null) {
                    remove()
                }
            }
        }
        return list2
    }

    private fun filterInCorrectAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        threshold: Int
    ): List<AnnotationData> {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list1.iterator()) {
            forEach {
                val path2 = list2.find { path -> comparePaths(it, path, threshold) }
                if (path2 != null) {
                    remove()
                }
            }
        }
        return list1
    }

    fun filteredAnnotations(
        userAnnotation: List<AnnotationData>,
        correctAnnotation: List<AnnotationData>,
        imageWidth: Int,
        imageHeight: Int,
        annotationVariationThreshold: Int?
    ): FilteredAnnotations {
        val maxCoordinate = max(max(imageWidth, imageHeight), 100)
        val threshold = annotationVariationThreshold?.let {
            (it * maxCoordinate / 100)
        } ?: run { (5.0 * maxCoordinate / 100).toInt() }

        val correctAnnotations =
            filterCorrectAnnotations(userAnnotation, correctAnnotation, threshold)
        val incorrectAnnotations =
            filterInCorrectAnnotations(userAnnotation, correctAnnotation, threshold)
        val missingAnnotations =
            filterMissingAnnotations(userAnnotation, correctAnnotation, threshold)

        return FilteredAnnotations(correctAnnotations, incorrectAnnotations, missingAnnotations)
    }
}