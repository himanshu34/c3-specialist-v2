package co.nayan.c3specialist_v2.videogallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.LearningVideosCategory.DELAYED_1
import co.nayan.c3specialist_v2.config.LearningVideosCategory.INTRODUCTION
import co.nayan.c3specialist_v2.config.LearningVideosCategory.VIOLATION
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.config.Role.ADMIN
import co.nayan.c3v2.core.config.Role.DRIVER
import co.nayan.c3v2.core.config.Role.LEADER
import co.nayan.c3v2.core.config.Role.MANAGER
import co.nayan.c3v2.core.config.Role.SPECIALIST
import co.nayan.c3v2.core.models.*
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VideoGalleryViewModel @Inject constructor(
    private val videoGalleryRepository: VideoGalleryRepository,
    private val sharedStorage: SharedStorage
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    var currentSelectedRole: String = ""
    var selectedTab: Int = 0

    fun fetchVideos() {
        when (selectedTab) {
            0 -> fetchLearningVideos()
            else -> fetchViolationVideos()
        }
    }

    private fun fetchLearningVideos() = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val lastSync = sharedStorage.getLastSyncLearningVideos()
        val diff = System.currentTimeMillis() - lastSync
        if (lastSync == 0L || diff > DELAYED_1) getUnsynchedVideos()
        else {
            val learningVideos = sharedStorage.getLearningVideos(currentSelectedRole)
            if (learningVideos.isNullOrEmpty()) getUnsynchedVideos()
            else _state.value = FetchLearningVideosPlaylistSuccessState(learningVideos)
        }
    }

    private fun getUnsynchedVideos(type: String? = null) = viewModelScope.launch(exceptionHandler) {
        val response = videoGalleryRepository.fetchLearningVideos()
        _state.value = if (response != null && response.success) {
            sharedStorage.setLearningVideos(response.data)

            when (type) {
                VIOLATION -> {
                    if (response.data?.violationVideos.isNullOrEmpty().not())
                        FetchLearningVideosPlaylistSuccessState(response.data?.violationVideos)
                    else NoVideosState
                }

                INTRODUCTION -> {
                    val introVideos = when (currentSelectedRole) {
                        DRIVER -> response.data?.introductionVideos?.driver
                        SPECIALIST -> response.data?.introductionVideos?.specialist
                        MANAGER -> response.data?.introductionVideos?.manager
                        LEADER -> response.data?.introductionVideos?.leader
                        ADMIN -> response.data?.introductionVideos?.admin
                        else -> null
                    }

                    if (introVideos.isNullOrEmpty().not())
                        FetchLearningVideosPlaylistSuccessState(introVideos)
                    else NoVideosState
                }

                else -> {
                    val responseVideos = when (currentSelectedRole) {
                        DRIVER -> response.data?.learningVideos?.driver
                        SPECIALIST -> response.data?.learningVideos?.specialist
                        MANAGER -> response.data?.learningVideos?.manager
                        LEADER -> response.data?.learningVideos?.leader
                        ADMIN -> response.data?.learningVideos?.admin
                        else -> null
                    }

                    if (responseVideos.isNullOrEmpty().not())
                        FetchLearningVideosPlaylistSuccessState(responseVideos)
                    else NoVideosState
                }
            }
        } else NoVideosState
    }

    private fun fetchViolationVideos() = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val lastSync = sharedStorage.getLastSyncLearningVideos()
        val diff = System.currentTimeMillis() - lastSync
        if (lastSync == 0L || diff > DELAYED_1) getUnsynchedVideos(VIOLATION)
        else {
            val violationVideos = sharedStorage.getViolationVideos()
            if (violationVideos.isNullOrEmpty()) getUnsynchedVideos(VIOLATION)
            else _state.value = FetchLearningVideosPlaylistSuccessState(violationVideos)
        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    data class FetchLearningVideosPlaylistSuccessState(
        val learningVideos: MutableList<Video>?
    ) : ActivityState()
}