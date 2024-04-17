package co.nayan.c3specialist_v2.phoneverification.otp

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class OtpManagerImpl @Inject constructor(
    private val activity: FragmentActivity
) : IOtpManager {

    private val AUTO_RETRIEVAL_TIMEOUT_SECONDS = 0L
    private lateinit var mAuth: FirebaseAuth
    lateinit var mResendToken: PhoneAuthProvider.ForceResendingToken
    var mVerificationId: String = ""

    private val events: MutableLiveData<ActivityState> by lazy { MutableLiveData<ActivityState>() }

    override suspend fun initFirebase() {
        mAuth = FirebaseAuth.getInstance().also {
            it.setLanguageCode(Locale.getDefault().language)
//            it.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
        }
    }

    override fun sendOtp(mobile: String, resend: Boolean) {
        if (resend) resend(mobile)
        else send(mobile)
    }

    override fun verifyOtp(otp: String) {
        val credential = PhoneAuthProvider.getCredential(mVerificationId, otp)
        signInWithPhoneAuthCredential(credential)
    }

    override fun subscribe(): MutableLiveData<ActivityState> = events

    private fun send(mobile: String) {
        val options = PhoneAuthOptions.newBuilder(mAuth)
            .setPhoneNumber(mobile)
            .setTimeout(AUTO_RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(mCallbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun resend(mobile: String) {
        val options = PhoneAuthOptions.newBuilder(mAuth)
            .setPhoneNumber(mobile)
            .setTimeout(AUTO_RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(mCallbacks)
            .setForceResendingToken(mResendToken)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        mAuth.signInWithCredential(credential).addOnCompleteListener(activity) { task ->
            events.value =
                if (task.isSuccessful) EventValidationSuccessState else EventValidationFailedState
        }
    }

    private val mCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(authCredential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(authCredential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            events.value = EventSendFailedState(e)
        }

        override fun onCodeSent(
            verificationId: String,
            forceResendingToken: PhoneAuthProvider.ForceResendingToken
        ) {
            super.onCodeSent(verificationId, forceResendingToken)
            mResendToken = forceResendingToken
            mVerificationId = verificationId
            events.value = EventSendSuccessState
        }
    }
}