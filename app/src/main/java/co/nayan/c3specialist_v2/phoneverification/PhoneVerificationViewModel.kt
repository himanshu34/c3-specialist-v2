package co.nayan.c3specialist_v2.phoneverification

import android.content.Context
import android.os.CountDownTimer
import android.text.format.DateUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.phoneverification.utils.isValidOTP
import co.nayan.c3specialist_v2.phoneverification.utils.isValidReferralCode
import co.nayan.c3specialist_v2.referral.IGooglePlayInstallManager
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorMessageState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PhoneVerificationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val phoneVerificationRepository: PhoneVerificationRepository,
    private val userRepository: UserRepository,
    val googlePlayInstallManager: IGooglePlayInstallManager
) : BaseViewModel() {

    var requestHint: (() -> Unit)? = null
    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    var mobileNumber: String = ""
    private var idToken: String? = null

    fun getUserInfo() = userRepository.getUserInfo()
    fun setInitialState() = viewModelScope.launch { _state.value = InitialState }

    fun validateUserLogin(isd: String, phoneNumber: String) {
        _state.value = when {
            (isd.isEmpty() || isd.length <= 2) -> {
                ErrorMessageState(errorMessage = context.getString(R.string.invalid_isd))
            }

            (phoneNumber.isEmpty() || phoneNumber.length <= 9) -> {
                ErrorMessageState(errorMessage = context.getString(R.string.invalid_phone_number))
            }

            else -> {
                mobileNumber = "$isd$phoneNumber"
                updatePhoneNumber(mobileNumber)
                ProgressState
            }
        }
    }

    private fun updatePhoneNumber(
        phoneNumber: String?
    ) = viewModelScope.launch(exceptionHandler) {
        val response = phoneVerificationRepository.updatePhoneNumber(phoneNumber)
        _state.value = if (response != null) {
            idToken = response.user?.phoneIdToken
            startOtpCountDown()
            PhoneVerificationSuccessState(idToken)
        } else ErrorMessageState(errorMessage = context.getString(co.nayan.c3v2.core.R.string.something_went_wrong))
    }

    fun resendOtp() = updatePhoneNumber(mobileNumber)

    fun validateOtp(otp: String) = viewModelScope.launch(exceptionHandler) {
        _state.value = if (otp.isValidOTP()) {
            authenticateOtp(otp)
            ProgressState
        } else ErrorMessageState(errorMessage = context.getString(R.string.invalid_otp))
    }

    private fun authenticateOtp(otp: String?) = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = phoneVerificationRepository.validateOTP(otp, idToken)
        val isVerified = response?.isPhoneVerified ?: false
        if (response != null && isVerified) {
            userRepository.setUserInfo(response)
        }
        _state.value = OTPVerificationState(isVerified)
    }

    /**
     *  Pair<String, Boolean> :
     *      Pair.First : is used for the updating count timer on to the view
     *      Pair.Second : is used if count down is finished
     */
    private val _countDownTimer: MutableLiveData<Pair<String, Boolean>> = MutableLiveData()
    val countDownTimer: LiveData<Pair<String, Boolean>> get() = _countDownTimer

    private lateinit var timer: CountDownTimer
    private fun startOtpCountDown() = viewModelScope.launch(exceptionHandler) {
        if (::timer.isInitialized) timer.cancel()
        timer = object : CountDownTimer(1000 * 61, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _countDownTimer.value =
                    Pair(DateUtils.formatElapsedTime(millisUntilFinished / 1000), false)
            }

            override fun onFinish() {
                _countDownTimer.value = Pair("00:00", true)
            }
        }
        timer.start()
    }

    fun fetchReferralCode() = viewModelScope.launch {
        val installReferralCode = googlePlayInstallManager.getReferralCode()
        Timber.tag("Referral Code").d(installReferralCode)
        Firebase.crashlytics.log("Referral Code : $installReferralCode")
        if (installReferralCode?.isNotEmpty() == true
            && installReferralCode.contains("google").not()
        ) validateReferralCode(installReferralCode)
    }

    fun validateReferralCode(referralCode: String) = viewModelScope.launch(exceptionHandler) {
        _state.value = when {
            referralCode.isEmpty() -> {
                ErrorMessageState(errorMessage = context.getString(R.string.invalid_referral_code))
            }

            referralCode.isValidReferralCode().not() -> {
                ErrorMessageState(errorMessage = context.getString(R.string.invalid_referral_code))
            }

            else -> {
                updateReferralCode(referralCode)
                InitialState
            }
        }
    }

    private fun updateReferralCode(referralCode: String) = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = phoneVerificationRepository.updateReferralCode(referralCode)
        _state.value = if (response != null) {
            response.user?.let {
                userRepository.setUserInfo(it)
                ReferralSuccessState
            } ?: run { ErrorMessageState(errorMessage = response.message) }
        } else ErrorMessageState(errorMessage = context.getString(co.nayan.c3v2.core.R.string.something_went_wrong))
    }

    fun isOnBoardingDone(): Boolean {
        return userRepository.isOnBoardingDone()
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    object ReferralSuccessState : ActivityState()
    data class OTPVerificationState(val success: Boolean) : ActivityState()
    data class PhoneVerificationSuccessState(val phoneIdToken: String?) : ActivityState()
}