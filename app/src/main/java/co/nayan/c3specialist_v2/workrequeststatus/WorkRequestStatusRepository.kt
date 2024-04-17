package co.nayan.c3specialist_v2.workrequeststatus

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.Work
import javax.inject.Inject

class WorkRequestStatusRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun getStatus(workRequestId: Int?): Work? {
        return apiClientFactory.apiClientBase.getStatus(workRequestId)
    }
}