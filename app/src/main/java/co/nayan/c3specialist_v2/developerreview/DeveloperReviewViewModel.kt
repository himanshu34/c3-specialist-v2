package co.nayan.c3specialist_v2.developerreview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3v2.core.models.*
import co.nayan.review.recordsgallery.RecordItem
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DeveloperReviewViewModel @Inject constructor(
    private val developerReviewRepository: DeveloperReviewRepository
) : BaseViewModel() {

    var question: String? = null
    var applicationMode: String? = null
    var workAssignmentId: Int? = null

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _contrastValue: MutableLiveData<Int> =
        MutableLiveData(developerReviewRepository.getContrast())
    val contrastValue: LiveData<Int> = _contrastValue

    private val records = mutableListOf<Record>()

    fun fetchTrainingRecords() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val newRecords = developerReviewRepository.fetchTrainingRecords(workAssignmentId)
            _state.value = if (newRecords.isNullOrEmpty()) NoRecordState
            else {
                records.clear()
                records.addAll(newRecords)
                SetUpRecordsState(records)
            }
        }
    }

    fun updateRecords(remainingRecords: List<Record>) {
        records.clear()
        records.addAll(remainingRecords)
        _state.value = if (records.isNotEmpty()) SetUpRecordsState(records) else NoRecordState
    }

    fun rejectRecords(ids: List<Int>) {
        viewModelScope.launch(exceptionHandler) {
            val success = developerReviewRepository.submitReview(
                rejectedIds = ids, approvedIds = emptyList()
            )
            _state.value = if (success) {
                records.removeAll { ids.contains(it.id) }
                if (records.isNotEmpty()) SetUpRecordsState(records)
                else NoRecordState
            } else FailureState
        }
    }

    fun approveAllRecords() {
        viewModelScope.launch(exceptionHandler) {
            val ids = records.map { it.id }
            val success = developerReviewRepository.submitReview(
                rejectedIds = emptyList(), approvedIds = ids
            )
            _state.value = if (success) NoRecordState
            else FailureState
        }
    }

    fun approveRecords(ids: List<Int>) {
        viewModelScope.launch(exceptionHandler) {
            val success = developerReviewRepository.submitReview(
                rejectedIds = emptyList(), approvedIds = ids
            )
            _state.value = if (success) {
                records.removeAll { ids.contains(it.id) }
                if (records.isNotEmpty()) SetUpRecordsState(records)
                else NoRecordState
            } else FailureState
        }
    }

    fun getRecords(): ArrayList<RecordItem> {
        val recordItems = arrayListOf<RecordItem>()
        records.forEach { recordItems.add(RecordItem(it)) }
        return recordItems
    }

    fun getSavedSpanCount(): Int {
        return developerReviewRepository.getSpanCount()
    }

    fun saveSpanCount(spanValue: Int) {
        developerReviewRepository.saveSpanCount(spanValue)
    }

    fun saveContrastValue(value: Int) {
        developerReviewRepository.saveContrast(value)
        _contrastValue.value = value
    }

    fun updateRecordStarredStatus(record: Record, position: Int, status: Boolean) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = RecordStatusProgressState
            val response = developerReviewRepository.updateRecordStarredStatus(record.id, status)

            if (response.success == true) {
                records.find { it.id == record.id }?.starred = status
                _state.value = RecordsStarredStatusState(position, status)
            } else _state.value = FailureState
        }
    }

    /**
     * Used to check whether current work is assigned or not
     */
    fun assignWork() {
        viewModelScope.launch(exceptionHandler) {
            val workAssignment = developerReviewRepository.assignWork()?.workAssignment
            if (workAssignmentId != workAssignment?.id) {
                _state.value = NoRecordState
            }
        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    object NoRecordState : ActivityState()
    data class SetUpRecordsState(val records: List<Record>) : ActivityState()
    object RecordStatusProgressState : ActivityState()
    data class RecordsStarredStatusState(val position: Int, val status: Boolean) :
        ActivityState()
}