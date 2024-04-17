package co.nayan.c3specialist_v2.datarecords

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.utils.currentDate
import co.nayan.c3specialist_v2.utils.endTime
import co.nayan.c3specialist_v2.utils.startTime
import co.nayan.c3specialist_v2.utils.weekStartDate
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.DataRecordsFilters
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DataRecordsViewModel @Inject constructor(
    private val dataRecordsRepository: DataRecordsRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    val aasmStates = mutableListOf<String>()
    val workflows = mutableListOf<WorkFlow>()

    private var isPaginationEnabled: Boolean = true
    var filters = DataRecordsFilters(
        startTime = Calendar.getInstance().weekStartDate(),
        endTime = Calendar.getInstance().currentDate()
    )
    var applicationMode: String? = null
    var question: String? = null
    var currentPage: Int = 1

    fun fetchFirstPage() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            currentPage = 1
            val response = fetchRecords()
            val records = response.data ?: emptyList()
            setupPagination(records.size, response.totalCount)

            _state.value = if (records.isNotEmpty()) {
                SetUpRecordsState(records, isPaginationEnabled)
            } else {
                NoRecordState
            }
        }
    }

    fun fetchNextPage() {
        if (isPaginationEnabled) {
            viewModelScope.launch(exceptionHandler) {
                val response = fetchRecords()
                val records = response.data ?: emptyList()
                setupPagination(records.size, response.totalCount)
                _state.value = SetUpNextPageRecordsState(records, isPaginationEnabled)
            }
        }
    }

    private suspend fun fetchRecords() = dataRecordsRepository.fetchRecords(
        wfStepId = filters.wfStep.first,
        page = currentPage,
        perPage = RECORDS_PER_PAGE,
        aasmState = filters.aasmState,
        startTime = filters.startTime.startTime(),
        endTime = filters.endTime.endTime()
    )

    private fun setupPagination(recordsCount: Int, totalCount: Int?) {
        val totalFetchedRecords = ((currentPage - 1) * RECORDS_PER_PAGE) + recordsCount
        isPaginationEnabled = totalCount ?: 0 > totalFetchedRecords

        if (isPaginationEnabled) {
            currentPage += 1
        }
    }

    fun getSavedSpanCount(): Int {
        return dataRecordsRepository.getSpanCount()
    }

    fun saveSpanCount(spanValue: Int) {
        dataRecordsRepository.saveSpanCount(spanValue)
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    fun fetchAasmStates() {
        viewModelScope.launch(exceptionHandler) {
            aasmStates.clear()
            aasmStates.addAll(dataRecordsRepository.fetchAasmStatesFormLocalStorage())
            aasmStates.clear()
            aasmStates.addAll(dataRecordsRepository.fetchAasmStatesFromServer())
            _state.value = AasmStatesUpdateState
        }
    }

    fun fetchWorkflows() {
        viewModelScope.launch(exceptionHandler) {
            workflows.clear()
            workflows.addAll(dataRecordsRepository.fetchWorkFlowsFormLocalStorage())
            val updatedSteps = dataRecordsRepository.fetchWorkFlowsFromServer()
            workflows.clear()
            workflows.addAll(updatedSteps)
            _state.value = WorkFlowsUpdateState
        }
    }

    fun fetchRecord(recordId: Int) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val record = dataRecordsRepository.fetchDataRecord(recordId)
            _state.value = FetchDataRecordSuccessState(record)
        }
    }

    object AasmStatesUpdateState : ActivityState()
    object WorkFlowsUpdateState : ActivityState()
    object NoRecordState : ActivityState()
    data class SetUpRecordsState(val records: List<Record>, val isPaginationEnabled: Boolean) :
        ActivityState()

    data class SetUpNextPageRecordsState(
        val records: List<Record>, val isPaginationEnabled: Boolean
    ) : ActivityState()

    data class FetchDataRecordSuccessState(val record: Record?) : ActivityState()

    companion object {
        const val RECORDS_PER_PAGE = 25
    }
}