package co.nayan.c3specialist_v2.referral

import android.content.Context
import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.GooglePlayClientConnectedState
import co.nayan.c3v2.core.models.GooglePlayClientDisconnectedState
import co.nayan.c3v2.core.models.InitialState
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePlayInstallManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IGooglePlayInstallManager {

    private lateinit var referrerClient: InstallReferrerClient
    private val _events: MutableLiveData<ActivityState> by lazy {
        MutableLiveData<ActivityState>(
            InitialState
        )
    }

    override fun initListener() {
        referrerClient = InstallReferrerClient.newBuilder(context).build()
        referrerClient.startConnection(mCallbacks)
    }

    override fun subscribe(): MutableLiveData<ActivityState> = _events

    override fun getReferralCode(): String? {
        if (::referrerClient.isInitialized && referrerClient.isReady) {
            val response: ReferrerDetails = referrerClient.installReferrer
            return response.installReferrer.extractUTMSource()
        }
        return null
    }

    private val mCallbacks = object : InstallReferrerStateListener {

        override fun onInstallReferrerSetupFinished(responseCode: Int) {
            when (responseCode) {
                InstallReferrerClient.InstallReferrerResponse.OK -> {
                    _events.value = GooglePlayClientConnectedState
                }

                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                    // API not available on the current Play Store app.
                }

                InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                    // Connection couldn't be established.
                }
            }
        }

        override fun onInstallReferrerServiceDisconnected() {
            // Try to restart the connection on the next request to
            // Google Play by calling the startConnection() method.
            _events.value = GooglePlayClientDisconnectedState
        }
    }

    private fun String.extractUTMSource(): String {
        return this.split("=")[1].split("&")[0]
    }
}