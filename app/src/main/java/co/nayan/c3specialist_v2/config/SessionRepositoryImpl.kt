package co.nayan.c3specialist_v2.config

import co.nayan.appsession.SessionRepositoryInterface
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.requests.SubmitSessionsRequest
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val userRepository: UserRepository
) : SessionRepositoryInterface {

    override suspend fun submitSessions(toSync: List<List<Any>>): Boolean {
        return try {
            val submitSessionsRequest = SubmitSessionsRequest(toSync)
            apiClientFactory.apiClientBase.submitSessions(submitSessionsRequest)
            true
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            false
        }
    }

    override fun userPhoneNumber(): String? {
        return userRepository.getUserInfo()?.phoneNumber
    }
}