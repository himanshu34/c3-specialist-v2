package co.nayan.c3v2.core.models.c3_module

data class DataRecordsFilters(
    var aasmState: String = "None",
    var startTime: String,
    var endTime: String,
    var wfStep: Pair<Int?, String> = Pair(-1, "None")
)