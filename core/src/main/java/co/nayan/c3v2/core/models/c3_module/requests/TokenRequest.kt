package co.nayan.c3v2.core.models.c3_module.requests

import com.google.gson.annotations.SerializedName

data class TokenRequest(
    val token: String,
    val model: String?,
    val version: String?,
    @SerializedName("build_version")
    val buildVersion: String?,
    val ram: String?
)