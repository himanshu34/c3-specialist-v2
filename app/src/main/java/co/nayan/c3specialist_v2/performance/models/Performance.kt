package co.nayan.c3specialist_v2.performance.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Performance(
    val workDuration: String? = null,
    val points: PointStats? = null,
    val annotationStats: UserStats? = null,
    val judgmentStats: UserStats? = null,
    val reviewStats: UserStats? = null
) : Parcelable


@Parcelize
data class PointStats(
    val potential: Float?,
    val completedPotential: Float?,
    val correctPotential: Float?,
    val incorrectPotential: Float?
) : Parcelable

@Parcelize
data class UserStats(
    val totalCount: Int?,
    val completedCount: Int?,
    val correctCount: Int?,
    val incorrectCount: Int?,
    val inconclusiveCount: Int?
) : Parcelable {
    fun accuracy(): String {
        return if (completedCount != null && correctCount != null) {
            if (completedCount != 0) {
                val accuracy = (correctCount * 100) / completedCount
                return "$accuracy%"
            } else {
                "0%"
            }
        } else {
            "0%"
        }
    }
}
