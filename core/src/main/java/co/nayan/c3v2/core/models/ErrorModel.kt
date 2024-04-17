package co.nayan.c3v2.core.models

data class ErrorModel(
    val success: Boolean? = false,
    val message: String? = null,
    val errors: String? = null
)