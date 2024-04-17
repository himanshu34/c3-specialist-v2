package com.nayan.nayancamv2.helper

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.storage.SharedPrefManager
import timber.log.Timber

/**
 * Utility class to get the camera related informations
 *
 * @property preferenceProvider
 * @property context
 */

class CameraUtils(
    private val preferenceProvider: SharedPrefManager,
    private val context: Context
) {

    internal fun getCameraParameters(cameraCharacteristics: CameraCharacteristics): String {
        val cameraParams = StringBuilder()
        CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_50HZ
        cameraCharacteristics.keys.forEach {
//            cameraParams.append("${it.name}=${cameraCharacteristics.get(it).toString()};")
            Timber.d("Camera Params : ${it.name}")
        }
        /*if (!preferenceProvider.getPreference().getBoolean(CAMERA_PARAM_UPLOADED, false)) {
            initiateCameraParameterUploader(cameraParams.toString())
        }*/
        return cameraParams.toString()
    }

    internal fun getExposureCompensationRange(cameraCharacteristics: CameraCharacteristics): Pair<Int, Int> {
        val values = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        var minExposure = 0
        val maxExposure = 0
        values?.let { minExposure = it.lower }
        val pair = Pair(minExposure, maxExposure)
        Firebase.crashlytics.log("Camera exposure range -> $minExposure, $maxExposure")
        return pair
    }

    internal fun getLowestExposureCompensation(cameraCharacteristics: CameraCharacteristics): Pair<Int, Int> {
        val values = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val minExposure = 0
        var maxExposure = 0
        values?.let { maxExposure = it.lower }
        return Pair(minExposure, maxExposure)
    }

    companion object {
        const val CAMERA_PARAM_UPLOADED = "CAMERA_PARAM_UPLOADED"
    }
}