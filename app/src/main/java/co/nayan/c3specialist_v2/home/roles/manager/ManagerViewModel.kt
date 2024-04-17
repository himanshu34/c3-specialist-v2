package co.nayan.c3specialist_v2.home.roles.manager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.home.roles.RoleBaseRepository
import co.nayan.c3specialist_v2.home.roles.specialist.*
import co.nayan.c3specialist_v2.storage.FileManager
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ManagerViewModel @Inject constructor(
    private val managerRepository: ManagerRepository,
    private val userRepository: UserRepository,
    private val fileManager: FileManager,
    private val roleBaseRepository: RoleBaseRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _stats: MutableLiveData<StatsResponse?> = MutableLiveData(null)
    val stats: LiveData<StatsResponse?> = _stats

    fun assignWork() = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = managerRepository.assignWork()
        if (response?.locked == true) {
            _state.value = ManagerAccountLockedState(
                response.message,
                response.incorrectSniffingRecords,
                response.wfStep
            )
        } else {
            val workAssignment = response?.workAssignment
            if (workAssignment == null) {
                val workRequestId = response?.workRequestId
                _state.value = if (workRequestId == null) WorkAssignmentFailureState
                else WorkRequestingState(workRequestId)
            } else setupWork(workAssignment)
        }
    }

    fun setupWork(workAssignment: WorkAssignment) = viewModelScope.launch(exceptionHandler) {
        setCanvasRole(Role.MANAGER)
        _state.value = if (shouldDownloadCameraAIModel(workAssignment))
            DownloadingAIEngineState(workAssignment)
        else {
            val potentialEarning = workAssignment.potentialPoints
            if (workAssignment.isEarningShown || potentialEarning.isNullOrEmpty()) {
                if (workAssignment.faqRequired == true) FaqNotSeenState(workAssignment)
                else WorkAssignmentSuccessState(workAssignment)
            } else EarningAlertState(workAssignment)
        }
    }

    fun fetchUserStats() = viewModelScope.launch {
        if (isManagerActive().not()) return@launch

        _state.value = ProgressState
        val response = managerRepository.fetchUserStats()
        _stats.value = response
        _state.value = FinishedState
    }

    fun getUserEmail() = userRepository.getUserInfo()?.email

    fun getUserName() = userRepository.getUserInfo()?.name

    fun setCanvasRole(role: String) {
        roleBaseRepository.setCanvasRole(role)
    }

    private fun shouldDownloadCameraAIModel(workAssignment: WorkAssignment?): Boolean {
        return workAssignment?.workType == WorkType.ANNOTATION &&
                workAssignment.wfStep?.aiAssistEnabled == true &&
                fileManager.shouldDownload(workAssignment.wfStep?.cameraAiModel)
    }

    fun saveDownloadDetailsFor(cameraAIModel: CameraAIModel?) {
        cameraAIModel?.let { fileManager.saveDownloadDetailsFor(cameraAIModel) }
    }

    fun isManagerActive() = userRepository.getUserRoles().contains(Role.MANAGER)

    fun saveLearningVideoCompletedFor(applicationMode: String?) {
        roleBaseRepository.saveLearningVideoCompletedFor(applicationMode)
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    fun getContrast(): Int {
        return managerRepository.getContrast()
    }

    fun getUserInfo() = userRepository.getUserInfo()

    data class ManagerAccountLockedState(
        val message: String?,
        val incorrectSniffingRecords: List<Record>?,
        val wfStep: WfStep?
    ) : ActivityState()
}

