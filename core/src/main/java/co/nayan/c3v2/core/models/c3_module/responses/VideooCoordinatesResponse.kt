package co.nayan.c3v2.core.models.c3_module.responses

import com.google.gson.annotations.SerializedName

data class VideooCoordinatesResponse(
    @SerializedName("uploaded_videos")
    val uploadedVideos: MutableList<VideoCoorinates>
)