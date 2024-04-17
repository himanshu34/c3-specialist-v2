package co.nayan.c3specialist_v2.home.roles.specialist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.home.roles.RoleBaseRepository
import co.nayan.c3specialist_v2.storage.FileManager
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.config.Role.MANAGER
import co.nayan.c3v2.core.config.Role.SPECIALIST
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.responses.SandboxTrainingResponse
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SpecialistViewModel @Inject constructor(
    private val specialistRepository: SpecialistRepository,
    private val userRepository: UserRepository,
    private val fileManager: FileManager,
    private val sharedStorage: SharedStorage,
    private val roleBaseRepository: RoleBaseRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _stats: MutableLiveData<StatsResponse?> = MutableLiveData(null)
    val stats: LiveData<StatsResponse?> = _stats

    /*fun assignManagerWork() = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = specialistRepository.assignManagerWork()
        val workAssignment = response?.workAssignment
        if (workAssignment == null) {
            val workRequestId = response?.workRequestId
            if (workRequestId == null) assignWork()
            else _state.value = WorkRequestingState(workRequestId, MANAGER)
        } else {
            setCanvasRole(MANAGER)
            setupWork(workAssignment)
        }
    }*/

    fun assignWork() = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = specialistRepository.assignWork()
        val workAssignment = response?.workAssignment
        if (workAssignment == null) {
            val workRequestId = response?.workRequestId
            _state.value = if (workRequestId == null) WorkAssignmentFailureState
            else WorkRequestingState(workRequestId, SPECIALIST)
        } else {
            setCanvasRole(SPECIALIST)
            setupWork(workAssignment)
        }
    }

    fun setupWork(workAssignment: WorkAssignment) = viewModelScope.launch(exceptionHandler) {
        if (workAssignment.sandboxRequired == true) setupSandboxWork(workAssignment)
        else setupNormalWork(workAssignment)
    }

    private fun setupNormalWork(workAssignment: WorkAssignment) = viewModelScope.launch {
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

    private fun setupSandboxWork(workAssignment: WorkAssignment) = viewModelScope.launch {
        if (workAssignment.faqRequired == true) _state.value = FaqNotSeenState(workAssignment)
        else {
            val wfStep = workAssignment.wfStep
            sandboxTraining(wfStep?.id, wfStep?.mediaType, wfStep?.annotationVariationThreshold)
        }
    }

    private fun sandboxTraining(
        wfStepId: Int?,
        mediaType: String?,
        annotationVariationThreshold: Int?
    ) = viewModelScope.launch(exceptionHandler) {
        val response = specialistRepository.sandboxTraining(wfStepId)
        _state.value = if (response != null) {
            response.wfStepId = wfStepId
            response.mediaType = mediaType
            response.annotationVariationThreshold = annotationVariationThreshold
            SandboxTrainingSetupSuccessState(response)
        } else WorkAssignmentFailureState
    }

    fun fetchUserStats() = viewModelScope.launch(exceptionHandler) {
        if (isSpecialistActive().not()) return@launch

        _state.value = ProgressState
        val response = specialistRepository.fetchUserStats()
        _stats.value = response
        _state.value = FinishedState
    }

    private fun shouldDownloadCameraAIModel(workAssignment: WorkAssignment?): Boolean {
        return workAssignment?.workType == WorkType.ANNOTATION &&
                workAssignment.wfStep?.aiAssistEnabled == true &&
                fileManager.shouldDownload(workAssignment.wfStep?.cameraAiModel)
    }

    fun getUserEmail() = userRepository.getUserInfo()?.email

    fun setCanvasRole(role: String) {
        roleBaseRepository.setCanvasRole(role)
    }

    fun currentRole(): String? {
        return roleBaseRepository.currentRole()
    }

    fun getUserName() = userRepository.getUserInfo()?.name
    fun getWalkThroughEnabled() = userRepository.getUserInfo()?.walkThroughEnabled
    fun getUserInfo() = userRepository.getUserInfo()

    fun saveDownloadDetailsFor(cameraAIModel: CameraAIModel?) {
        cameraAIModel?.let { fileManager.saveDownloadDetailsFor(cameraAIModel) }
    }

    fun isSpecialistActive() = userRepository.getUserRoles().contains(SPECIALIST)
    fun isManagerActive() = userRepository.getUserRoles().contains(MANAGER)

    fun getAppLanguage() = userRepository.getAppLanguage()

    fun getSpecialistIntroVideo() = viewModelScope.launch {
        _state.value = if (userRepository.isApplicationTutorialDone()) AppIntroVideoState(null)
        else {
            val introVideos = sharedStorage.getIntroductionVideos(SPECIALIST)
            AppIntroVideoState(introVideos?.firstOrNull())
        }
    }

    fun saveIntroVideoCompleted() {
        userRepository.saveAppLearningVideoCompletedFor()
    }

    fun saveLearningVideoCompletedFor(applicationMode: String?) {
        roleBaseRepository.saveLearningVideoCompletedFor(applicationMode)
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }
}

data class WorkAssignmentSuccessState(val workAssignment: WorkAssignment) : ActivityState()
data class SandboxTrainingSetupSuccessState(
    val sandboxTrainingResponse: SandboxTrainingResponse?
) : ActivityState()

data class DownloadingAIEngineState(val workAssignment: WorkAssignment) : ActivityState()
object WorkAssignmentFailureState : ActivityState()
object NoWorkflowStepsState : ActivityState()
data class FetchWorkflowStepsSuccessState(val wfSteps: List<ActiveWfStep>) : ActivityState()
data class WorkRequestingState(val workRequestId: Int, val role: String? = null) : ActivityState()
data class FaqNotSeenState(val workAssignment: WorkAssignment) : ActivityState()
data class EarningAlertState(val workAssignment: WorkAssignment) : ActivityState()
data class AppIntroVideoState(val video: Video?) : ActivityState()