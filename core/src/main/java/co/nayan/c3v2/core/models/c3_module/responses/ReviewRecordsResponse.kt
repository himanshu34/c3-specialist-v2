package co.nayan.c3v2.core.models.c3_module.responses

import co.nayan.c3v2.core.models.Record

data class ReviewRecordsResponse(
    val dataRecords: List<Record>?
)