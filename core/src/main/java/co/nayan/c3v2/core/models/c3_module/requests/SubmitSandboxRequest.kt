package co.nayan.c3v2.core.models.c3_module.requests

import android.os.Parcelable
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import kotlinx.parcelize.Parcelize

data class SandboxTrainingRequest(
    val wfStepId: Int?
)

data class SandboxSubmitAnnotationRequest(
    val sandboxRecordId: Int?,
    val annotation: List<AnnotationObjectsAttribute>?,
    val status: String?
)

@Parcelize
data class SandboxRefreshDataRequest(
    val recordId: Int?,
    val annotation: List<AnnotationObjectsAttribute>? = null
) : Parcelable