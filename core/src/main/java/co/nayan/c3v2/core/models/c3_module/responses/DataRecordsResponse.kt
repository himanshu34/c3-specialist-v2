package co.nayan.c3v2.core.models.c3_module.responses

import co.nayan.c3v2.core.models.Record

data class DataRecordsResponse(
    val data: List<Record>?,
    val totalCount: Int?
)

data class DataRecordResponse(
    val data: Record?
)