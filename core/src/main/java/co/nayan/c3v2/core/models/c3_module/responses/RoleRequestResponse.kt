package co.nayan.c3v2.core.models.c3_module.responses

import com.google.gson.annotations.SerializedName

data class RoleRequestResponse(
    @SerializedName("success") val success: Boolean?,
    @SerializedName("request") val request: List<RoleRequest>?,
)