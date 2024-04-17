package co.nayan.c3v2.core.models.c3_module.responses

import android.os.Parcelable
import co.nayan.c3v2.core.models.CurrentAnnotation
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.WfStep
import kotlinx.parcelize.Parcelize

data class IncorrectWfStep(
    val incorrectCount: Int?,
    val wfStep: WfStep?
)

data class IncorrectRecordsResponse(
    val incorrectAnnotations: List<IncorrectAnnotation>?,
    val incorrectJudgments: List<IncorrectJudgment>?,
    val incorrectReviews: List<IncorrectReview>?,
    val page: Int?,
    val totalCount: Int?,
    val perPage: Int?
)

@Parcelize
data class IncorrectJudgment(
    var dataRecord: Record?,
    val annotation: CurrentAnnotation?,
    val wfStep: WfStep?,
    val judgment: Boolean?
) : Parcelable

@Parcelize
data class IncorrectAnnotation(
    var dataRecord: Record?,
    val incorrectAnnotation: CurrentAnnotation?,
    val wfStep: WfStep?
) : Parcelable

@Parcelize
data class IncorrectReview(
    var dataRecord: Record?,
    val annotation: CurrentAnnotation?,
    val judgment: Boolean?,
    val wfStep: WfStep?
) : Parcelable {

    fun getCorrectReview() =
        if (judgment == true) {
            "Reject"
        } else {
            "Approve"
        }

    fun getUserReview() =
        if (judgment == true) {
            "Approve"
        } else {
            "Reject"
        }
}
