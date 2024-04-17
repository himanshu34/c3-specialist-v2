package co.nayan.c3v2.core.models.login_module

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class LoginRequest(
    val email: String,
    val password: String,
    val model: String?,
    val version: String?,
    @SerializedName("build_version")
    val buildVersion: String?,
    val ram: String?
)

@Keep
data class ForgotPasswordRequest(
    val email: String
)

@Keep
data class CarCamRequest(
    val name: String,
    val email: String,
    val type: String,
    val phoneNumber : String,
    val model: String?,
    val version: String?,
    @SerializedName("build_version")
    val buildVersion: String?,
    val ram: String?
)