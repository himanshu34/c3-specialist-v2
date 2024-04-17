package co.nayan.c3specialist_v2.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RoleRequestViewModel @Inject constructor(
    private val roleRequestRepository: RoleRequestRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    fun createRoles(roles: List<String>) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = roleRequestRepository.createRoles(roles)
            _state.value = CrateRoleRequestState(response.success == true)
        }
    }

    fun getRolesRequest() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = roleRequestRepository.getRolesRequest()
            _state.value = GetRoleRequestState(!(response.request.isNullOrEmpty()))
        }
    }

    data class CrateRoleRequestState(val isRoleCreated: Boolean) : ActivityState()
    data class GetRoleRequestState(val isAlreadyRequested: Boolean) : ActivityState()

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }
}