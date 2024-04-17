package co.nayan.c3specialist_v2.developerreview

import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.Work
import co.nayan.c3v2.core.models.c3_module.requests.AdminWorkAssignment
import co.nayan.c3v2.core.models.c3_module.requests.RecordUpdateStarredRequest
import co.nayan.c3v2.core.models.c3_module.requests.SubmitReviewRequest
import co.nayan.c3v2.core.models.c3_module.responses.RecordStarredStatusResponse
import javax.inject.Inject

class DeveloperReviewRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val sharedStorage: SharedStorage
) {
    suspend fun assignWork(): Work? {
        return apiClientFactory.apiClientBase.adminWorkAssignment(AdminWorkAssignment(null))
    }

    suspend fun submitReview(rejectedIds: List<Int>, approvedIds: List<Int>): Boolean {
        return apiClientFactory.apiClientBase.submitAdminReview(
            SubmitReviewRequest(rejectedIds, approvedIds, null, null)
        ).success ?: false
    }

    suspend fun fetchTrainingRecords(workAssignmentId: Int?): List<Record>? {
        return apiClientFactory.apiClientBase.adminNextRecords(workAssignmentId)?.nextRecords
    }

    fun getSpanCount(): Int {
        return sharedStorage.getSpanCount()
    }

    fun saveSpanCount(spanValue: Int) {
        sharedStorage.saveSpanCount(spanValue)
    }

    fun saveContrast(value: Int) {
        sharedStorage.saveContrast(value)
    }

    fun getContrast(): Int {
        return sharedStorage.getContrast()
    }

    suspend fun updateRecordStarredStatus(
        recordId: Int, status: Boolean
    ): RecordStarredStatusResponse {
        return apiClientFactory.apiClientBase.updateRecordStarredStatus(recordId, RecordUpdateStarredRequest(status))
    }
}