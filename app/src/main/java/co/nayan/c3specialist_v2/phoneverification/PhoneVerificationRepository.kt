package co.nayan.c3specialist_v2.phoneverification

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.c3_module.requests.PhoneVerificationRequest
import co.nayan.c3v2.core.models.c3_module.responses.PhoneVerificationResponse
import co.nayan.c3v2.core.models.c3_module.responses.ReferralCodeRequest
import co.nayan.c3v2.core.models.c3_module.responses.ReferralCodeResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class PhoneVerificationRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory
) {

    suspend fun updatePhoneNumber(phoneNumber: String?): PhoneVerificationResponse? {
        return apiClientFactory.apiClientBase.updatePhoneNumber(PhoneVerificationRequest(phoneNumber))
    }

    suspend fun updateReferralCode(referralCode: String): ReferralCodeResponse? {
        return try {
            apiClientFactory.apiClientBase.updateReferralCode(ReferralCodeRequest(referralCode))
        } catch (e: HttpException) {
            val errorResponse = e.response()?.errorBody()?.string()
            Gson().fromJson(errorResponse, ReferralCodeResponse::class.java)
        } catch (e: IOException) {
            ReferralCodeResponse(null, message = e.message)
        }
    }

    suspend fun validateOTP(otp: String?, idToken: String?): User? {
        return apiClientFactory.apiClientBase.validateOTP(idToken, otp)
    }
}