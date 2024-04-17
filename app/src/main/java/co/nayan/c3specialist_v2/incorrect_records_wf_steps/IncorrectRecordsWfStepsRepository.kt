package co.nayan.c3specialist_v2.incorrect_records_wf_steps

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectWfStep
import javax.inject.Inject

class IncorrectRecordsWfStepsRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory
) {

    suspend fun fetchSpecialistIncorrectAnnotationsWfSteps(
        startDate: String?,
        endDate: String?
    ): List<IncorrectWfStep>? {
        return apiClientFactory.apiClientBase.fetchSpecialistIncorrectAnnotationsWfSteps(startDate, endDate)
    }

    suspend fun fetchSpecialistIncorrectJudgmentsWfSteps(
        startDate: String?,
        endDate: String?
    ): List<IncorrectWfStep>? {
        return apiClientFactory.apiClientBase.fetchSpecialistIncorrectJudgmentsWfSteps(startDate, endDate)
    }

    suspend fun fetchManagerIncorrectAnnotationsWfSteps(
        startDate: String?,
        endDate: String?
    ): List<IncorrectWfStep>? {
        return apiClientFactory.apiClientBase.fetchManagerIncorrectAnnotationsWfSteps(startDate, endDate)
    }


    suspend fun fetchManagerIncorrectJudgmentsWfSteps(
        startDate: String?,
        endDate: String?
    ): List<IncorrectWfStep>? {
        return apiClientFactory.apiClientBase.fetchManagerIncorrectJudgmentsWfSteps(startDate, endDate)
    }

    suspend fun fetchManagerIncorrectReviewsWfSteps(
        startDate: String?,
        endDate: String?
    ): List<IncorrectWfStep>? {
        return apiClientFactory.apiClientBase.fetchManagerIncorrectReviewsWfSteps(startDate, endDate)
    }


    suspend fun fetchMemberIncorrectAnnotationsWfSteps(
        startDate: String?,
        endDate: String?,
        userRole: String?,
        userId: Int?
    ): List<IncorrectWfStep>? {
        return apiClientFactory.apiClientBase.fetchMemberIncorrectAnnotationsWfSteps(
            startDate,
            endDate,
            userRole,
            userId
        )
    }

    suspend fun fetchMemberIncorrectJudgmentsWfSteps(
        startDate: String?,
        endDate: String?,
        userRole: String?,
        userId: Int?
    ): List<IncorrectWfStep>? {
        return apiClientFactory.apiClientBase.fetchMemberIncorrectJudgmentsWfSteps(startDate, endDate, userRole, userId)
    }

    suspend fun fetchMemberIncorrectReviewsWfSteps(
        startDate: String?,
        endDate: String?,
        userRole: String?,
        userId: Int?
    ): List<IncorrectWfStep>? {
        return apiClientFactory.apiClientBase.fetchMemberIncorrectReviewsWfSteps(startDate, endDate, userRole, userId)
    }
}