package co.nayan.c3specialist_v2.team.request_member

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.requests.NewMemberRequest
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class RequestMemberViewModel @Inject constructor(
    private val requestMemberRepository: RequestMemberRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    fun requestTeamMember(email: String, name: String, phone: String) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val request = NewMemberRequest(email = email, name = name, phoneNumber = phone)
            val response = requestMemberRepository.requestNewMember(request)
            _state.value = if (response?.success == true) {
                FinishedState
            } else {
                ErrorState(IOException(response?.message))
            }
        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }
}