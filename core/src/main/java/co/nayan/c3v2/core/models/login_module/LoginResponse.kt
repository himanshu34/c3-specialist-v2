package co.nayan.c3v2.core.models.login_module

import androidx.annotation.Keep
import co.nayan.c3v2.core.models.User

@Keep
data class LoginResponse(
    val data: User
)

@Keep
data class ForgotPasswordResponse(
    val success: Boolean,
    val message: String
)