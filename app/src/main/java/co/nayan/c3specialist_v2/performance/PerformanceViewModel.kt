package co.nayan.c3specialist_v2.performance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.utils.endTime
import co.nayan.c3specialist_v2.utils.startTime
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.LeaderStats
import co.nayan.c3v2.core.models.c3_module.responses.MemberStats
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val performanceRepository: PerformanceRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _stats: MutableLiveData<StatsResponse?> = MutableLiveData(null)
    val stats: LiveData<StatsResponse?> = _stats

    fun fetchSpecialistPerformance(startDate: String, endDate: String) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = performanceRepository.fetchSpecialistPerformance(
                startDate.startTime(), endDate.endTime()
            )
            _stats.value = response
            _state.value = FinishedState
        }
    }

    fun fetchManagerPerformance(startDate: String, endDate: String) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = performanceRepository.fetchManagerPerformance(
                startDate.startTime(), endDate.endTime()
            )
            _stats.value = response
            _state.value = FinishedState
        }
    }

    fun fetchTeamMembersPerformance(startDate: String?, endDate: String?, userType: String) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = performanceRepository.fetchTeamMembersPerformance(
                startDate?.startTime(), endDate?.endTime(), userType
            )

            _state.value = if (response.isNullOrEmpty()) {
                NoStatsState
            } else {
                TeamMembersPerformanceStatsSuccessState(response)
            }
        }
    }

    fun fetchOverAllPerformance(startDate: String?, endDate: String?, userType: String) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = performanceRepository.fetchOverallTeamPerformance(
                startDate?.startTime(), endDate?.endTime(), userType
            )
            _state.value = OverallTeamPerformanceStatsSuccessState(response)
        }
    }

    fun fetchLeaderPerformance(startDate: String?, endDate: String?) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = performanceRepository.fetchLeaderPerformance(
                startDate?.startTime(), endDate?.endTime()
            )
            _state.value = LeaderPerformanceSuccessState(response)
        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    object NoStatsState : ActivityState()
    data class OverallTeamPerformanceStatsSuccessState(val membersStats: MemberStats?) :
        ActivityState()

    data class TeamMembersPerformanceStatsSuccessState(val membersStats: List<MemberStats>) :
        ActivityState()

    data class LeaderPerformanceSuccessState(val leaderStats: LeaderStats?) : ActivityState()
}