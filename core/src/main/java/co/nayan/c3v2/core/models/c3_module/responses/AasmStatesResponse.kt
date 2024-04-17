package co.nayan.c3v2.core.models.c3_module.responses

data class AasmStatesResponse(
    val data: StatesData?,
)

data class StatesData(
    val states: List<String>?
)
