package co.nayan.c3specialist_v2.home.roles.driver

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.LearningVideosResponse
import co.nayan.c3v2.core.models.c3_module.responses.DriverStatsResponse
import co.nayan.c3v2.core.models.c3_module.responses.EventsResponse
import co.nayan.c3v2.core.models.driver_module.CameraAIWorkFlowResponse
import javax.inject.Inject

class DriverRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory
) {
    suspend fun fetchUserStats(startDate: String, endDate: String): DriverStatsResponse? {
        return apiClientFactory.apiClientBase.fetchDriverStats(startDate, endDate)
    }

    suspend fun fetchAIWorkFlow(latitude: String?, longitude: String?): CameraAIWorkFlowResponse? {
        return apiClientFactory.apiClientBase.getAiWorkFlow(latitude, longitude)
    }

    suspend fun fetchLearningVideos(): LearningVideosResponse? {
        return apiClientFactory.apiClientBase.getLearningVideos()
    }

    suspend fun getEvents(): EventsResponse? {
        return apiClientFactory.apiClientBase.getEvents()
    }
}