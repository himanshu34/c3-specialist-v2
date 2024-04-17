package co.nayan.c3specialist_v2.incorrectrecords

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
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectAnnotation
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectJudgment
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectReview
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class IncorrectRecordsViewModel @Inject
constructor(private val incorrectRecordsRepository: IncorrectRecordsRepository) : BaseViewModel() {

    var startDate: String? = null
    var endDate: String? = null
    var workType: String? = null
    var userRole: String? = null
    var userId: Int? = null
    var isForMember: Boolean = false
    var wfStepId: Int? = null
    private var isPaginationEnabled = true
    private var currentPage = 1

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val incorrectAnnotations = arrayListOf<IncorrectAnnotation>()
    private val incorrectJudgments = arrayListOf<IncorrectJudgment>()
    private val incorrectReviews = arrayListOf<IncorrectReview>()

    fun fetchIncorrectRecords() {
        when (workType) {
            WorkType.ANNOTATION -> fetchIncorrectAnnotations()
            WorkType.VALIDATION -> fetchIncorrectJudgments()
            WorkType.REVIEW -> fetchIncorrectReviews()
        }
    }

    fun fetchNextPage() {
        when (workType) {
            WorkType.ANNOTATION -> fetchIncorrectAnnotationsNextPage()
            WorkType.VALIDATION -> fetchIncorrectJudgmentsNextPage()
            WorkType.REVIEW -> fetchIncorrectReviewsNextPage()
        }
    }

    private fun fetchIncorrectAnnotations() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            incorrectAnnotations.clear()
            val response = if (isForMember) {
                fetchMemberIncorrectAnnotations()
            } else {
                if (userRole == Role.SPECIALIST) fetchSpecialistIncorrectAnnotations()
                else fetchManagerIncorrectAnnotations()
            }
            val annotations = response?.incorrectAnnotations ?: emptyList()
            val totalFetchedRecords = ((currentPage - 1) * RECORDS_PER_PAGE) + annotations.size
            isPaginationEnabled = response?.totalCount ?: 0 > totalFetchedRecords
            if (isPaginationEnabled) currentPage = (response?.page ?: 0) + 1
            _state.value = if (annotations.isNotEmpty()) {
                incorrectAnnotations.addAll(annotations)
                SetUpIncorrectRecordsState(
                    annotations.mapNotNull { it.dataRecord }, isPaginationEnabled
                )
            } else NoRecordState
        }
    }

    private fun fetchIncorrectAnnotationsNextPage() {
        if(isPaginationEnabled.not()) return
        viewModelScope.launch(exceptionHandler) {
            val response = if (isForMember) fetchMemberIncorrectAnnotations()
            else {
                if (userRole == Role.SPECIALIST) fetchSpecialistIncorrectAnnotations()
                else fetchManagerIncorrectAnnotations()
            }
            val annotations = response?.incorrectAnnotations ?: emptyList()
            annotations.forEach {
                if (incorrectAnnotations.contains(it).not()) incorrectAnnotations.add(it)
            }
            val totalFetchedRecords = ((currentPage - 1) * RECORDS_PER_PAGE) + annotations.size
            isPaginationEnabled = response?.totalCount ?: 0 > totalFetchedRecords
            if (isPaginationEnabled) currentPage = (response?.page ?: 0) + 1
            _state.value = SetUpNextPageRecordsState(
                annotations.mapNotNull { it.dataRecord }, isPaginationEnabled
            )
        }
    }

    private fun fetchIncorrectJudgments() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            incorrectJudgments.clear()
            val response = if (isForMember) fetchMemberIncorrectJudgments()
            else {
                if (userRole == Role.SPECIALIST) fetchSpecialistIncorrectJudgments()
                else fetchManagerIncorrectJudgments()
            }
            val judgments = response?.incorrectJudgments ?: emptyList()
            val totalFetchedRecords = ((currentPage - 1) * RECORDS_PER_PAGE) + judgments.size

            isPaginationEnabled = response?.totalCount ?: 0 > totalFetchedRecords
            if (isPaginationEnabled) currentPage = (response?.page ?: 0) + 1

            _state.value = if (judgments.isNotEmpty()) {
                incorrectJudgments.addAll(judgments)
                SetUpIncorrectRecordsState(
                    judgments.mapNotNull { it.dataRecord }, isPaginationEnabled
                )
            } else NoRecordState
        }
    }

    private fun fetchIncorrectJudgmentsNextPage() {
        if(isPaginationEnabled.not()) return
        viewModelScope.launch(exceptionHandler) {
            val response = if (isForMember) fetchMemberIncorrectJudgments()
            else {
                if (userRole == Role.SPECIALIST) fetchSpecialistIncorrectJudgments()
                else fetchManagerIncorrectJudgments()
            }
            val judgments = response?.incorrectJudgments ?: emptyList()
            judgments.forEach {
                if (incorrectJudgments.contains(it).not()) incorrectJudgments.add(it)
            }
            val totalFetchedRecords = ((currentPage - 1) * RECORDS_PER_PAGE) + judgments.size
            isPaginationEnabled = response?.totalCount ?: 0 > totalFetchedRecords
            if (isPaginationEnabled) currentPage = (response?.page ?: 0) + 1
            _state.value = SetUpNextPageRecordsState(
                judgments.mapNotNull { it.dataRecord }, isPaginationEnabled
            )
        }
    }

    private fun fetchIncorrectReviews() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            incorrectReviews.clear()
            val response = if (isForMember) fetchMemberIncorrectReviews()
            else fetchManagerIncorrectReviews()
            val reviews = response?.incorrectReviews ?: emptyList()
            val totalFetchedRecords = ((currentPage - 1) * RECORDS_PER_PAGE) + reviews.size
            isPaginationEnabled = response?.totalCount ?: 0 > totalFetchedRecords
            if (isPaginationEnabled) currentPage = (response?.page ?: 0) + 1

            _state.value = if (reviews.isNotEmpty()) {
                incorrectReviews.addAll(reviews)
                SetUpIncorrectRecordsState(
                    reviews.mapNotNull { it.dataRecord }, isPaginationEnabled
                )
            } else NoRecordState
        }
    }

    private fun fetchIncorrectReviewsNextPage() {
        if(isPaginationEnabled.not()) return
        viewModelScope.launch(exceptionHandler) {
            val response = if (isForMember) fetchMemberIncorrectReviews()
            else fetchManagerIncorrectReviews()
            val reviews = response?.incorrectReviews ?: emptyList()
            reviews.forEach {
                if (incorrectReviews.contains(it).not()) incorrectReviews.add(it)
            }
            val totalFetchedRecords = ((currentPage - 1) * RECORDS_PER_PAGE) + reviews.size
            isPaginationEnabled = response?.totalCount ?: 0 > totalFetchedRecords
            if (isPaginationEnabled) currentPage = (response?.page ?: 0) + 1
            _state.value = SetUpNextPageRecordsState(
                reviews.mapNotNull { it.dataRecord }, isPaginationEnabled
            )
        }
    }

    private suspend fun fetchManagerIncorrectReviews() =
        incorrectRecordsRepository.fetchManagerIncorrectReviews(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            page = currentPage,
            perPage = RECORDS_PER_PAGE,
            wfStepId = wfStepId
        )

    private suspend fun fetchManagerIncorrectJudgments() =
        incorrectRecordsRepository.fetchManagerIncorrectJudgments(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            page = currentPage,
            perPage = RECORDS_PER_PAGE,
            wfStepId = wfStepId
        )

    private suspend fun fetchSpecialistIncorrectJudgments() =
        incorrectRecordsRepository.fetchSpecialistIncorrectJudgments(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            page = currentPage,
            perPage = RECORDS_PER_PAGE,
            wfStepId = wfStepId
        )

    private suspend fun fetchManagerIncorrectAnnotations() =
        incorrectRecordsRepository.fetchManagerIncorrectAnnotations(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            page = currentPage,
            perPage = RECORDS_PER_PAGE,
            wfStepId = wfStepId
        )

    private suspend fun fetchSpecialistIncorrectAnnotations() =
        incorrectRecordsRepository.fetchSpecialistIncorrectAnnotations(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            page = currentPage,
            perPage = RECORDS_PER_PAGE,
            wfStepId = wfStepId
        )

    private suspend fun fetchMemberIncorrectJudgments() =
        incorrectRecordsRepository.fetchMemberIncorrectJudgments(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            page = currentPage,
            perPage = RECORDS_PER_PAGE,
            userRole = userRole,
            userId = userId,
            wfStepId = wfStepId
        )

    private suspend fun fetchMemberIncorrectAnnotations() =
        incorrectRecordsRepository.fetchMemberIncorrectAnnotations(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            page = currentPage,
            perPage = RECORDS_PER_PAGE,
            userRole = userRole,
            userId = userId,
            wfStepId = wfStepId
        )

    private suspend fun fetchMemberIncorrectReviews() =
        incorrectRecordsRepository.fetchMemberIncorrectReviews(
            startDate = startDate?.startTime(),
            endDate = endDate?.endTime(),
            page = currentPage,
            perPage = RECORDS_PER_PAGE,
            userRole = userRole,
            userId = userId,
            wfStepId = wfStepId
        )

    fun getIncorrectAnnotation(recordId: Int): IncorrectAnnotation? {
        return incorrectAnnotations.find { it.dataRecord?.id == recordId }
    }

    fun getIncorrectJudgment(recordId: Int): IncorrectJudgment? {
        return incorrectJudgments.find { it.dataRecord?.id == recordId }
    }

    fun getIncorrectReview(recordId: Int): IncorrectReview? {
        return incorrectReviews.find { it.dataRecord?.id == recordId }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    object NoRecordState : ActivityState()

    data class SetUpIncorrectRecordsState(
        val incorrectRecords: List<Record>, val isPaginationEnabled: Boolean
    ) : ActivityState()

    data class SetUpNextPageRecordsState(
        val incorrectRecords: List<Record>, val isPaginationEnabled: Boolean
    ) : ActivityState()

    companion object {
        const val RECORDS_PER_PAGE = 10
    }
}