package co.nayan.c3specialist_v2.search

import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.di.preference.PreferenceHelper
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NayanSearchViewModel @Inject constructor(
    private val sharedStorage: SharedStorage,
    private val mPreferenceHelper: PreferenceHelper
) : BaseViewModel() {

    fun getLocalStorageForWebView(): String? {
        val userJson = JSONObject()
        sharedStorage.getUserProfileInfo()?.let {
            userJson.put("id", it.id)
            userJson.put("email", it.email)
            userJson.put("name", it.name)
            userJson.put("tokenType", "Bearer")
            mPreferenceHelper.getAuthenticationHeaders()?.let { auth ->
                userJson.put("token", auth.access_token)
                userJson.put("client", auth.client)
                userJson.put("exp", auth.expiry)
                userJson.put("uid", auth.uid)
            }
            return userJson.toString()
        } ?: run { return null }
    }

    override fun throwException(e: Exception) {
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }
}