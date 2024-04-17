package co.nayan.canvas.sandbox.utils

import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.canvas.sandbox.models.FilteredAnnotations
import timber.log.Timber
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object LaneMatcher {

    private const val SQ_EQUALITY_THRESHOLD = 30
    private const val CURVE_POINTS_COUNT = 50

    fun matchAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): Boolean {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        if (list1.size == list2.size) {
            for (line1 in list1) {
                list2.find { line2 ->
                    comparePoints(
                        line1.points,
                        line2.points,
                        annotationVariationThreshold
                    )
                } ?: return false
            }
            return true
        } else {
            return false
        }
    }

    private fun filterCorrectAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): List<AnnotationData> {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list1.iterator()) {
            forEach {
                val line2 = list2.find { line2 ->
                    comparePoints(
                        it.points,
                        line2.points,
                        annotationVariationThreshold
                    )
                }
                if (line2 == null) {
                    remove()
                } else {
                    list2.remove(line2)
                }
            }
        }

        return list1
    }

    private fun filterMissingAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): List<AnnotationData> {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list2.iterator()) {
            forEach {
                val line2 = list1.find { line2 ->
                    comparePoints(
                        it.points,
                        line2.points,
                        annotationVariationThreshold
                    )
                }
                if (line2 != null) {
                    remove()
                }
            }
        }

        return list2
    }

    private fun filterIncorrectAnnotations(
        userAnnotations: List<AnnotationData>,
        correctAnnotations: List<AnnotationData>,
        annotationVariationThreshold: Int?
    ): List<AnnotationData> {
        val list1 = userAnnotations.toMutableList()
        val list2 = correctAnnotations.toMutableList()

        with(list1.iterator()) {
            forEach {
                val line2 = list2.find { line2 ->
                    comparePoints(
                        it.points,
                        line2.points,
                        annotationVariationThreshold
                    )
                }
                if (line2 != null) {
                    remove()
                }
            }
        }

        return list1
    }

    private fun comparePoints(
        controlPoints1: List<List<Float>>?,
        controlPoints2: List<List<Float>>?,
        annotationVariationThreshold: Int?
    ): Boolean {
        val threshold =
            annotationVariationThreshold?.let { it * 6 } ?: run { SQ_EQUALITY_THRESHOLD }
        val curve1 = equidistantPoints(controlPoints1, CURVE_POINTS_COUNT)
        val curve2 = equidistantPoints(controlPoints2, CURVE_POINTS_COUNT)
        val d1 = maxDistance(curve1, curve2, threshold)
        val curve2Inverse = equidistantPoints(controlPoints2?.reversed(), CURVE_POINTS_COUNT)
        val d2 = maxDistance(curve1, curve2Inverse, threshold)
        Timber.d("Points: $controlPoints1, $controlPoints2")
        Timber.d("Max distance is ${min(d1, d2)}")
        return (d1 < threshold) || (d2 < threshold)
    }

    @Suppress("SameParameterValue")
    private fun equidistantPoints(
        controlPoints: List<List<Float>>?,
        requiredCount: Int
    ): List<List<Float>> {
        val curvePoints = curvePoints(controlPoints, requiredCount * 50)
        var totalLength = 0f
        curvePoints.forEachIndexed { i, p ->
            if (i > 0) {
                totalLength += distance(p, curvePoints[i - 1])
            }
        }

        val segmentLength = totalLength / requiredCount

        val result = mutableListOf<List<Float>>()
        var currentLength = 0f
        curvePoints.forEachIndexed { i, p ->
            if (i > 0) {
                currentLength += distance(p, curvePoints[i - 1])
            }

            if (currentLength > segmentLength) {
                result.add(p)
                currentLength = 0f
            }
        }

        return result
    }

    private fun curvePoints(
        controlPoints: List<List<Float>>?,
        requiredCount: Int
    ): List<List<Float>> {
        val curvePoints = mutableListOf<List<Float>>()

        if (!controlPoints.isNullOrEmpty()) {
            for (j in 0 until controlPoints.size - 2 step 2) {
                for (i in 0..requiredCount) {
                    val t = i.toFloat() / requiredCount
                    val x =
                        (1 - t) * (1 - t) * controlPoints[j][0] + 2 * (1 - t) * t * controlPoints[j + 1][0] + t * t * controlPoints[j + 2][0]
                    val y =
                        (1 - t) * (1 - t) * controlPoints[j][1] + 2 * (1 - t) * t * controlPoints[j + 1][1] + t * t * controlPoints[j + 2][1]
                    curvePoints.add(listOf(x, y))
                }
            }
        }

        return curvePoints
    }

    private fun maxDistance(
        curve1: List<List<Float>>,
        curve2: List<List<Float>>,
        threshold: Int
    ): Float {
        return curve1.zip(curve2).map { points -> distance(points.first, points.second) }
            .maxOrNull() ?: (threshold + 1).toFloat()
    }

    private fun distance(p1: List<Float>, p2: List<Float>): Float =
        sqrt((p2[1] - p1[1]).pow(2) + (p2[0] - p1[0]).pow(2))

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
            filterIncorrectAnnotations(
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