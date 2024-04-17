package co.nayan.c3specialist_v2.dashboard

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.RoleApiRequest
import co.nayan.c3v2.core.models.c3_module.responses.RoleRequestResponse
import javax.inject.Inject

class RoleRequestRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun createRoles(roles: List<String>): RoleRequestResponse {
        return apiClientFactory.apiClientBase.createRolesRequest(request = RoleApiRequest(request = roles))
    }

    suspend fun getRolesRequest(): RoleRequestResponse {
        return apiClientFactory.apiClientBase.getRolesRequest()
    }
}