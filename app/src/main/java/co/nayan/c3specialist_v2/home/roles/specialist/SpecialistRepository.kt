package co.nayan.c3specialist_v2.home.roles.specialist

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.Work
import co.nayan.c3v2.core.models.c3_module.requests.SandboxTrainingRequest
import co.nayan.c3v2.core.models.c3_module.responses.SandboxTrainingResponse
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import javax.inject.Inject

class SpecialistRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchUserStats(): StatsResponse? {
        return apiClientFactory.apiClientBase.fetchSpecialistHomeStats()
    }

    suspend fun assignWork(): Work? {
        return apiClientFactory.apiClientBase.specialistWorkAssignment()
    }

    suspend fun assignManagerWork(): Work? {
        return apiClientFactory.apiClientBase.managerWorkAssignment()
    }

    suspend fun sandboxTraining(wfStepId: Int?): SandboxTrainingResponse? {
        return apiClientFactory.apiClientBase.sandboxTraining(SandboxTrainingRequest(wfStepId))
    }
}