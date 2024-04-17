package co.nayan.c3v2.core.models.c3_module.requests

data class SendNotificationRequest(
    val user_id: Int?,
    val message: String?,
    val title: String?
)