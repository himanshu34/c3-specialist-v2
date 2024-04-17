package co.nayan.c3specialist_v2.applanguage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.AppLanguage
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.storage.SharedStorage
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
class AppLanguageViewModel @Inject constructor(
    private val appLanguageRepository: AppLanguageRepository,
    private val sharedStorage: SharedStorage
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    fun updateLanguage(language: String) = viewModelScope.launch(exceptionHandler) {
        val preferenceStoredLanguage = getAppLanguage() ?: AppLanguage.ENGLISH
        val isSameLanguage = preferenceStoredLanguage.equals(language, ignoreCase = true)
        _state.value = if (isSameLanguage) LanguageUpdateDuplicateState
        else {
            _state.value = ProgressState
            appLanguageRepository.updateLanguage(language)
            LanguageUpdateSuccessState
        }
        sharedStorage.setAppLanguage(language)
    }

    fun getAppLanguage() = sharedStorage.getAppLanguage()

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    object LanguageUpdateDuplicateState : ActivityState()
    object LanguageUpdateSuccessState : ActivityState()
}