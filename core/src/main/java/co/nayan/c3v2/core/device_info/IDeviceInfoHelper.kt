package co.nayan.c3v2.core.device_info

import co.nayan.c3v2.core.models.c3_module.DeviceConfig

interface IDeviceInfoHelper {

    fun saveDeviceConfig(buildVersion: String, ram: String)
    fun getDeviceConfig(): DeviceConfig?
}