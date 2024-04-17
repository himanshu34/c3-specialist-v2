package co.nayan.c3v2.login

import co.nayan.c3v2.core.api.SafeApiRequest
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.models.login_module.CarCamRequest
import co.nayan.c3v2.core.models.login_module.ForgotPasswordRequest
import co.nayan.c3v2.core.models.login_module.ForgotPasswordResponse
import co.nayan.c3v2.core.models.login_module.LoginRequest
import co.nayan.c3v2.core.models.login_module.LoginResponse
import co.nayan.c3v2.core.models.login_module.SignUpRequest
import co.nayan.c3v2.core.models.login_module.SignUpResponse
import co.nayan.c3v2.core.models.login_module.SocialSignUpRequest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoginRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val deviceInfoHelperImpl: DeviceInfoHelperImpl
) : SafeApiRequest() {

    fun saveDeviceConfig(buildVersion: String, ram: String) {
        deviceInfoHelperImpl.saveDeviceConfig(buildVersion, ram)
    }

    fun getDeviceConfig() = deviceInfoHelperImpl.getDeviceConfig()

    suspend fun login(
        username: String,
        password: String
    ): Flow<LoginResponse> = makeSafeRequestForFlow {
        val deviceConfig = getDeviceConfig()
        apiClientFactory.apiClientLogin.login(
            LoginRequest(
                username,
                password,
                deviceConfig?.model,
                deviceConfig?.version,
                deviceConfig?.buildVersion,
                deviceConfig?.ram
            )
        )
    }

    suspend fun signUp(
        name: String,
        email: String,
        password: String
    ): Flow<SignUpResponse> = makeSafeRequestForFlow {
        val deviceConfig = getDeviceConfig()
        apiClientFactory.apiClientLogin.signUp(
            SignUpRequest(
                name,
                email,
                password,
                password,
                deviceConfig?.model,
                deviceConfig?.version,
                deviceConfig?.buildVersion,
                deviceConfig?.ram
            )
        )
    }

    suspend fun forgotPassword(email: String): Flow<ForgotPasswordResponse> =
        makeSafeRequestForFlow {
            apiClientFactory.apiClientLogin.forgotPassword(ForgotPasswordRequest(email))
        }

    suspend fun socialLogin(
        name: String?,
        email: String?,
        photoUrl: String?,
        token: String?,
        type: String
    ): Flow<SignUpResponse> = makeSafeRequestForFlow {
        val deviceConfig = getDeviceConfig()
        apiClientFactory.apiClientLogin.socialSignUp(
            SocialSignUpRequest(
                name,
                email,
                photoUrl,
                token,
                type,
                deviceConfig?.model,
                deviceConfig?.version,
                deviceConfig?.buildVersion,
                deviceConfig?.ram
            )
        )
    }

    suspend fun carCamLogin(identifier: String, email: String): Flow<LoginResponse> = makeSafeRequestForFlow {
        val deviceConfig = getDeviceConfig()
        apiClientFactory.apiClientLogin.camCamLogin(
            CarCamRequest(
                "Kent User",
                email,
                "kent",
                identifier,
                deviceConfig?.model,
                deviceConfig?.version,
                deviceConfig?.buildVersion,
                deviceConfig?.ram
            )
        )
    }
}