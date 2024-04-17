package co.nayan.c3v2.core.models.c3_module.responses

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class AllowedLocation(
    @SerializedName("id")
    val id: Int?,
    @SerializedName("latitude")
    val latitude: String?,
    @SerializedName("longitude")
    val longitude: String?,
    @SerializedName("radius")
    val radius: Float?,
    @SerializedName("active")
    val active: Boolean?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
)