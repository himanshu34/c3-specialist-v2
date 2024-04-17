package co.nayan.c3specialist_v2.home.roles.leader

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.LeaderHomeStatsResponse
import javax.inject.Inject

class LeaderRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchLeaderHomeStats(): LeaderHomeStatsResponse? {
        return apiClientFactory.apiClientBase.fetchLeaderHomeStats()
    }
}