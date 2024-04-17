package co.nayan.c3specialist_v2.workflowsteps

import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.WfStep
import co.nayan.c3v2.core.models.Work
import co.nayan.c3v2.core.models.c3_module.requests.AdminWorkAssignment
import javax.inject.Inject

class WorkFlowStepsRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val sharedStorage: SharedStorage
) {

    suspend fun fetchWorkFlowSteps(workFlowId: Int): List<WfStep>? {
        return apiClientFactory.apiClientBase.fetchWfSteps(workFlowId)
    }

    suspend fun assignWork(request: AdminWorkAssignment): Work? {
        return apiClientFactory.apiClientBase.adminWorkAssignment(request)
    }

    fun setCanvasRole(role: String) {
        sharedStorage.setRoleForCanvas(role)
    }

    fun currentRole(): String? {
        return sharedStorage.getRoleForCanvas()
    }
}