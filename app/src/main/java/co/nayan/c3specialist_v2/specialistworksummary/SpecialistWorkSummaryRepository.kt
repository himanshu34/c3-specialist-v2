package co.nayan.c3specialist_v2.specialistworksummary

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import javax.inject.Inject

class SpecialistWorkSummaryRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchWorkSummary(startTime: String, endTime: String): StatsResponse? {
        return apiClientFactory.apiClientBase.fetchSpecialistWorkSummary(startTime, endTime)
    }
}