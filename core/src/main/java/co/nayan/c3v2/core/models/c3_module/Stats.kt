package co.nayan.c3v2.core.models.c3_module

data class Stats(
    val workDuration: String?,

    val totalAnnotations: Int?,
    val completedAnnotations: Int?,
    val correctAnnotations: Int?,
    val incorrectAnnotations: Int?,
    val inconclusiveAnnotations: Int?,

    val totalJudgments: Int?,
    val completedJudgments: Int?,
    val correctJudgments: Int?,
    val incorrectJudgments: Int?,
    val inconclusiveJudgments: Int?,

    val totalReviews: Int?,
    val completedReviews: Int?,
    val correctReviews: Int?,
    val incorrectReviews: Int?,
    val inconclusiveReviews: Int?,
    val reviewDuration: String?,
    val approvedCount: Int?,
    val resetCount: Int?,

    val potentialScore: String?,
    val completedPotentialScore: String?,
    val correctScore: String?,
    val incorrectScore: String?,
    val lastUpdatedAt: String?
) {
    fun getAnnotationAccuracy(): String {
        return if (completedAnnotations != null && correctAnnotations != null) {
            if (completedAnnotations != 0) {
                val accuracy = (correctAnnotations * 100) / completedAnnotations
                return "$accuracy%"
            } else "0%"
        } else "0%"
    }

    fun getJudgmentAccuracy(): String {
        return if (completedJudgments != null && correctJudgments != null) {
            if (completedJudgments != 0) {
                val accuracy = (correctJudgments * 100) / completedJudgments
                return "$accuracy%"
            } else "0%"
        } else "0%"
    }

    fun getReviewAccuracy(): String {
        return if (completedReviews != null && correctReviews != null) {
            if (completedReviews != 0) {
                val accuracy = (correctReviews * 100) / completedReviews
                return "$accuracy%"
            } else "0%"
        } else "0%"
    }
}