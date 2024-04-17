package co.nayan.c3v2.core.models.c3_module.responses

data class UpdatePasswordResponse(
    val message: String?, val isCorrectPassword: Boolean?, val idToken: String?
)
