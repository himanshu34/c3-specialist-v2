package co.nayan.c3specialist_v2.team.request_member

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.requests.NewMemberRequest
import co.nayan.c3v2.core.models.c3_module.responses.RequestMemberResponse
import javax.inject.Inject

class RequestMemberRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun requestNewMember(request: NewMemberRequest): RequestMemberResponse? {
        return apiClientFactory.apiClientBase.requestNewMember(request)
    }
}