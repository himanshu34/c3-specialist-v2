package co.nayan.c3specialist_v2.incorrectrecordsdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class IncorrectRecordDetailsViewModel @Inject constructor(
    private val incorrectRecordDetailsRepository: IncorrectRecordDetailsRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    fun fetchRecord(recordId: Int?) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val response = incorrectRecordDetailsRepository.fetchRecord(recordId)
            _state.value = RecordResultState(response)
        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    data class RecordResultState(val record: Record?) : ActivityState()
}