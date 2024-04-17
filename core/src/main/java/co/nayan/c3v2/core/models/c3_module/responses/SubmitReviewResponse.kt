package co.nayan.c3v2.core.models.c3_module.responses

import androidx.annotation.Keep

@Keep
data class SubmitReviewResponse(
    val success: Boolean?,
    val userAccountLocked: Boolean?
)