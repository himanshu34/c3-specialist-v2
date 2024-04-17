package co.nayan.c3specialist_v2.profile.utils

import co.nayan.c3v2.core.models.c3_module.responses.IfscVerificationResponse
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BankInfoManager @Inject constructor() {

    private lateinit var ifscCodeVerificationListener: IfscCodeVerificationListener

    fun verifyIFSCCode(ifscCode: String) {
        val uploadUrl = "https://ifsc.razorpay.com/$ifscCode"
        val request = Request.Builder().url(uploadUrl).get().build()

        if (isCallbackInitialized()) {
            try {
                val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body

                if (response.isSuccessful) {
                    val ifscResponse = getIfscVerificationResponse(responseBody)
                    ifscCodeVerificationListener.success(ifscResponse)
                } else {
                    ifscCodeVerificationListener.failure()
                }

            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                ifscCodeVerificationListener.failure()
            }
        }
    }

    private fun getIfscVerificationResponse(body: ResponseBody?): IfscVerificationResponse {
        val type = object : TypeToken<IfscVerificationResponse>() {}.type
        return Gson().fromJson(body?.string(), type)
    }

    private fun isCallbackInitialized() =
        this@BankInfoManager::ifscCodeVerificationListener.isInitialized

    fun setIfscCodeVerificationListener(callback: IfscCodeVerificationListener) {
        ifscCodeVerificationListener = callback
    }
}

interface IfscCodeVerificationListener {
    fun success(response: IfscVerificationResponse)
    fun failure()
}