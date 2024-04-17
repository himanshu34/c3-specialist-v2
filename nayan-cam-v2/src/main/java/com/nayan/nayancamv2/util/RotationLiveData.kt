package com.nayan.nayancamv2.util

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
import androidx.lifecycle.LiveData
import co.nayan.c3v2.core.models.OrientationState
import timber.log.Timber

/**
 * Calculates closest 90-degree orientation to compensate for the device
 * rotation relative to sensor orientation, i.e., allows user to see camera
 * frames with the expected orientation.
 */
class RotationLiveData(context: Context) : LiveData<OrientationState>() {

    private val listener = object : OrientationEventListener(context.applicationContext) {
        override fun onOrientationChanged(orientation: Int) {
            // val rightLandscape = 90
            // val leftLandscape = 270
            val rotation = when {
                orientation <= 45 -> Surface.ROTATION_0
                orientation <= 135 -> Surface.ROTATION_90
                orientation <= 225 -> Surface.ROTATION_180
                orientation <= 315 -> Surface.ROTATION_270
                else -> Surface.ROTATION_0
            }

            postValue(OrientationState(rotation, orientation))
        }
    }

    public override fun onActive() {
        super.onActive()
        Timber.d("Adding Orientation Listener")
        listener.enable()
    }

    public override fun onInactive() {
        super.onInactive()
        Timber.d("Removing orientation listener")
        listener.disable()
    }
}
