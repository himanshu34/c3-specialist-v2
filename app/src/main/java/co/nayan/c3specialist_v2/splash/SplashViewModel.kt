package co.nayan.c3specialist_v2.splash

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.getDistanceInMeter
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.c3_module.responses.AllowedLocation
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val splashRepository: SplashRepository,
    private val sharedStorage: SharedStorage
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    fun getLastLocationReceivedTime() = sharedStorage.getLastLocationReceivedTime()

    fun saveLastLocation(latLng: LatLng) = viewModelScope.launch {
        sharedStorage.saveLastLocation(latLng)
    }

    fun isUserAllowedToUseApplication(
        currentLocation: LatLng? = null
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val validLocation = currentLocation ?: sharedStorage.getLastLocation()
        val allowedLocations = sharedStorage.getAllowedLocation()
        if (allowedLocations.isNullOrEmpty().not()) {
            val isUserAllowed = isLocationAllowed(
                validLocation,
                allowedLocations
            )
            sharedStorage.setUserAllowedToUseApplication(isUserAllowed)
            _state.postValue(AllowedLocationState(isUserAllowed))
        } else _state.postValue(AllowedLocationState(false))
    }

    fun fetchAllowedLocations(
        currentLocation: LatLng?
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        try {
            if (_state.value != FetchingAllowedLocations) {
                _state.postValue(FetchingAllowedLocations)
                val response = splashRepository.fetchAllowedLocation()
                sharedStorage.setAllowedLocation(response)
                _state.postValue(FinishedState)
            }
        } catch (e: Exception) {
            Timber.e(e)
            Firebase.crashlytics.recordException(e)
        }

        isUserAllowedToUseApplication(currentLocation)
    }

    private suspend fun isLocationAllowed(
        currentLocation: LatLng?,
        allowedLocations: List<AllowedLocation>?
    ): Boolean = withContext(Dispatchers.Default) {
        currentLocation?.let { location ->
            allowedLocations?.find { it.isActiveAndWithinRadius(location) } != null
        } ?: false
    }

    private fun AllowedLocation.isActiveAndWithinRadius(currentLocation: LatLng): Boolean {
        return active == true && latitude != null && longitude != null && radius != null &&
                getDistanceInMeter(
                    currentLocation,
                    latitude,
                    longitude
                ) <= radius!!.toFloat() * 1000
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    data class AllowedLocationState(val isAllowed: Boolean) : ActivityState()

    fun registerFCMToken() = viewModelScope.launch {
        FirebaseMessaging.getInstance().token.addOnCompleteListener {
            if (it.isSuccessful.not()) return@addOnCompleteListener

            CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
                userRepository.registerFCMToken(it.result)
            }
        }
    }

    object FetchingAllowedLocations : ActivityState()
    object ReferralSuccessState : ActivityState()
}