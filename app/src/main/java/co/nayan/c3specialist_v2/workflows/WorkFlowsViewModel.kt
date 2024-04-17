package co.nayan.c3specialist_v2.workflows

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.WorkFlow
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WorkFlowsViewModel @Inject constructor(
    private val workFlowsRepository: WorkFlowsRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    fun fetchWorkFlows() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = workFlowsRepository.fetchWorkFlows()
            _state.value = if (response.isNullOrEmpty()) {
                NoWorkflowState
            } else {
                val filteredWorkFlows =
                    response.sortedBy { it.priority }.sortedBy { it.enabled?.not() }
                FetchWorkflowsSuccessState(filteredWorkFlows)
            }
        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    object NoWorkflowState : ActivityState()
    data class FetchWorkflowsSuccessState(val workFlows: List<WorkFlow>) : ActivityState()
}