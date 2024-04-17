package co.nayan.c3specialist_v2.phoneverification.otp

import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState

interface IOtpManager {
    suspend fun initFirebase()
    fun sendOtp(mobile: String, resend: Boolean = false)
    fun verifyOtp(otp: String)
    fun subscribe(): MutableLiveData<ActivityState>
}