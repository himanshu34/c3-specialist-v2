package co.nayan.review.models

import androidx.annotation.Keep
import co.nayan.c3v2.core.models.WorkAssignment

@Keep
data class ReviewIntentInputData(
    val workAssignment: WorkAssignment?,
    val appFlavor: String?
)