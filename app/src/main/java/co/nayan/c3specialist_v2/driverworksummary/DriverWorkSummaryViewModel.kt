package co.nayan.c3specialist_v2.driverworksummary

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
import co.nayan.c3v2.core.models.c3_module.responses.DriverStatsResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DriverWorkSummaryViewModel @Inject constructor(
    private val driverWorkSummaryRepository: DriverWorkSummaryRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _stats: MutableLiveData<DriverStatsResponse?> = MutableLiveData(null)
    val stats: LiveData<DriverStatsResponse?> = _stats

    fun fetchUserStats(
        startDate: String,
        endDate: String
    ) = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response =
            driverWorkSummaryRepository.fetchUserStats(startDate.startTime(), endDate.endTime())
        _state.value = if (response != null) {
            _stats.value = response
            FinishedState
        } else StatsErrorState
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    object StatsErrorState : ActivityState()
}