package co.nayan.c3specialist_v2.performance

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.LeaderStats
import co.nayan.c3v2.core.models.c3_module.responses.MemberStats
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import javax.inject.Inject

class PerformanceRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchSpecialistPerformance(startTime: String, endTime: String): StatsResponse? {
        return apiClientFactory.apiClientBase.fetchSpecialistPerformance(startTime, endTime)
    }

    suspend fun fetchManagerPerformance(startTime: String, endTime: String): StatsResponse? {
        return apiClientFactory.apiClientBase.fetchManagerPerformance(startTime, endTime)
    }

    suspend fun fetchOverallTeamPerformance(
        startTime: String?,
        endTime: String?,
        userType: String
    ): MemberStats? {
        return apiClientFactory.apiClientBase.fetchOverallTeamPerformance(startTime, endTime, userType)?.stats
    }

    suspend fun fetchTeamMembersPerformance(
        startTime: String?,
        endTime: String?,
        userType: String
    ): List<MemberStats>? {
        return apiClientFactory.apiClientBase.fetchTeamMembersPerformance(startTime, endTime, userType)?.stats
    }

    suspend fun fetchLeaderPerformance(
        startTime: String?,
        endTime: String?
    ): LeaderStats? {
        return apiClientFactory.apiClientBase.fetchLeaderPerformance(startTime, endTime).leaderStats
    }
}