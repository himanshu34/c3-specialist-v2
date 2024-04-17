package co.nayan.c3v2.core.models.c3_module.responses

data class LeaderHomeStatsResponse(
    val stats: TeamStats?,
    val success: Boolean?,
    val leaderStats: LeaderStats?
)

data class LeaderPerformanceResponse(
    val leaderStats: LeaderStats?,
    val success: Boolean?
)

data class LeaderStats(
    val annotationsPotentialScore: Float?,
    val judgmentsPotentialScore: Float?,
    val reviewsPotentialScore: Float?,
    val overallPotentialScore: Float?,
    val overallCompletedPotentialScore: Float?,
    val overallCorrectScore: Float?,
    val overallIncorrectScore: Float?,
    val lastUpdatedAt: String?
)

data class TeamStats(
    val specialist: MemberStats?,
    val manager: MemberStats?,
    val lastUpdatedAt: String?
) {
    fun getTotalWorkHours(): String {
        val specialistWorkingTime = (specialist?.totalHoursWorked ?: "00:00").split(":")
        val managerWorkingTime = (manager?.totalHoursWorked ?: "00:00").split(":")

        var totalMinutes = specialistWorkingTime.last().toInt() + managerWorkingTime.last().toInt()
        var totalHours =
            specialistWorkingTime.first().toInt() + specialistWorkingTime.first().toInt()

        if (totalMinutes > 60) {
            totalHours += 1
            totalMinutes -= 60
        }

        return if (totalMinutes < 10) {
            "$totalHours:0$totalMinutes"
        } else {
            "$totalHours:$totalMinutes"
        }
    }

    fun getTotalPotentialScore(): String {
        return ((specialist?.potentialScore ?: 0f) + (manager?.potentialScore ?: 0f)).toString()
    }
}