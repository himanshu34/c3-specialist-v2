package co.nayan.c3specialist_v2.home.roles.manager

import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.Work
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import javax.inject.Inject

class ManagerRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val sharedStorage: SharedStorage
) {

    suspend fun assignWork(): Work? {
        return apiClientFactory.apiClientBase.managerWorkAssignment()
    }

    suspend fun fetchUserStats(): StatsResponse? {
        return apiClientFactory.apiClientBase.fetchManagerHomeStats()
    }

    fun getContrast(): Int {
        return sharedStorage.getContrast()
    }
}