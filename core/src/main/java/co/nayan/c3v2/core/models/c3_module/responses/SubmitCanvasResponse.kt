package co.nayan.c3v2.core.models.c3_module.responses

data class SubmitCanvasResponse(
    val success: Boolean?,
    val successIds: List<Int>?,
    val failureIds: List<Int>?,
    val sniffingPassed: Boolean?,
)