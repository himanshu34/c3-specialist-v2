package com.nayan.nayancamv2.model

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class UserLocation(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var speedMpS: Float = 0F,
    var speedKpH: Float = 0F,
    var address: String = "",
    var postalCode: String = "",
    var city: String = "",
    var state: String = "",
    var country: String = "",
    var countryCode: String = "",
    var validLocation: Boolean = false,
    var altitude: Double = 0.0,
    var time: Long = 0L
) : Parcelable

@Keep
@Parcelize
data class UserLocationMeta(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: String = "",
    val heading: String = "",
    val locationTimeStamp: Long = 0L,
    var timeStamp: Long = 0L
) : Parcelable