package co.nayan.c3specialist_v2.dashboard

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.impl.CityKmlManagerImpl
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.config.Role.DRIVER
import co.nayan.c3v2.core.config.Role.SPECIALIST
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.NoVideosState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.c3_module.requests.UpdatePersonalInfoRequest
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_15
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val dashboardRepository: DashboardRepository,
    private val sharedStorage: SharedStorage,
    private val cityKmlManagerImpl: CityKmlManagerImpl,
    private val deviceInfoHelperImpl: DeviceInfoHelperImpl
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    fun getUserInfo() = userRepository.getUserInfo()

    fun getAppLanguage() = userRepository.getAppLanguage()

    fun fetchMinVersionCodeRequired() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        _state.postValue(ProgressState)
        val response = dashboardRepository.fetchMinVersionCodeRequired()
        val value = if (response != null) {
            val minVersionRequired = response.minAppVersion
            sharedStorage.saveAppConfigData(response)
            if (minVersionRequired != null && BuildConfig.VERSION_CODE >= minVersionRequired)
                InitialState else UpdateAppState(minVersionRequired)
        } else InitialState
        _state.postValue(value)
    }

    fun fetchSurgeLocations() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val currentTime = System.currentTimeMillis()
        val diff = currentTime - sharedStorage.getLastSyncSurgeLocations()
        if (diff > DELAYED_15) {
            val response = dashboardRepository.fetchSurgeLocations()
            if (response != null && response.success) {
                sharedStorage.saveSurgeLocationResponse(response)
                response.cityWards?.let { wards ->
                    val lastSyncCityKmlBoundaries = sharedStorage.getLastSyncCityKmlBoundaries()
                    val elapsedTimeMillis = currentTime - lastSyncCityKmlBoundaries
                    val shouldFreshDownload = (elapsedTimeMillis > TimeUnit.HOURS.toMillis(24))
                    cityKmlManagerImpl.fetchCityWards(
                        shouldFreshDownload,
                        sharedStorage.getLastLocation(),
                        wards
                    )
                }
            }
        }
    }

    fun fetchUserDetails() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        try {
            val savedActiveRoles = userRepository.getUserRoles()
            val user = dashboardRepository.fetchUserDetails()
            userRepository.setUserInfo(user)
            val activeRoles = user.activeRoles ?: emptyList()

            if (activeRoles.isEmpty()) _state.postValue(NoActiveRolesState)
            else {
                val rolesAdded = activeRoles - savedActiveRoles.toSet()
                val rolesRemoved = savedActiveRoles - activeRoles.toSet()

                val message = getRolesUpdateMessage(rolesAdded, rolesRemoved)
                if (message.isNotEmpty())
                    _state.postValue(RolesUpdateState(message, activeRoles))
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
            _state.postValue(FinishedState)
        }
    }

    private fun getRolesUpdateMessage(
        rolesAdded: List<String>, rolesRemoved: List<String>
    ): String {
        var message = ""
        if (rolesAdded.isNotEmpty()) {
            message += if (rolesAdded.size == 1)
                "${rolesAdded.first().uppercase(Locale.getDefault())} role is added."
            else "${rolesAdded.joinToString { it.uppercase(Locale.getDefault()) }} roles are added."
        }
        if (rolesRemoved.isNotEmpty()) {
            message += if (rolesRemoved.size == 1)
                "${rolesRemoved.first().uppercase(Locale.getDefault())} role is removed."
            else "${rolesRemoved.joinToString { "${it.uppercase(Locale.getDefault())}," }} roles are removed."
        }
        return message
    }

    fun getUserLocation(
        userName: String?,
        context: Context
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val userLocation = UserLocation()
        try {
            val lastLocation = userRepository.getLastLocation()
            val location = Location("")
            location.longitude = lastLocation.longitude
            location.latitude = lastLocation.latitude

            userLocation.latitude = location.latitude
            userLocation.longitude = location.longitude
            userLocation.speedMpS = location.speed
            userLocation.speedKpH = location.speed
            userLocation.altitude = location.altitude
            userLocation.time = location.time

            _state.postValue(ProgressState)
            val geoCoder = Geocoder(context, Locale.getDefault())
            val value = try {
                val addresses: List<Address>? = geoCoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )
                // If any additional address line present than only,
                // check with max available address lines by getMaxAddressLineIndex()
                addresses?.let {
                    if (it.isNotEmpty()) {
                        userLocation.address = it[0].getAddressLine(0)
                        userLocation.postalCode = it[0].postalCode ?: ""
                        userLocation.city = it[0].locality ?: ""
                        userLocation.state = it[0].adminArea ?: ""
                        userLocation.country = it[0].countryName ?: ""
                        userLocation.countryCode = it[0].countryCode ?: ""
                        userLocation.validLocation = true
                    }
                }
                FetchUserLocationSuccessState(userName, userLocation)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                FinishedState
            }
            _state.postValue(value)
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            _state.postValue(FinishedState)
        }
    }

    fun updateBasePersonalInfo(
        name: String?,
        address: String,
        state: String,
        city: String,
        country: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val deviceConfig = deviceInfoHelperImpl.getDeviceConfig()
        dashboardRepository.updatePersonalInfo(
            UpdatePersonalInfoRequest(
                name = name ?: "",
                address = address,
                city = city,
                state = state,
                country = country,
                model = deviceConfig?.model,
                version = deviceConfig?.version,
                buildVersion = deviceConfig?.buildVersion,
                ram = deviceConfig?.ram
            )
        )
        val user = userRepository.getUserInfo()
        user!!.address = address
        user.city = city
        user.state = state
        user.country = country
        userRepository.setUserInfo(user)
        _state.postValue(FinishedState)
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    fun setUpAppLearningVideoStatus() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val roles = userRepository.getUserRoles()
        if (userRepository.isApplicationTutorialDone() ||
            userRepository.isLearningVideosEnabled().not()
        ) return@launch

        _state.postValue(ProgressState)
        val lastSync = sharedStorage.getLastSyncLearningVideos()
        val diff = System.currentTimeMillis() - lastSync
        if (lastSync == 0L || diff > DELAYED_15) getLearningVideos(roles)
        else {
            val introVideos = sharedStorage.getIntroductionVideos(SPECIALIST)
            if (introVideos.isNullOrEmpty()) getLearningVideos(roles)
            else if (roles.contains(DRIVER).not() && roles.contains(SPECIALIST))
                _state.postValue(AppLearningVideoState(introVideos.first()))
            else _state.postValue(AppLearningVideoState(null))
        }
    }

    private fun getLearningVideos(
        roles: List<String>
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val response = dashboardRepository.fetchLearningVideos()
        val value = if (response != null && response.success) {
            sharedStorage.setLearningVideos(response.data)
            val videos = response.data?.introductionVideos?.specialist
            when {
                videos.isNullOrEmpty() -> NoVideosState
                (roles.contains(DRIVER).not() && roles.contains(SPECIALIST)) -> {
                    AppLearningVideoState(videos.first())
                }

                else -> AppLearningVideoState(null)
            }
        } else NoVideosState
        _state.postValue(value)
    }

    fun saveAppLearningVideoCompletedFor() {
        userRepository.saveAppLearningVideoCompletedFor()
    }

    object NoActiveRolesState : ActivityState()
    data class UpdateAppState(val minVersionRequired: Int?) : ActivityState()
    data class RolesUpdateState(val message: String, val activeRoles: List<String>) :
        ActivityState()

    data class AppLearningVideoState(val video: Video?) : ActivityState()
    data class FetchUserLocationSuccessState(
        val userName: String?,
        val userLocation: UserLocation
    ) : ActivityState()
}