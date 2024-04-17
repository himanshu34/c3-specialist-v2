package co.nayan.c3specialist_v2.splash

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.AllowedLocation
import javax.inject.Inject

class SplashRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchAllowedLocation(): MutableList<AllowedLocation>? {
        val allowedLocationRes = apiClientFactory.apiClientBase.fetchAllowedLocations()
        return if (allowedLocationRes == null || allowedLocationRes.allowedLocations.isNullOrEmpty())
            arrayListOf() else allowedLocationRes.allowedLocations
    }
}