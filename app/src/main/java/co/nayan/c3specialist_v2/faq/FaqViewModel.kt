package co.nayan.c3specialist_v2.faq

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.FaqDataCategories
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.DisplayDataItem
import co.nayan.c3v2.core.models.c3_module.FaqData
import co.nayan.c3v2.core.models.c3_module.FaqDataConfirmationResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FaqViewModel @Inject constructor(
    private val trainingRepository: FaqRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    var correctDataSize: Int? = null
    var incorrectDataSize: Int? = null
    var junkDataSize: Int? = null
    var workAssignment: WorkAssignment? = null

    fun fetchTrainingData() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = InitialState
            val data = trainingRepository.fetchTrainingData(workAssignment?.wfStep?.id).faqs
            if (!data.isNullOrEmpty()) {
                val displayDataList = getDisplayData(data)
                _state.value = FaqDataSuccessState(displayDataList)
            } else {
                _state.value = FaqDataUnSuccessState
            }
        }
    }

    private fun getDisplayData(toFilter: List<FaqData>): List<DisplayDataItem> {
        val correctData = mutableListOf<FaqData>()
        val incorrectData = mutableListOf<FaqData>()
        val junkData = mutableListOf<FaqData>()

        toFilter.forEach {
            when (it.category) {
                FaqDataCategories.CORRECT -> {
                    correctData.add(it)
                }

                FaqDataCategories.INCORRECT -> {
                    incorrectData.add(it)
                }

                else -> {
                    junkData.add(it)
                }
            }
        }
        correctDataSize = correctData.size
        incorrectDataSize = incorrectData.size
        junkDataSize = junkData.size

        val maxSizeFromAllFaqDataSets =
            getMaxSize(correctData.size, incorrectData.size, junkData.size)

        val displayDataItems = mutableListOf<DisplayDataItem>()
        for (index in 0 until maxSizeFromAllFaqDataSets) {
            if (correctData.size > 0) {
                if (correctData.size > index) {
                    displayDataItems.add(
                        DisplayDataItem(
                            correctData[index], true, FaqDataCategories.CORRECT
                        )
                    )
                } else {
                    displayDataItems.add(
                        DisplayDataItem(
                            null, false, FaqDataCategories.CORRECT
                        )
                    )
                }
            }
            if (incorrectData.size > 0) {
                if (incorrectData.size > index) {
                    displayDataItems.add(
                        DisplayDataItem(
                            incorrectData[index], true, FaqDataCategories.INCORRECT
                        )
                    )
                } else {
                    displayDataItems.add(
                        DisplayDataItem(
                            null, false, FaqDataCategories.INCORRECT
                        )
                    )
                }
            }
            if (junkData.size > 0) {
                if (junkData.size > index) {
                    displayDataItems.add(
                        DisplayDataItem(
                            junkData[index], true, FaqDataCategories.JUNK
                        )
                    )
                } else {
                    displayDataItems.add(
                        DisplayDataItem(
                            null, false, FaqDataCategories.JUNK
                        )
                    )
                }
            }
        }

        return displayDataItems
    }

    private fun getMaxSize(size1: Int, size2: Int, size3: Int): Int {
        var maxSizeFromAllDataSets: Int = if (size1 > size2) {
            size1
        } else {
            size2
        }
        if (maxSizeFromAllDataSets < size3) {
            maxSizeFromAllDataSets = size3
        }
        return maxSizeFromAllDataSets
    }

    fun submitConfirmation() {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val data = trainingRepository.submitConfirmation(workAssignment?.wfStep?.id)
            _state.value = SubmitConfirmationSuccessState(data)

        }
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    data class SubmitConfirmationSuccessState(
        val response: FaqDataConfirmationResponse
    ) : ActivityState()
}

data class FaqDataSuccessState(
    val displayDataItem: List<DisplayDataItem>
) : ActivityState()

object FaqDataUnSuccessState : ActivityState()