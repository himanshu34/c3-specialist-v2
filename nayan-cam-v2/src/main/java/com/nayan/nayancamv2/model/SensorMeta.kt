package com.nayan.nayancamv2.model

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class SensorMeta(
    var accelerometer: ArrayList<Float> = ArrayList(),
    var linearAcceleration: ArrayList<Float> = ArrayList(),
    var angularVelocity: ArrayList<Float> = ArrayList(),
    var gyroHeadingDegrees: Float = 0.0f,
    var gyroscopeCalibrated: ArrayList<Float> = ArrayList(),
    var magneticField: ArrayList<Float> = ArrayList(),
    var magHeading: Float = 0.0f,
    var rotationMatrix: ArrayList<Float> = ArrayList(),
    var angleWithDirection: String = "",
    var pitch: Float = 0.0f,
    var roll: Float = 0.0f,
    var azimuth: Float = 0.0f,
    var proximity: Float = 1.0f,
    var timeStamp: Long = 0L,
    var heading: Float=0.0F,
    var currentTimeStamp:Long=0L
) : Parcelable