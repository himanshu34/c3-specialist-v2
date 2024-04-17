package co.nayan.c3v2.login

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.nayan.c3v2.core.generateUniqueIdentifier
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorMessageState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.login_module.SignUpResponse
import co.nayan.c3v2.core.utils.isEmailValid
import com.facebook.CallbackManager
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loginRepository: LoginRepository
) : ViewModel() {

    // Facebook callback manager
    val callbackManager: CallbackManager = CallbackManager.Factory.create()
    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    fun saveDeviceConfig(buildVersion: String, ram: String) = viewModelScope.launch {
        loginRepository.saveDeviceConfig(buildVersion, ram)
    }

    fun getDeviceModel() = loginRepository.getDeviceConfig()?.model ?: ""
    fun getBuildVersion() = loginRepository.getDeviceConfig()?.buildVersion

    fun validateSocialLogin(
        name: String,
        email: String,
        photoUrl: String?,
        token: String,
        type: String
    ) = viewModelScope.launch(exceptionHandler) {
        val value = when {
            name.isEmpty() -> ErrorMessageState(errorMessage = context.getString(R.string.name_cant_be_blank))
            email.isEmpty() -> ErrorMessageState(errorMessage = context.getString(R.string.email_cant_be_blank))
            (!email.isEmailValid()) -> ErrorMessageState(errorMessage = context.getString(R.string.invalid_email))
            token.isEmpty() -> ErrorMessageState(errorMessage = context.getString(R.string.invalid_token))
            else -> {
                authenticateSocial(name, email, photoUrl, token, type)
                ProgressState
            }
        }
        _state.postValue(value)
    }

    private fun authenticateSocial(
        name: String?,
        email: String?,
        photoUrl: String?,
        token: String?,
        type: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        loginRepository.socialLogin(name, email, photoUrl, token, type)
            .onStart {
                _state.postValue(ProgressState)
            }.catch {
                _state.postValue(ErrorState(it as Exception))
            }.collect {
                _state.postValue(SignUpSuccessState(it))
            }
    }

    fun validateUserLogin(
        email: String, password: String
    ) = viewModelScope.launch(exceptionHandler) {
        val value = when {
            email.isEmpty() -> ErrorMessageState(errorMessage = context.getString(R.string.username_or_email_cant_be_blank))
            (!email.isEmailValid()) -> ErrorMessageState(errorMessage = context.getString(R.string.invalid_email))
            password.isEmpty() -> ErrorMessageState(errorMessage = context.getString(R.string.password_cant_be_blank))
            else -> {
                authenticateUserLogin(email, password)
                ProgressState
            }
        }
        _state.postValue(value)
    }

    private fun authenticateUserLogin(
        email: String,
        password: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        loginRepository.login(email, password)
            .onStart {
                _state.postValue(ProgressState)
            }.catch {
                _state.postValue(ErrorState(it as Exception))
            }.collect {
                _state.postValue(LoginSuccessState(it.data))
            }
    }

    fun validateUserSignUp(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ) = viewModelScope.launch(exceptionHandler) {
        val value = when {
            name.isEmpty() -> ErrorMessageState(errorMessage = context.getString(R.string.name_cant_be_blank))
            email.isEmpty() -> ErrorMessageState(errorMessage = context.getString(R.string.email_cant_be_blank))
            (!email.isEmailValid()) -> ErrorMessageState(errorMessage = context.getString(R.string.invalid_email))
            password.isEmpty() -> ErrorMessageState(errorMessage = context.getString(R.string.password_cant_be_blank))
            (!password.contentEquals(confirmPassword)) -> {
                ErrorMessageState(errorMessage = context.getString(R.string.password_match))
            }

            else -> {
                authenticateUserSignUp(name, email, password)
                ProgressState
            }
        }
        _state.postValue(value)
    }

    private fun authenticateUserSignUp(
        name: String,
        email: String,
        password: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        loginRepository.signUp(name, email, password)
            .onStart {
                _state.postValue(ProgressState)
            }.catch {
                _state.postValue(ErrorState(it as Exception))
            }.collect {
                _state.postValue(SignUpSuccessState(it))
            }
    }

    fun forgotPassword(email: String) = viewModelScope.launch(exceptionHandler) {
        if (email.isNotEmpty() && email.isEmailValid()) sendForgotPassword(email)
        else _state.postValue(ErrorMessageState(errorMessage = context.getString(R.string.invalid_email)))
    }

    private fun sendForgotPassword(
        email: String
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        loginRepository.forgotPassword(email)
            .onStart {
                _state.postValue(ProgressState)
            }.catch {
                _state.postValue(ErrorState(it as Exception))
            }.collect {
                val value = if (it.success) ForgotPasswordSuccessState
                else ErrorMessageState(errorMessage = it.message)
                _state.postValue(value)
            }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        run {
            Firebase.crashlytics.log("Login coroutineExceptionHandler")
            Firebase.crashlytics.recordException(t)
            _state.postValue(ErrorState(t as Exception))
        }
    }

    fun authenticateKentUser() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        var success = false
        while (!success) {
            delay(2000)
            val uniqueIdentifier = context.generateUniqueIdentifier()
            val emailFormation = "$uniqueIdentifier@nayantech.com"
            try {
                loginRepository.carCamLogin(uniqueIdentifier, emailFormation)
                    .onStart {
                        _state.postValue(ProgressState)
                    }.catch {
                        _state.postValue(ErrorState(it as Exception))
                    }.collect {
                        _state.postValue(LoginSuccessState(it.data))
                        success = true // Set the flag to exit the loop on successful authentication
                    }
            } catch (e: Exception) {
                _state.postValue(ErrorState(e))
            }
        }
    }

    data class SignUpSuccessState(val signUpResponse: SignUpResponse) : ActivityState()
    data class LoginSuccessState(val user: User) : ActivityState()
    object ForgotPasswordSuccessState : ActivityState()
}