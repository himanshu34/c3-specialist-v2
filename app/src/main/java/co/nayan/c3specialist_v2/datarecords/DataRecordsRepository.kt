package co.nayan.c3specialist_v2.datarecords

import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.WorkFlow
import co.nayan.c3v2.core.models.c3_module.responses.DataRecordsResponse
import javax.inject.Inject

class DataRecordsRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val sharedStorage: SharedStorage
) {

    suspend fun fetchRecords(
        wfStepId: Int?,
        page: Int,
        perPage: Int,
        aasmState: String,
        startTime: String?,
        endTime: String?
    ): DataRecordsResponse {
        val state = if(aasmState == "None") null else aasmState
        val stepId = if(wfStepId == -1) null else wfStepId
        return apiClientFactory.apiClientBase.fetchDataRecords(
            page,
            perPage,
            state,
            startTime,
            endTime,
            stepId
        )
    }

    suspend fun fetchDataRecord(recordId: Int): Record? {
        return apiClientFactory.apiClientBase.fetchDataRecord(recordId).data
    }

    fun getSpanCount(): Int {
        return sharedStorage.getSpanCount()
    }

    fun saveSpanCount(spanValue: Int) {
        sharedStorage.saveSpanCount(spanValue)
    }

    fun fetchAasmStatesFormLocalStorage(): List<String> {
        return sharedStorage.fetchAasmStats()
    }

    suspend fun fetchAasmStatesFromServer(): List<String> {
        val aasmStates = apiClientFactory.apiClientBase.fetchAasmStats().data?.states ?: emptyList()
        if (aasmStates.isNotEmpty()) {
            sharedStorage.saveAasmStates(aasmStates)
        }
        return aasmStates
    }

    fun fetchWorkFlowsFormLocalStorage(): List<WorkFlow> {
        return sharedStorage.fetchWorkFlows()
    }

    suspend fun fetchWorkFlowsFromServer(): List<WorkFlow> {
        val workFlows = apiClientFactory.apiClientBase.fetchWorkflows() ?: emptyList()
        if (workFlows.isNotEmpty()) {
            sharedStorage.saveWorkFlows(workFlows)
        }
        return workFlows
    }
}