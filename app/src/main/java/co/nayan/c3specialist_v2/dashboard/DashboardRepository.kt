package co.nayan.c3specialist_v2.dashboard

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.LearningVideosResponse
import co.nayan.c3v2.core.models.SurgeLocationsResponse
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.c3_module.requests.UpdatePersonalInfoRequest
import co.nayan.c3v2.core.models.c3_module.responses.FetchAppMinVersionResponse
import co.nayan.c3v2.core.models.c3_module.responses.UserResponse
import javax.inject.Inject

class DashboardRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchMinVersionCodeRequired(): FetchAppMinVersionResponse? {
        return apiClientFactory.apiClientBase.fetchMinVersionCode()
    }

    suspend fun fetchSurgeLocations(): SurgeLocationsResponse? {
        return apiClientFactory.apiClientBase.fetchSurgeLocations()
    }

    suspend fun fetchUserDetails(): User {
        return apiClientFactory.apiClientBase.fetchUserDetails()
    }

    suspend fun fetchLearningVideos(): LearningVideosResponse? {
        return apiClientFactory.apiClientBase.getLearningVideos()
    }

    suspend fun updatePersonalInfo(request: UpdatePersonalInfoRequest): UserResponse {
        return apiClientFactory.apiClientBase.updatePersonalInfo(request)
    }
}