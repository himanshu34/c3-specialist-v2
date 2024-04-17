package co.nayan.c3v2.core.models.c3_module.responses

import co.nayan.c3v2.core.models.User

data class PhoneVerificationResponse(
    val user: User?
)

data class ReferralCodeRequest(
    val referal_code: String
)

data class ReferralCodeResponse(
    val user: User?,
    val message: String?
)