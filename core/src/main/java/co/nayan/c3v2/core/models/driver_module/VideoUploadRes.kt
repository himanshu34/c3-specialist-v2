package co.nayan.c3v2.core.models.driver_module

import androidx.annotation.Keep

@Keep
data class VideoUploadRes(
    val success: Boolean,
    val videoId: Int,
    val result: String?
)

@Keep
data class VideoFilesStatusRequest(
    val fileNames: List<String>
)

@Keep
data class VideoFilesStatusResponse(
    val syncedData: MutableList<String>?,
    val goingToDelete: MutableList<String>?
)