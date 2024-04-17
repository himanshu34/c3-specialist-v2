package co.nayan.c3specialist_v2.incorrectrecordsdetail

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.Record
import javax.inject.Inject

class IncorrectRecordDetailsRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory
) {

    suspend fun fetchRecord(
        recordId: Int?,
    ): Record? {
        return apiClientFactory.apiClientBase.fetchDataRecord(recordId).data
    }
}