package co.nayan.c3specialist_v2.workrequeststatus

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.WorkAssignment
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WorkRequestStatusViewModel @Inject constructor(
    private val workRequestStatusRepository: WorkRequestStatusRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state
    private var workRequestId: Int? = null

    private var isChecking: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            handler.postDelayed(this, STATUS_CHECK_DURATION)
            getStatus()
        }
    }

    fun startStatusCheck() {
        handler.removeCallbacks(runnable)
        handler.post(runnable)
    }

    fun getStatus() {
        if (isChecking) return
        isChecking = true
        viewModelScope.launch(exceptionHandler) {
            val response = workRequestStatusRepository.getStatus(workRequestId)
            when (response?.status) {
                WorkRequestStatus.QUEUED -> {
                    _state.value = WorkRequestProgressState(0)
                }
                WorkRequestStatus.CREATED -> {
                    _state.value = WorkRequestProgressState(0)
                }
                WorkRequestStatus.FAILED -> {
                    handler.removeCallbacks(runnable)
                    _state.value = WorkRequestFailedState
                }
                WorkRequestStatus.ASSIGNED -> {
                    handler.removeCallbacks(runnable)
                    _state.value = WorkRequestSuccessState(response.workAssignment)
                }
                WorkRequestStatus.NO_WORK -> {
                    handler.removeCallbacks(runnable)
                    _state.value = WorkRequestNoWorkState
                }
                else -> {
                    handler.removeCallbacks(runnable)
                    _state.value = WorkRequestFailedState
                }
            }
            isChecking = false
        }
    }

    fun setWorkRequestId(toSet: Int?) {
        workRequestId = toSet
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    data class WorkRequestProgressState(val progress: Int) : ActivityState()
    data class WorkRequestSuccessState(val workAssignment: WorkAssignment?) : ActivityState()
    object WorkRequestFailedState : ActivityState()
    object WorkRequestNoWorkState : ActivityState()

    companion object {
        const val STATUS_CHECK_DURATION = 4_000L
    }

    object WorkRequestStatus {
        const val QUEUED = "queued"
        const val CREATED = "created"
        const val FAILED = "failed"
        const val ASSIGNED = "assigned"
        const val NO_WORK = "no_work"
    }
}