package co.nayan.c3v2.core.models.c3_module.responses

import android.os.Parcelable
import co.nayan.c3v2.core.models.Question
import co.nayan.c3v2.core.models.Record
import kotlinx.parcelize.Parcelize

data class SandboxRecordResponse(
    val trainingStatus: String?,
    val records: List<Record>?,
    val streak: Int?,
    val requiredCount: Int?
)

@Parcelize
data class SandboxTrainingResponse(
    val success: Boolean?,
    val sandboxTrainingId: Int?,
    val wfStepName: String?,
    val question: Question?,
    val applicationMode: String?,
    var wfStepId: Int?,
    var mediaType: String?,
    var annotationVariationThreshold: Int?
) : Parcelable

data class SandboxRecordsResponse(
    val sandboxRecords: List<Record>?
)