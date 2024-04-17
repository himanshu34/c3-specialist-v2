package co.nayan.c3specialist_v2.screen_sharing.models

data class MeetingAction(
    val action: String,
    val value: Any?
)

data class MeetingStatus(
    val localStatus: String?,
    val remoteStatus: String?
)