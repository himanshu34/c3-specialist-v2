package co.nayan.review.recordsgallery

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.RecordJudgment
import co.nayan.c3v2.core.models.RecordReview
import co.nayan.c3v2.core.models.Template
import co.nayan.review.config.Timer.START_TIME_IN_MILLIS
import co.nayan.review.utils.notifyObservers
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
open class ReviewViewModel @Inject constructor(
    private val reviewRepository: ReviewRepositoryInterface
) : ViewModel() {

    var workAssignmentId: Int? = null
    var wfStepId: Int? = null
    var applicationMode: String? = null
    var userCategory: String? = null
    var appFlavor: String? = null
    var question: String? = null
    var position: Int = 0
    var isSubmittingRecords: Boolean = false

    // Sniffing Timer variables
    var mTimerRunning: Boolean = false
    var mTimeLeftInMillis: Long = START_TIME_IN_MILLIS
    var mEndTime: Long = 0

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    val _records: MutableLiveData<MutableList<RecordItem>> = MutableLiveData(mutableListOf())
    val records: LiveData<MutableList<RecordItem>> = _records

    private val _record: MutableLiveData<RecordItem> = MutableLiveData()
    val record: LiveData<RecordItem> = _record

    private val _templateState: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val templateState: LiveData<ActivityState> = _templateState

    val _contrastValue: MutableLiveData<Int> =
        MutableLiveData(reviewRepository.getContrast())
    val contrastValue: LiveData<Int> = _contrastValue

    private val _canUndo: MutableLiveData<Boolean> = MutableLiveData()
    val canUndo: LiveData<Boolean> = _canUndo

    var currentTabPosition: Int = TabPositions.PENDING

    private val incorrectSniffingRecords = arrayListOf<Record>()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _state.value = ErrorState(throwable as Exception)
    }

    companion object {
        private const val SUBMIT_TRIGGER = 5
    }

    fun fetchTemplates() {
        viewModelScope.launch(exceptionHandler) {
            try {
                _templateState.value = ProgressState
                val templates = reviewRepository.fetchTemplates(wfStepId)
                _templateState.value = TemplatesSuccessState(templates)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _templateState.value = ErrorState(e)
            }
        }
    }

    fun fetchRecords() = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val newRecords = reviewRepository.nextRecords(workAssignmentId)
        val nonSniffingRecords =
            newRecords?.filter { it.isSniffingRecord == false }?.toMutableList()
        var maxNonSniffingId = nonSniffingRecords?.maxByOrNull { it.id }?.id
        // Generate random sniffing id for sniffing records
        val sniffingRecords = newRecords?.filter { it.isSniffingRecord == true }
        if (maxNonSniffingId != null && sniffingRecords.isNullOrEmpty().not()) {
            sniffingRecords?.map {
                it.randomSniffingId = maxNonSniffingId + 50
                maxNonSniffingId++
            }
        }

        // Shuffle sniffing records
        val shuffledRecords =
            generateShuffleRecords(nonSniffingRecords, sniffingRecords?.toMutableList())
        _state.value = if (shuffledRecords.isNullOrEmpty()) RecordsFinishedState
        else {
            currentTabPosition = TabPositions.PENDING
            addNewRecords(shuffledRecords)
            setupRecords()
            RecordsSuccessState
        }
    }

    private fun generateShuffleRecords(
        nonSniffingRecords: MutableList<Record>?,
        sniffingRecords: MutableList<Record>?
    ): MutableList<Record> {
        val shuffledRecords = nonSniffingRecords ?: mutableListOf()
        if (sniffingRecords.isNullOrEmpty().not()) {
            try {
                val iterator = sniffingRecords?.iterator()
                while (iterator?.hasNext() == true) {
                    val item = iterator.next()
                    val position = (1 until shuffledRecords.size).random()
                    val subList = shuffledRecords.subList(position - 1, shuffledRecords.size - 1)
                    try {
                        if (position < subList.size && position > 0) {
                            // Map below fields from previous record
                            subList[position - 1].let { prevRecord ->
                                item.driverId = prevRecord.driverId
                                item.videoRecordedOn = prevRecord.videoRecordedOn
                                item.videoSourceId = prevRecord.videoSourceId
                                item.videoId = prevRecord.videoId
                                item.metatag = prevRecord.metatag
                                item.annotationPriority = prevRecord.annotationPriority
                                item.judgmentPriority = prevRecord.judgmentPriority
                                item.reviewPriority = prevRecord.reviewPriority
                                item.cityKmlPriority = prevRecord.cityKmlPriority
                                item.applicationMode = prevRecord.applicationMode
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    shuffledRecords.add(position, item)
                    iterator.remove()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return shuffledRecords
    }

    /**
     * Add new records if they are not already present in the local list
     */
    private suspend fun addNewRecords(toAdd: List<Record>) {
        withContext(Dispatchers.Main) {
            _records.value?.clear()
            _records.value?.let { records ->
                for (newRecord in toAdd) records.add(RecordItem(newRecord))
            }
        }
    }

    /**
     * Notify [_records] to populate records in bulk and notify [_record] to populate single record
     * and set user state [_state] to initial from progress
     */
    private fun setupRecords() {
        _records.notifyObservers()
        _record.value = _records.value?.first()
        if (_state.value == ProgressState) {
            _state.value = InitialState
        }
    }

    fun rejectRecords(ids: List<Int>) {
        _records.value?.forEach {
            if (ids.contains(it.record.id)) {
                it.isRejected = true
                it.isApproved = false
            }
        }
    }

    fun approveRecords(ids: List<Int>) {
        _records.value?.forEach {
            if (ids.contains(it.record.id)) {
                it.isApproved = true
                it.isRejected = false
            }
        }
    }

    fun approveAllRecords() {
        _records.value?.forEach {
            if (it.isApproved.not() && it.isRejected.not()) {
                it.isApproved = true
                it.isRejected = false
            }
        }
    }

    fun rejectAllRecords() {
        _records.value?.forEach {
            if (it.isApproved.not() && it.isRejected.not()) {
                it.isApproved = false
                it.isRejected = true
            }
        }
    }

    fun rejectDeveloperRecord(ids: List<Int>) {
        viewModelScope.launch(exceptionHandler) {
            try {
                _state.value = ProgressState
                val success = reviewRepository.submitDeveloperReview(
                    rejectedIds = ids, approvedIds = emptyList()
                )
                _state.value = if (success) RecordSubmittedState(ids[0]) else FailureState
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    fun approveDeveloperRecord(ids: List<Int>) {
        viewModelScope.launch(exceptionHandler) {
            try {
                _state.value = ProgressState
                val success = reviewRepository.submitDeveloperReview(
                    rejectedIds = emptyList(), approvedIds = ids
                )
                _state.value = if (success) RecordSubmittedState(ids[0]) else FailureState
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    fun processNextRecord() {
        _records.value?.let {
            // If there are pending records, present the next record
            Timber.e("Position: $position, Records Size: ${it.size}")
            if (position < it.size - 1 && position > -1) {
                position += 1
                _record.value = it[position]
            } else _state.value = RecordsFinishedState

            when {
                (position == SUBMIT_TRIGGER) -> submitSavedReviews()
                else -> {
                    if (it.isNotEmpty() && _record.value == it.last())
                        submitSavedReviews()
                }
            }
        }
    }

    /**
     * Saved [review] as [RecordJudgment] into the local storage
     */
    fun submitReview(review: Boolean) {
        viewModelScope.launch {
            _record.value?.record?.let {
                val isSniffing = it.isSniffingRecord ?: false
                val isCorrect = (it.needsRejection?.xor(review) ?: true)
                if (isSniffing && isCorrect.not()) _state.value = SniffingIncorrectWarningState
                val recordReview = RecordReview(
                    recordId = it.id,
                    review = review,
                    isSniffing = isSniffing,
                    isSniffingCorrect = isSniffing && isCorrect
                )
                reviewRepository.saveReview(recordReview)
            }
        }
    }

    private fun submitSavedReviews() {
        viewModelScope.launch {
            try {
                isSubmittingRecords = true
                val submitAnswers = reviewRepository.submitSavedReviews()
                isSubmittingRecords = false
                if (submitAnswers.isAccountLocked) {
                    val incorrectSniffingIds = submitAnswers.incorrectSniffingIds ?: emptyList()
                    val records = _records.value?.map { it.record } ?: mutableListOf()
                    incorrectSniffingRecords.addAll(records.filter { record ->
                        incorrectSniffingIds.contains(record.id)
                    })
                    _state.value = UserAccountLockedState
                } else {
                    removedSubmittedRecords(
                        submitAnswers.annotationIds ?: emptyList(),
                        submitAnswers.recordIds ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                isSubmittingRecords = false
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    /**
     * If annotations or judgments are submitted then we will remove those records from [_records]
     */
    private suspend fun removedSubmittedRecords(annotationIds: List<Int>, recordIds: List<Int>) {
        withContext(Dispatchers.Main) {
            val currentRecordCount = _records.value?.size ?: 0
            _records.value?.removeAll {
                val actualRecord = it.record
                recordIds.contains(actualRecord.id) || annotationIds.contains(actualRecord.currentAnnotation?.id)
            }
            val removedRecords = currentRecordCount - (_records.value?.size ?: 0)
            position -= removedRecords
            setupUndoRecordState()
        }
    }

    fun submitRecords() {
        viewModelScope.launch(exceptionHandler) {
            try {
                _state.value = RecordSubmissionProgressState
                val approvedRecords = getRecords(TabPositions.APPROVED)
                val rejectedRecords = getRecords(TabPositions.REJECTED)

                val approvedIds = approvedRecords.map { it.id }
                val rejectedIds = rejectedRecords.map { it.id }

                val sniffingIds = getSniffingRecordIds()
                val sniffingCorrectApproved = approvedRecords.getCorrectSniffingSubmissions(false)
                val sniffingCorrectRejected = rejectedRecords.getCorrectSniffingSubmissions(true)
                val sniffingCorrectIds = sniffingCorrectApproved + sniffingCorrectRejected
                val sniffingIncorrectIds = sniffingIds - sniffingCorrectIds.toSet()

                val filteredApprovedIds = getFilteredIds(approvedIds, sniffingIds)
                val filteredRejectedIds = getFilteredIds(rejectedIds, sniffingIds)

                val response = reviewRepository.submitReview(
                    rejectedIds = filteredRejectedIds,
                    approvedIds = filteredApprovedIds,
                    sniffingCorrectIds = sniffingCorrectIds,
                    sniffingIncorrectIds = sniffingIncorrectIds
                )
                val records = _records.value?.map { it.record } ?: mutableListOf()
                incorrectSniffingRecords.addAll(records.filter { record ->
                    sniffingIncorrectIds.contains(record.id)
                })

                _state.value = if (response.success == true) {
                    if (response.userAccountLocked == true) UserAccountLockedState
                    else {
                        _records.value?.clear()
                        RecordsFinishedState
                    }
                } else FailureState
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            } catch (e: IOException) {
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    fun submitIncorrectSniffingRecords() {
        submitSavedReviews()
    }

    private fun getFilteredIds(ids: List<Int>, sniffingIds: List<Int>) =
        ids.filter { sniffingIds.contains(it).not() }

    private fun List<Record>.getCorrectSniffingSubmissions(isRejected: Boolean) =
        filter { it.isSniffingRecord == true && isRejected == it.needsRejection }.map { it.id }

    private fun getSniffingRecordIds() =
        _records.value?.filter { it.record.isSniffingRecord == true }
            ?.map { it.record.id } ?: emptyList()

    fun currentRole(): String? {
        return reviewRepository.currentRole()
    }

    fun reviewedRecordsCount() = _records.value?.count { it.isRejected || it.isApproved } ?: 0

    fun saveContrastValue(value: Int) {
        reviewRepository.saveContrast(value)
        _contrastValue.value = value
    }

    fun getRecordItems(position: Int = currentTabPosition): List<RecordItem> {
        return when (position) {
            TabPositions.APPROVED -> {
                _records.value?.filter { it.isApproved && it.isRejected.not() } ?: emptyList()
            }

            TabPositions.REJECTED -> {
                _records.value?.filter { it.isApproved.not() && it.isRejected } ?: emptyList()
            }

            else -> {
                _records.value?.filter { it.isApproved.not() && it.isRejected.not() } ?: emptyList()
            }
        }
    }

    fun updateRecords(recordItems: ArrayList<RecordItem>) {
        recordItems.forEach { recordItem ->
            _records.value?.find { it.record.id == recordItem.record.id }?.apply {
                isRejected = recordItem.isRejected
                isApproved = recordItem.isApproved
            }
        }
    }

    fun getRecords(position: Int = currentTabPosition): List<Record> {
        return when (position) {
            TabPositions.APPROVED -> {
                _records.value?.filter { it.isApproved && it.isRejected.not() }?.map { it.record }
                    ?: emptyList()
            }

            TabPositions.REJECTED -> {
                _records.value?.filter { it.isApproved.not() && it.isRejected }?.map { it.record }
                    ?: emptyList()
            }

            else -> {
                _records.value?.filter { it.isApproved.not() && it.isRejected.not() }
                    ?.map { it.record } ?: emptyList()
            }
        }
    }

    fun getSpanCount(): Int {
        return reviewRepository.getSpanCount()
    }

    fun saveSpanCount(count: Int) {
        reviewRepository.saveSpanCount(count)
    }

    fun isSelectionEnabled(): Boolean {
        return true
    }

    fun getIncorrectSniffingRecords(): ArrayList<Record> {
        return incorrectSniffingRecords
    }

    fun undoReview() {
        reviewRepository.undoReview()
        undoRecord()
    }

    fun setupUndoRecordState() {
        _canUndo.value = position != 0
    }

    private fun undoRecord() {
        _records.value?.let {
            if (position > 0 && position <= it.size) {
                position -= 1
                _record.value = it[position]
            }
        }
    }

    object FailureState : ActivityState()
    class RecordSubmittedState(val recordId: Int) : ActivityState()
    object RecordsSuccessState : ActivityState()
    object RecordsFinishedState : ActivityState()
    object RecordSubmissionProgressState : ActivityState()
    object SniffingIncorrectWarningState : ActivityState()
    object UserAccountLockedState : ActivityState()
    class TemplatesSuccessState(
        val templates: List<Template>
    ) : ActivityState()
}

@Parcelize
data class RecordItem(
    val record: Record,
    var isApproved: Boolean = false,
    var isRejected: Boolean = false
) : Parcelable

object TabPositions {
    const val PENDING = 0
    const val APPROVED = 1
    const val REJECTED = 2
}