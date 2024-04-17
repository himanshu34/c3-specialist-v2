package co.nayan.c3v2.core.models.c3_module

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class WFQnAnsData(
    val success: Boolean?,
    val faqs: List<FaqData>?
)

@Parcelize
data class FaqData(
    val id: Int?,
    val wfStepId: Int?,
    val image: String?,
    val category: String,
    val position: Int?,
) : Parcelable

data class FaqDataConfirmationResponse(
    val message: String?,
    val errors: String?
)

data class DisplayDataItem(
    val faqData: FaqData? = null,
    val isPresent: Boolean,
    val category: String
)

data class FaqDataConfirmationRequest(
    val wfStepId: Int?,
)