package co.nayan.c3v2.core.models.c3_module.responses

import co.nayan.c3v2.core.models.User

data class UserResponse(
    val message: String?,
    val user: User?
)