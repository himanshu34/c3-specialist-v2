package co.nayan.c3specialist_v2.home.roles.leader

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.LeaderHomeStatsResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LeaderViewModel @Inject constructor(
    private val leaderRepository: LeaderRepository,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _stats: MutableLiveData<LeaderHomeStatsResponse?> = MutableLiveData(null)
    val stats: LiveData<LeaderHomeStatsResponse?> = _stats

    fun fetchUserStats() = viewModelScope.launch(exceptionHandler) {
        if (isLeaderActive().not()) return@launch

        _state.value = ProgressState
        val response = leaderRepository.fetchLeaderHomeStats()
        _stats.value = response
        _state.value = FinishedState
    }

    fun getUserEmail() = userRepository.getUserInfo()?.email

    fun getUserName() = userRepository.getUserInfo()?.name

    fun isLeaderActive() = userRepository.getUserRoles().contains(Role.LEADER)

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }
}