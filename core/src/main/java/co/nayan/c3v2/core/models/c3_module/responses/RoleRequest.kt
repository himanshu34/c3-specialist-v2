package co.nayan.c3v2.core.models.c3_module.responses

import com.google.gson.annotations.SerializedName

data class RoleRequest(
    @SerializedName("id") val id: Int?,
    @SerializedName("role") val role: String?,
    @SerializedName("user_id") val longitude: Int?,
    @SerializedName("approved") val approved: Boolean?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)