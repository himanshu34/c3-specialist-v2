package co.nayan.c3v2.core.di.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import co.nayan.c3v2.core.fromPrettyJson
import co.nayan.c3v2.core.models.HeaderAuthProvider
import co.nayan.c3v2.core.models.c3_module.DeviceConfig
import co.nayan.c3v2.core.toPrettyJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceHelper @Inject constructor(@ApplicationContext context: Context) {

    companion object {
        const val SHARED_PREF_NAME = "C3CorePrefs"
        const val KEY_USER_AUTH_DATA = "_user_auth_data"
        const val KEY_DEVICE_CONFIG_DATA = "_device_config_data"
    }

    private val sharedPref: SharedPreferences =
        context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)

    fun saveAuthenticationHeaders(headerAuthProvider: HeaderAuthProvider) {
        val valueString = headerAuthProvider.toPrettyJson()
        sharedPref.edit(commit = true) { putString(KEY_USER_AUTH_DATA, valueString) }
    }

    fun getAuthenticationHeaders(): HeaderAuthProvider? {
        return sharedPref.getString(KEY_USER_AUTH_DATA, null)?.fromPrettyJson() ?: run { null }
    }

    fun saveDeviceConfig(deviceConfig: DeviceConfig) {
        sharedPref.edit { putString(KEY_DEVICE_CONFIG_DATA, deviceConfig.toPrettyJson()) }
    }

    fun getDeviceConfig(): DeviceConfig? {
        return sharedPref.getString(KEY_DEVICE_CONFIG_DATA, null)?.fromPrettyJson() ?: run { null }
    }

    fun clearPreferences() {
        sharedPref.edit { clear() }
    }
}