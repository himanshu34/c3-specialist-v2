package co.nayan.c3specialist_v2.phoneverification.otp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.nayan.c3specialist_v2.R
import co.nayan.c3v2.core.utils.parcelable
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import timber.log.Timber

/**
 * BroadcastReceiver to wait for SMS messages. This can be registered either
 * in the AndroidManifest or at runtime.  Should filter Intents on
 * SmsRetriever.SMS_RETRIEVED_ACTION.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    lateinit var smsBroadcastReceiverListener: SmsBroadcastReceiverListener

    override fun onReceive(context: Context, intent: Intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
            val extras = intent.extras
            val smsRetrieverStatus = extras?.get(SmsRetriever.EXTRA_STATUS) as Status

            when (smsRetrieverStatus.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    // Get consent intent
                    extras.parcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT).also {
                        if (::smsBroadcastReceiverListener.isInitialized)
                            smsBroadcastReceiverListener.onSuccess(it)
                    }
                }
                CommonStatusCodes.TIMEOUT -> {
                    // Time out occurred, handle the error.
                    Timber.e(smsRetrieverStatus.statusMessage)
                    if (::smsBroadcastReceiverListener.isInitialized)
                        smsBroadcastReceiverListener.onFailure(context.getString(R.string.sms_retriever_timeout))
                }
            }
        }
    }
}

interface SmsBroadcastReceiverListener {
    fun onSuccess(intent: Intent?)
    fun onFailure(errorMessage: String?)
}