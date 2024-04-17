package co.nayan.c3specialist_v2.workflows

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.WorkFlow
import javax.inject.Inject

class WorkFlowsRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchWorkFlows(): List<WorkFlow>? {
        return apiClientFactory.apiClientBase.fetchWorkflows()
    }
}