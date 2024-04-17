package co.nayan.c3v2.core.models.c3_module.responses

import co.nayan.c3v2.core.models.c3_module.UserListItem

data class CallUserListResponse(
    val users: List<UserListItem>?,
    val success: Boolean?,
    val totalPages: Int?,
    val currentPage: Int?
)