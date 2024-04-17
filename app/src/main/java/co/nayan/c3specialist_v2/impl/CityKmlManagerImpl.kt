package co.nayan.c3specialist_v2.impl

import androidx.lifecycle.MutableLiveData
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.CityKmlRequest
import co.nayan.c3v2.core.models.CityKmlResponse
import co.nayan.c3v2.core.models.CityWards
import co.nayan.c3v2.core.models.MapData
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.getDistanceInMeter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CityKmlManagerImpl @Inject constructor(
    private val sharedStorage: SharedStorage,
    private val apiClientFactory: ApiClientFactory
) : ICityKmlManager {

    private val kmlManagerJob = SupervisorJob()
    private val kmlManagerScope = CoroutineScope(Dispatchers.IO + kmlManagerJob)
    private val _kmlBoundariesResponse: MutableLiveData<MutableList<MapData>> = MutableLiveData()

    override suspend fun getCityKmlBoundaries() = sharedStorage.getCityKmlBoundaries()

    override suspend fun fetchCityWards(
        shouldFreshDownload: Boolean,
        location: LatLng,
        cityWards: MutableList<CityWards>
    ) = kmlManagerScope.launch {
        try {
            val allCityKmlBoundariesList = getCityKmlBoundaries()
            val kmlOfInterestRadius = sharedStorage.getKmlOfInterestRadius() * 1000 // In Meters
            val actualCoverageWards = cityWards.filter {
                getDistanceInMeter(location, it.centerLat, it.centerLon) <= kmlOfInterestRadius
            }

            // Split Data for regions which are already present and rest which are
            // not present in our shared preference
            val coverageArea = mutableListOf<MapData>()
            val notFoundArea = mutableListOf<CityWards>()
            Timber.tag("CityKmlManagerImpl").d("shouldFreshDownload -> $shouldFreshDownload")
            if (shouldFreshDownload) notFoundArea.addAll(actualCoverageWards)
            else {
                actualCoverageWards.map { cityWard ->
                    allCityKmlBoundariesList.find { mapData ->
                        mapData.name.equals(cityWard.cityKml, ignoreCase = true)
                    }?.let { coverageArea.add(it) } ?: run { notFoundArea.add(cityWard) }
                }
            }

            val notFoundAreaList = fetchNoAreaCityWardSpecs(notFoundArea)
            notFoundAreaList?.map { result ->
                result?.cityKml?.let { kmlBoundaries ->
                    coverageArea.addAll(kmlBoundaries)

                    // To add only unique elements
                    kmlBoundaries.map {
                        if (allCityKmlBoundariesList.contains(it).not())
                            allCityKmlBoundariesList.add(it)
                    }
                }
            }

            sharedStorage.saveCityKmlBoundaries(allCityKmlBoundariesList)

            // Send Live Data for only those which are covering under your reach
            _kmlBoundariesResponse.postValue(coverageArea)
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
        }
    }

    private suspend fun fetchNoAreaCityWardSpecs(
        notFoundArea: MutableList<CityWards>
    ): List<CityKmlResponse?>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val deferredList = notFoundArea.map { async { fetchCityWardSpecs(it.cityKml) } }
            Timber.tag("CityKmlManagerImpl").d("NoAreaFound size -> ${notFoundArea.size}")
            // Fetch details of async request in parallel and wait for all the requests to complete
            deferredList.awaitAll()
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
            null
        }
    }

    override suspend fun fetchCityWardSpecs(cityName: String?): CityKmlResponse? {
        return apiClientFactory.apiClientBase.fetchCityWardSpecs(CityKmlRequest(cityName))
    }

    override fun subscribe(): MutableLiveData<MutableList<MapData>> = _kmlBoundariesResponse
}