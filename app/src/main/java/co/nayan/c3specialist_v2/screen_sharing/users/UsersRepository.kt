package co.nayan.c3specialist_v2.screen_sharing.users

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.requests.SendNotificationRequest
import co.nayan.c3v2.core.models.c3_module.responses.CallUserListResponse
import javax.inject.Inject

class UsersRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchUsers(page: Int, perPage: Int): CallUserListResponse {
        return apiClientFactory.apiClientBase.fetchUsersList(page, perPage)
    }

    suspend fun inviteUser(id: Int?, message: String, title: String?) {
        apiClientFactory.apiClientBase.sendInitNotification(
            SendNotificationRequest(id, message, title)
        )
    }
}