package co.nayan.c3specialist_v2.incorrect_records_wf_steps

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.utils.endTime
import co.nayan.c3specialist_v2.utils.startTime
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectWfStep
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class IncorrectRecordsWfStepsViewModel @Inject
constructor(private val incorrectRecordsWfStepsRepository: IncorrectRecordsWfStepsRepository) :
    BaseViewModel() {

    var startDate: String? = null
    var endDate: String? = null
    var workType: String? = null
    var userRole: String? = null
    var userId: Int? = null
    var isForMember: Boolean = false

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    fun fetchIncorrectRecordsWfSteps() {
        when (workType) {
            WorkType.ANNOTATION -> fetchIncorrectAnnotationsWfSteps()
            WorkType.VALIDATION -> fetchIncorrectJudgmentsWfSteps()
            WorkType.REVIEW -> fetchIncorrectReviewsWfSteps()
        }
    }

    private fun fetchIncorrectAnnotationsWfSteps() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = if (isForMember) {
                fetchMemberIncorrectAnnotationsWfSteps()
            } else {
                if (userRole == Role.SPECIALIST) {
                    fetchSpecialistIncorrectAnnotationsWfSteps()
                } else {
                    fetchManagerIncorrectAnnotationsWfSteps()
                }
            }
            _state.value = if(response.isNullOrEmpty()) {
                NoWfStepState
            } else {
                SetUpIncorrectRecordsWfStepsState(response)
            }
        }
    }

    private fun fetchIncorrectJudgmentsWfSteps() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = if (isForMember) {
                fetchMemberIncorrectJudgmentsWfSteps()
            } else {
                if (userRole == Role.SPECIALIST) {
                    fetchSpecialistIncorrectJudgmentsWfSteps()
                } else {
                    fetchManagerIncorrectJudgmentsWfSteps()
                }
            }
            _state.value = if(response.isNullOrEmpty()) {
                NoWfStepState
            } else {
                SetUpIncorrectRecordsWfStepsState(response)
            }
        }
    }

    private fun fetchIncorrectReviewsWfSteps() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = if (isForMember) {
                fetchMemberIncorrectReviewsWfSteps()
            } else {
                fetchManagerIncorrectReviewsWfSteps()
            }
            _state.value = if(response.isNullOrEmpty()) {
                NoWfStepState
            } else {
                SetUpIncorrectRecordsWfStepsState(response)
            }
        }
    }

    private suspend fun fetchSpecialistIncorrectAnnotationsWfSteps() =
        incorrectRecordsWfStepsRepository.fetchSpecialistIncorrectAnnotationsWfSteps(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime()
        )

    private suspend fun fetchSpecialistIncorrectJudgmentsWfSteps() =
        incorrectRecordsWfStepsRepository.fetchSpecialistIncorrectJudgmentsWfSteps(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime()
        )

    private suspend fun fetchManagerIncorrectAnnotationsWfSteps() =
        incorrectRecordsWfStepsRepository.fetchManagerIncorrectAnnotationsWfSteps(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime()
        )

    private suspend fun fetchManagerIncorrectJudgmentsWfSteps() =
        incorrectRecordsWfStepsRepository.fetchManagerIncorrectJudgmentsWfSteps(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime()
        )

    private suspend fun fetchManagerIncorrectReviewsWfSteps() =
        incorrectRecordsWfStepsRepository.fetchManagerIncorrectReviewsWfSteps(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime()
        )

    private suspend fun fetchMemberIncorrectAnnotationsWfSteps() =
        incorrectRecordsWfStepsRepository.fetchMemberIncorrectAnnotationsWfSteps(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            userId = userId,
            userRole = userRole
        )

    private suspend fun fetchMemberIncorrectJudgmentsWfSteps() =
        incorrectRecordsWfStepsRepository.fetchMemberIncorrectJudgmentsWfSteps(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            userId = userId,
            userRole = userRole
        )

    private suspend fun fetchMemberIncorrectReviewsWfSteps() =
        incorrectRecordsWfStepsRepository.fetchMemberIncorrectReviewsWfSteps(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            userId = userId,
            userRole = userRole
        )

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    object NoWfStepState : ActivityState()

    data class SetUpIncorrectRecordsWfStepsState(
        val wfSteps: List<IncorrectWfStep>
    ) : ActivityState()
}