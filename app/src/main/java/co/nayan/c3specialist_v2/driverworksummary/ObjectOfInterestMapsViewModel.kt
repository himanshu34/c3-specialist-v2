package co.nayan.c3specialist_v2.driverworksummary

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.impl.CityKmlManagerImpl
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3specialist_v2.utils.endTime
import co.nayan.c3specialist_v2.utils.startTime
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.CityWards
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.Events
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.SurgeLocationsResponse
import co.nayan.c3v2.core.models.c3_module.responses.VideooCoordinatesResponse
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_15
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ObjectOfInterestMapsViewModel @Inject constructor(
    private val driverWorkSummaryRepository: DriverWorkSummaryRepository,
    val userRepository: UserRepository,
    val sharedStorage: SharedStorage,
    private val cityKmlManagerImpl: CityKmlManagerImpl
) : BaseViewModel() {

    fun saveLastLocation(latLng: LatLng) {
        sharedStorage.saveLastLocation(latLng)
    }
    fun getLastLocation() = sharedStorage.getLastLocation()

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _stats: MutableLiveData<VideooCoordinatesResponse?> = MutableLiveData(null)
    val stats: LiveData<VideooCoordinatesResponse?> = _stats

    private val _events: MutableLiveData<MutableList<Events>> = MutableLiveData(null)
    val events: LiveData<MutableList<Events>> = _events

    fun fetchVideoCoordinates(
        startDate: String?,
        endDate: String?
    ) = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        try {
            val response = driverWorkSummaryRepository.fetchVideoCoordinates(
                startDate?.startTime(),
                endDate?.endTime()
            )
            _stats.value = response
            _state.value = FinishedState
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _surgeLocationResponse: MutableLiveData<SurgeLocationsResponse?> = MutableLiveData()
    val surgeLocationResponse: LiveData<SurgeLocationsResponse?> = _surgeLocationResponse

    fun fetchSurgeLocations() = viewModelScope.launch(exceptionHandler) {
        try {
            val currentTimeMillis = System.currentTimeMillis()
            val diff = currentTimeMillis - sharedStorage.getLastSyncSurgeLocations()
            _state.value = ProgressState
            val response = if (diff > DELAYED_15) driverWorkSummaryRepository.fetchSurgeLocations()
            else sharedStorage.getSurgeLocationResponse()
            response?.let {
                sharedStorage.saveSurgeLocationResponse(it)
                _surgeLocationResponse.value = it
                fetchCityKmlBoundaries(currentTimeMillis, it.cityWards ?: mutableListOf())
            }
            _state.value = FinishedState
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchCityKmlBoundaries(
        currentTimeMillis: Long,
        wards: MutableList<CityWards>
    ) = viewModelScope.launch {
        val lastSyncCityKmlBoundaries = sharedStorage.getLastSyncCityKmlBoundaries()
        val elapsedTimeMillis = currentTimeMillis - lastSyncCityKmlBoundaries
        val shouldFreshDownload = (elapsedTimeMillis > TimeUnit.HOURS.toMillis(24))
        cityKmlManagerImpl.fetchCityWards(
            shouldFreshDownload,
            sharedStorage.getLastLocation(),
            wards
        )
    }

    fun getEvents() = viewModelScope.launch(exceptionHandler) {
        val cachedEvents = sharedStorage.getEventList()
        _events.value = cachedEvents?.ifEmpty { mutableListOf() }

        try {
            val currentTimeMillis = System.currentTimeMillis()
            val diff = currentTimeMillis - sharedStorage.getLastEventListSync()
            if (diff > DELAYED_15) {
                driverWorkSummaryRepository.getEvents()?.let {
                    _events.value = it.data
                    sharedStorage.saveEventList(it.data)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    fun updateLastSurgeDisplayed() {
        sharedStorage.updateLastSurgeDisplayed()
    }

    fun getLastSurgeDisplayed() = sharedStorage.getLastSurgeDisplayed()
}