package co.nayan.c3specialist_v2.profile

import co.nayan.c3specialist_v2.profile.utils.KycManager
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.BankDetails
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.c3_module.AuthenticationResponse
import co.nayan.c3v2.core.models.c3_module.requests.UpdateBankDetailsRequest
import co.nayan.c3v2.core.models.c3_module.requests.UpdatePasswordRequest
import co.nayan.c3v2.core.models.c3_module.requests.UpdatePersonalInfoRequest
import co.nayan.c3v2.core.models.c3_module.responses.UpdatePasswordResponse
import co.nayan.c3v2.core.models.c3_module.responses.UserResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val kycManager: KycManager
) {

    suspend fun getKycStatus() = kycManager.getKycStatusDetails()

    suspend fun uploadImage(image: MultipartBody.Part): UserResponse {
        return apiClientFactory.apiClientBase.uploadImage(image)
    }

    suspend fun fetchUserDetails(): User {
        return apiClientFactory.apiClientBase.fetchUserDetails()
    }

    suspend fun authenticatePassword(password: String): AuthenticationResponse {
        return apiClientFactory.apiClientBase.authenticatePassword(password)
    }

    suspend fun updatePersonalInfo(request: UpdatePersonalInfoRequest): UserResponse {
        return apiClientFactory.apiClientBase.updatePersonalInfo(request)
    }

    suspend fun updatePanDetails(
        idTypePart: RequestBody,
        numberPart: RequestBody,
        image: MultipartBody.Part
    ): UserResponse {
        return apiClientFactory.apiClientBase.updatePanDetails(idTypePart, numberPart, image)
    }

    suspend fun updatePhotoIdDetails(
        idTypePart: RequestBody,
        numberPart: RequestBody,
        image: MultipartBody.Part
    ): UserResponse {
        return apiClientFactory.apiClientBase.updatePhotoIdDetails(idTypePart, numberPart, image)
    }

    suspend fun updateBankDetails(request: UpdateBankDetailsRequest): BankDetails {
        return apiClientFactory.apiClientBase.updateBankDetails(request)
    }

    suspend fun fetchBankDetails(token: String?): BankDetails {
        return apiClientFactory.apiClientBase.fetchBankDetails(token)
    }

    suspend fun updatePassword(updatePasswordRequest: UpdatePasswordRequest): UpdatePasswordResponse {
        return apiClientFactory.apiClientBase.updatePassword(updatePasswordRequest)
    }
}