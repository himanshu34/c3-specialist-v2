package co.nayan.c3v2.core.models.c3_module.responses

data class OverallTeamPerformanceResponse(
    val stats: MemberStats?,
    val success: Boolean?
)

data class MembersPerformanceResponse(
    val stats: List<MemberStats>?,
    val success: Boolean?
)

data class MemberStats(
    val accuracy: Float?,
    val actualEarnings: Float?,
    val actualScore: Float?,
    val completedAnnotations: Int?,
    val completedJudgments: Int?,
    val completedReviews: Int?,
    val completedPotentialScore: Float?,
    val correctAnnotations: Int?,
    val correctJudgments: Int?,
    val correctReviews: Int?,
    val correctScore: Float?,
    val inconclusiveAnnotations: Int?,
    val inconclusiveJudgments: Int?,
    val inconclusiveReviews: Int?,
    val incorrectAnnotations: Int?,
    val incorrectJudgments: Int?,
    val incorrectReviews: Int?,
    val incorrectScore: Float?,
    val lastUpdatedAt: String?,
    val potentialScore: Float?,
    val totalAnnotations: Int?,
    val totalJudgments: Int?,
    val totalReviews: Int?,
    val userEmail: String?,
    val userName: String?,
    val userId: Int?,
    val workDuration: String?,
    val totalHoursWorked: String?
) {
    fun getAccuracy(): Float {
        val completedCount =
            (completedAnnotations ?: 0) + (completedJudgments ?: 0) + (completedReviews ?: 0)
        val correctCount =
            (correctAnnotations ?: 0) + (correctJudgments ?: 0) + (correctReviews ?: 0)
        return if (completedCount != 0) {
            return (correctCount * 100) * 1f / completedCount
        } else {
            0f
        }
    }
}