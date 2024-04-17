package co.nayan.c3v2.core.models.c3_module.responses

import com.google.gson.annotations.SerializedName

data class VideoCoorinates(
    @SerializedName("id")
    val id: Int?,
    @SerializedName("latitude")
    val latitude: Double?,
    @SerializedName("longitude")
    val longitude: Double?
)