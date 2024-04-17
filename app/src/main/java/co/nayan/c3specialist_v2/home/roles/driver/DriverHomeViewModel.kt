package co.nayan.c3specialist_v2.home.roles.driver

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.LearningVideosCategory.DELAYED_1
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3specialist_v2.utils.currentDate
import co.nayan.c3specialist_v2.utils.endTime
import co.nayan.c3specialist_v2.utils.startTime
import co.nayan.c3v2.core.config.Role.DRIVER
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.NoVideosState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.c3_module.responses.DriverStatsResponse
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_15
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class DriverHomeViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val userRepository: UserRepository,
    private val sharedStorage: SharedStorage
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _stats: MutableLiveData<DriverStatsResponse?> = MutableLiveData(null)
    val stats: LiveData<DriverStatsResponse?> = _stats

    fun saveLastLocation(latLng: LatLng) = viewModelScope.launch {
        sharedStorage.saveLastLocation(latLng)
    }

    fun getUserName() = userRepository.getUserInfo()?.name
    fun getUserEmail() = userRepository.getUserInfo()?.email
    fun isSurveyor() = userRepository.getUserInfo()?.isSurveyor ?: false

    fun fetchUserStats() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val currentDate = Calendar.getInstance().currentDate()
        _state.postValue(ProgressState)
        val response = driverRepository.fetchUserStats(
            currentDate.startTime(),
            currentDate.endTime()
        )
        _stats.postValue(response)
        _state.postValue(FinishedState)
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    fun getEvents() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val currentTimeMillis = System.currentTimeMillis()
        val diff = currentTimeMillis - sharedStorage.getLastEventListSync()
        if (diff > DELAYED_15)
            driverRepository.getEvents()?.let { sharedStorage.saveEventList(it.data) }
    }

    fun setDriverLearningVideoCompleted() = viewModelScope.launch {
        userRepository.setDriverLearningVideoCompleted()
    }

    fun isDriverTutorialDone() = userRepository.isDriverTutorialDone()

    fun setupLearningVideo() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        try {
            _state.postValue(ProgressState)
            val lastSync = sharedStorage.getLastSyncLearningVideos()
            val diff = System.currentTimeMillis() - lastSync
            if (lastSync == 0L || diff > DELAYED_1) getUnsynchedVideos()
            else {
                val driverIntroVideos = sharedStorage.getIntroductionVideos(DRIVER)
                if (driverIntroVideos.isNullOrEmpty()) getUnsynchedVideos()
                else _state.postValue(DriverLearningVideoState(driverIntroVideos.first()))
            }
        } catch (e: Exception) {
            throwException(e)
        }
    }

    private fun getUnsynchedVideos() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val response = driverRepository.fetchLearningVideos()
        val value = if (response != null && response.success) {
            sharedStorage.setLearningVideos(response.data)

            val introVideos = response.data?.introductionVideos?.driver
            if (introVideos.isNullOrEmpty()) NoVideosState
            else DriverLearningVideoState(introVideos.first())
        } else NoVideosState

        _state.postValue(value)
    }

    fun isLearningVideosEnabled() = userRepository.isLearningVideosEnabled()

    data class DriverLearningVideoState(val video: Video?) : ActivityState()
}