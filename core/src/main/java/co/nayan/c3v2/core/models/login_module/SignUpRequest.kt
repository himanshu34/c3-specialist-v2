package co.nayan.c3v2.core.models.login_module

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class SignUpRequest(
    val name: String,
    val email: String,
    val password: String,
    @SerializedName("password_confirmation")
    val passwordConfirmation: String,
    val model: String?,
    val version: String?,
    @SerializedName("build_version")
    val buildVersion: String?,
    val ram: String?
)

@Keep
data class SocialSignUpRequest(
    val name: String?,
    val email: String?,
    @SerializedName("photo_url")
    val photoUrl: String?,
    val token: String?,
    val type: String,
    val model: String?,
    val version: String?,
    @SerializedName("build_version")
    val buildVersion: String?,
    val ram: String?
)