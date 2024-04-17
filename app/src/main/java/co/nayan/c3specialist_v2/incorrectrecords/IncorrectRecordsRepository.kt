package co.nayan.c3specialist_v2.incorrectrecords

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectRecordsResponse
import javax.inject.Inject

class IncorrectRecordsRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory
) {

    suspend fun fetchSpecialistIncorrectAnnotations(
        startDate: String?,
        endDate: String?,
        page: Int,
        perPage: Int,
        wfStepId: Int?
    ): IncorrectRecordsResponse? {
        return apiClientFactory.apiClientBase.fetchSpecialistIncorrectAnnotations(
            startDate,
            endDate,
            page,
            perPage,
            wfStepId
        )
    }

    suspend fun fetchSpecialistIncorrectJudgments(
        startDate: String?,
        endDate: String?,
        page: Int,
        perPage: Int,
        wfStepId: Int?
    ): IncorrectRecordsResponse? {
        return apiClientFactory.apiClientBase.fetchSpecialistIncorrectJudgments(
            startDate,
            endDate,
            page,
            perPage,
            wfStepId
        )
    }

    suspend fun fetchManagerIncorrectAnnotations(
        startDate: String?,
        endDate: String?,
        page: Int,
        perPage: Int,
        wfStepId: Int?
    ): IncorrectRecordsResponse? {
        return apiClientFactory.apiClientBase.fetchManagerIncorrectAnnotations(
            startDate,
            endDate,
            page,
            perPage,
            wfStepId
        )
    }

    suspend fun fetchManagerIncorrectJudgments(
        startDate: String?,
        endDate: String?,
        page: Int,
        perPage: Int,
        wfStepId: Int?
    ): IncorrectRecordsResponse? {
        return apiClientFactory.apiClientBase.fetchManagerIncorrectJudgments(startDate, endDate, page, perPage, wfStepId)
    }

    suspend fun fetchManagerIncorrectReviews(
        startDate: String?,
        endDate: String?,
        page: Int,
        perPage: Int,
        wfStepId: Int?
    ): IncorrectRecordsResponse? {
        return apiClientFactory.apiClientBase.fetchManagerIncorrectReviews(startDate, endDate, page, perPage, wfStepId)
    }

    suspend fun fetchMemberIncorrectAnnotations(
        startDate: String?,
        endDate: String?,
        page: Int,
        perPage: Int,
        userRole: String?,
        userId: Int?,
        wfStepId: Int?
    ): IncorrectRecordsResponse? {
        return apiClientFactory.apiClientBase.fetchMemberIncorrectAnnotations(
            startDate,
            endDate,
            page,
            perPage,
            userRole,
            userId,
            wfStepId
        )
    }

    suspend fun fetchMemberIncorrectJudgments(
        startDate: String?,
        endDate: String?,
        page: Int,
        perPage: Int,
        userRole: String?,
        userId: Int?,
        wfStepId: Int?
    ): IncorrectRecordsResponse? {
        return apiClientFactory.apiClientBase.fetchMemberIncorrectJudgments(
            startDate,
            endDate,
            page,
            perPage,
            userRole,
            userId,
            wfStepId
        )
    }

    suspend fun fetchMemberIncorrectReviews(
        startDate: String?,
        endDate: String?,
        page: Int,
        perPage: Int,
        userRole: String?,
        userId: Int?,
        wfStepId: Int?
    ): IncorrectRecordsResponse? {
        return apiClientFactory.apiClientBase.fetchMemberIncorrectReviews(
            startDate,
            endDate,
            page,
            perPage,
            userRole,
            userId,
            wfStepId
        )
    }
}