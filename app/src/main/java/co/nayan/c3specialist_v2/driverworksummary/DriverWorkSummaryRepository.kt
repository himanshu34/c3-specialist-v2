package co.nayan.c3specialist_v2.driverworksummary

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.SurgeLocationsResponse
import co.nayan.c3v2.core.models.c3_module.responses.DriverStatsResponse
import co.nayan.c3v2.core.models.c3_module.responses.EventsResponse
import co.nayan.c3v2.core.models.c3_module.responses.VideooCoordinatesResponse
import javax.inject.Inject

class DriverWorkSummaryRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory
) {
    suspend fun fetchUserStats(startDate: String, endDate: String): DriverStatsResponse? {
        return apiClientFactory.apiClientBase.fetchDriverStats(startDate, endDate)
    }

    suspend fun fetchVideoCoordinates(
        startDate: String?,
        endDate: String?
    ): VideooCoordinatesResponse? {
        return apiClientFactory.apiClientBase.fetchVideoCoordinates(startDate, endDate)
    }

    suspend fun fetchSurgeLocations(): SurgeLocationsResponse? {
        return apiClientFactory.apiClientBase.fetchSurgeLocations()
    }

    suspend fun getEvents(): EventsResponse? {
        return apiClientFactory.apiClientBase.getEvents()
    }
}