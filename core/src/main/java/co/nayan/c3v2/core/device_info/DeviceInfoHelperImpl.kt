package co.nayan.c3v2.core.device_info

import android.os.Build.BOARD
import android.os.Build.BOOTLOADER
import android.os.Build.HARDWARE
import android.os.Build.ID
import android.os.Build.MANUFACTURER
import android.os.Build.MODEL
import android.os.Build.TIME
import android.os.Build.USER
import android.os.Build.VERSION
import android.os.Build.VERSION.RELEASE
import android.util.Log
import co.nayan.c3v2.core.di.preference.PreferenceHelper
import co.nayan.c3v2.core.models.c3_module.DeviceConfig
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoHelperImpl @Inject constructor(
    val mPreferenceHelper: PreferenceHelper
) : IDeviceInfoHelper {

    private val model = deviceModel
    private val hardware: String? = HARDWARE
    private val board: String? = BOARD
    private val bootloader: String? = BOOTLOADER
    private val user: String? = USER
    val version: String = "$RELEASE (API ${VERSION.SDK_INT})"
    private val id: String? = ID
    private val time = TIME

    private val deviceModel
        get() = capitalize(
            if (MODEL.lowercase().startsWith(MANUFACTURER.lowercase()))
                MODEL else "$MANUFACTURER $MODEL"
        )

    private fun capitalize(str: String) = str.apply {
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun logDetails(deviceConfig: DeviceConfig) {
        Log.e("DeviceInfoHelperImpl", "model: ${deviceConfig.model}")
        Log.e("DeviceInfoHelperImpl", "hardware: ${deviceConfig.hardware}")
        Log.e("DeviceInfoHelperImpl", "board: ${deviceConfig.board}")
        Log.e("DeviceInfoHelperImpl", "bootloader: ${deviceConfig.bootloader}")
        Log.e("DeviceInfoHelperImpl", "version: ${deviceConfig.version}")
    }

    override fun saveDeviceConfig(buildVersion: String, ram: String) {
        val savedDeviceConfig = getDeviceConfig()
        val updatedDeviceConfig = savedDeviceConfig?.let {
            it.buildVersion = buildVersion
            it
        } ?: run {
            DeviceConfig(
                id,
                model,
                hardware,
                board,
                bootloader,
                version,
                buildVersion,
                ram,
                user,
                time
            )
        }

        mPreferenceHelper.saveDeviceConfig(updatedDeviceConfig)
        logDetails(updatedDeviceConfig)
    }

    override fun getDeviceConfig(): DeviceConfig? {
        return mPreferenceHelper.getDeviceConfig()
    }
}