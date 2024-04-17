package co.nayan.c3specialist_v2.faq

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.FaqDataConfirmationRequest
import javax.inject.Inject

class FaqRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchTrainingData(wfStepId: Int?) =
        apiClientFactory.apiClientBase.fetchFaqData(wfStepId)

    suspend fun submitConfirmation(wfStepId: Int?) =
        apiClientFactory.apiClientBase.submitFaqConfirmation(FaqDataConfirmationRequest(wfStepId))
}