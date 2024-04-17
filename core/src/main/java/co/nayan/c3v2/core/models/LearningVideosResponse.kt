package co.nayan.c3v2.core.models

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

data class LearningVideosResponse(
    val success: Boolean,
    val data: LearningVideosData?
)

data class LearningVideosData(
    val violationVideos: MutableList<Video>,
    val learningVideos: LearningVideosResult,
    val introductionVideos: LearningVideosResult
)

@Keep
@Parcelize
data class Video(
    val id: Int,
    val roleId: Int,
    val youtubeUrl: String?,
    val gcpUrl: String?,
    val name: String?,
    val code: String?,
    val applicationModeName: String?
) : Parcelable

data class LearningVideosResult(
    val driver: MutableList<Video>?,
    val specialist: MutableList<Video>?,
    val manager: MutableList<Video>?,
    val leader: MutableList<Video>?,
    val admin: MutableList<Video>?
)