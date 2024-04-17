package co.nayan.c3v2.core.models.c3_module.responses

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class AllowedLocationResponse(
    @SerializedName("allowed_locations")
    val allowedLocations: MutableList<AllowedLocation>?
)