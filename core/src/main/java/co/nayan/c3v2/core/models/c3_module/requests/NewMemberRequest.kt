package co.nayan.c3v2.core.models.c3_module.requests

data class NewMemberRequest(
    val email: String?,
    val phoneNumber: String?,
    val name: String?
)
