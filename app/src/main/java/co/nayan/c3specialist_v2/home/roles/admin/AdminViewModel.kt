package co.nayan.c3specialist_v2.home.roles.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.home.roles.specialist.FetchWorkflowStepsSuccessState
import co.nayan.c3specialist_v2.home.roles.specialist.NoWorkflowStepsState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentFailureState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentSuccessState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkRequestingState
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.ActiveWfStep
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _stats: MutableLiveData<StatsResponse?> = MutableLiveData(null)
    val stats: LiveData<StatsResponse?> = _stats

    fun fetchUserStats() = viewModelScope.launch(exceptionHandler) {
        if (isAdminActive().not()) return@launch

        _state.value = ProgressState
        val response = adminRepository.fetchUserStats()
        _stats.value = response
        _state.value = FinishedState
    }

    fun requestWorkStepToWork() = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = adminRepository.requestWorkStepToWork()
        _state.value = if (response.success) {
            if (response.data.isNullOrEmpty()) NoWorkflowStepsState
            else FetchWorkflowStepsSuccessState(response.data ?: emptyList())
        } else NoWorkflowStepsState
    }

    fun assignWork(wfStep: ActiveWfStep) = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = adminRepository.assignWork(wfStep.id)
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

    fun getUserEmail() = userRepository.getUserInfo()?.email
    fun getUserName() = userRepository.getUserInfo()?.name

    fun setCanvasRole(role: String) {
        adminRepository.setCanvasRole(role)
    }

    fun isAdminActive() = userRepository.getUserRoles().contains(Role.ADMIN)
    fun getUserInfo() = userRepository.getUserInfo()
}