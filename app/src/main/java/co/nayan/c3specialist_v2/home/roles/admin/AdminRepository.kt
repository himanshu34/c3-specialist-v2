package co.nayan.c3specialist_v2.home.roles.admin

import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.ActiveWfStepResponse
import co.nayan.c3v2.core.models.Work
import co.nayan.c3v2.core.models.c3_module.requests.AdminWorkAssignment
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import javax.inject.Inject

class AdminRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val sharedStorage: SharedStorage
) {
    suspend fun requestWorkStepToWork(): ActiveWfStepResponse {
        return apiClientFactory.apiClientBase.adminRequestWorkStep()
    }

    suspend fun assignWork(wfStepId: Int): Work? {
        return apiClientFactory.apiClientBase.adminWorkAssignment(AdminWorkAssignment(wfStepId))
    }

    suspend fun fetchUserStats(): StatsResponse? {
        return apiClientFactory.apiClientBase.fetchDeveloperHomeStats()
    }

    fun setCanvasRole(role: String) {
        sharedStorage.setRoleForCanvas(role)
    }
}