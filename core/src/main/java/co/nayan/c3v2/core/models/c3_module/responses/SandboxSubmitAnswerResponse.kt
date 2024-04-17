package co.nayan.c3v2.core.models.c3_module.responses

import co.nayan.c3v2.core.models.Record

data class SandboxSubmitAnswerResponse(
    val success: Boolean?,
    val nextRecords: List<Record>?
)