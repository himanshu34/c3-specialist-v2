package co.nayan.c3v2.core.models.c3_module

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class DeviceConfig(
    val id: String?,
    val model: String,
    val hardware: String?,
    val board: String?,
    val bootloader: String?,
    val version: String,
    var buildVersion: String,
    val ram: String,
    val user: String?,
    val time: Long?
) : Parcelable