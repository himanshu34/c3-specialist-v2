package co.nayan.c3v2.core.models.c3_module.requests

data class SpecialistIncorrectRecordsRequest(
    val startDate: String?,
    val endDate: String?,
    val page: Int,
    val perPage: Int
)