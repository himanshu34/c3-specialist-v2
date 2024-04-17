package co.nayan.c3v2.core.models.c3_module.requests

import com.google.gson.annotations.SerializedName

data class UpdatePersonalInfoRequest(
    val name: String,
    val address: String,
    val city: String,
    val state: String,
    val country: String,
    val model: String?,
    val version: String?,
    @SerializedName("build_version")
    val buildVersion: String?,
    val ram: String?
)

data class UpdateBankDetailsRequest(
    val beneficiaryName: String?,
    val accountNumber: String?,
    val bankName: String?,
    val bankIfsc: String?,
    val idToken: String?
)

data class UpdatePasswordRequest(
    val password: String?,
    val newPassword: String?
)