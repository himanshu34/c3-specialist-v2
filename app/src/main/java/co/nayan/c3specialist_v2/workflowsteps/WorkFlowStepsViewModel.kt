package co.nayan.c3specialist_v2.workflowsteps

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentFailureState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentSuccessState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkRequestingState
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.WfStep
import co.nayan.c3v2.core.models.c3_module.requests.AdminWorkAssignment
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WorkFlowStepsViewModel @Inject constructor(
    private val workFlowStepsRepository: WorkFlowStepsRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    fun fetchWfSteps(workFlowId: Int) = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = workFlowStepsRepository.fetchWorkFlowSteps(workFlowId)
        _state.value = if (response.isNullOrEmpty()) NoWorkflowStepsState
        else FetchWorkflowStepsSuccessState(response.sortedBy { it.position })
    }

    fun assignWork(wfStepId: Int?) = viewModelScope.launch(exceptionHandler) {
        _state.value = ReviewWorkProgressState
        val response = workFlowStepsRepository.assignWork(AdminWorkAssignment(wfStepId))
        val workAssignment = response?.workAssignment
        _state.value = if (workAssignment == null) {
            val workRequestId = response?.workRequestId
            if (workRequestId == null) WorkAssignmentFailureState
            else WorkRequestingState(workRequestId)
        } else WorkAssignmentSuccessState(workAssignment)
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    fun setCanvasRole(role: String) {
        workFlowStepsRepository.setCanvasRole(role)
    }

    fun currentRole(): String? {
        return workFlowStepsRepository.currentRole()
    }

    object ReviewWorkProgressState : ActivityState()
    object NoWorkflowStepsState : ActivityState()
    data class FetchWorkflowStepsSuccessState(val wfSteps: List<WfStep>) : ActivityState()
}