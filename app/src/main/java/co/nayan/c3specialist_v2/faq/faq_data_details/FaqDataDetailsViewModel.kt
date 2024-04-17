package co.nayan.c3specialist_v2.faq.faq_data_details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.faq.FaqDataUnSuccessState
import co.nayan.c3specialist_v2.faq.FaqRepository
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.FaqData
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FaqDataDetailsViewModel @Inject constructor(
    private val faqRepository: FaqRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    fun fetchTrainingData(wfStepId: Int) {
        viewModelScope.launch(exceptionHandler) {
            _state.value = ProgressState
            val data = faqRepository.fetchTrainingData(wfStepId).faqs
            _state.value = if (!data.isNullOrEmpty()) {
                sortDataByCategories(data)
                FaqDataSuccessState(data)
            } else FaqDataUnSuccessState
        }
    }

    private fun sortDataByCategories(toFilter: List<FaqData>) {
        toFilter.sortedWith { data1, data2 ->
            data2.category.compareTo(data1.category)
        }
    }

    data class FaqDataSuccessState(val faqDataList: List<FaqData>) : ActivityState()

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }
}
