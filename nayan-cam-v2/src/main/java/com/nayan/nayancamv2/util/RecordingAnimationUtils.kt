package com.nayan.nayancamv2.util

import android.view.View
import android.view.animation.Animation
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import co.nayan.c3v2.core.utils.gone
import co.nayan.nayancamv2.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun View.showRecordingAlert(
    animation: Animation,
    resource: Int, recordingState: Int, volume: Int
) = withContext(Dispatchers.Main) {
    CommonUtils.playSound(resource, context, volume)
    when (recordingState) {
        RecordingEventState.RECORDING_STARTED -> {
            updateRecordingEvent(animation, R.drawable.bg_recording_alert)
        }

        RecordingEventState.RECORDING_SUCCESSFUL -> {
            updateRecordingEvent(animation, R.drawable.bg_recording_success) {
                CoroutineScope(Dispatchers.Main).launch {
                    updateRecordingEvent(
                        animation,
                        R.drawable.bg_ai_scanning
                    )
                }
            }
        }

        RecordingEventState.RECORDING_CORRUPTED, RecordingEventState.RECORDING_FAILED -> {
            updateRecordingEvent(animation, R.drawable.bg_recording_fail) {
                CoroutineScope(Dispatchers.Main).launch {
                    updateRecordingEvent(animation, R.drawable.bg_ai_scanning)
                }
            }
        }
    }
}

suspend fun View.updateRecordingEvent(
    animation: Animation,
    backgroundResId: Int?,
    onAnimationEnd: () -> Unit = {}
) = withContext(Dispatchers.Main) {
    clearAnimation()
    backgroundResId?.let {
        background = ResourcesCompat.getDrawable(resources, backgroundResId, null)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                onAnimationEnd()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
        startAnimation(animation)
    } ?: run { background = null }
}

suspend fun TextView.onSurveyorWarningStatus(
    ifUserLocationFallsWithInSurge: Boolean,
    isValidSpeed: Boolean,
    volumeLevel: Int,
    callback: (isVisible: Boolean) -> Unit
) = withContext(Dispatchers.Main) {

    if (ifUserLocationFallsWithInSurge) {
        if (isValidSpeed) gone()
        else {
            val warningTextBuilder = StringBuilder()
            warningTextBuilder.append(context.getString(R.string.warning_driving_fast))
            warningTextBuilder.append("\n\n")
            warningTextBuilder.append(context.getString(R.string.driving_fast_text))
            val warningTxt = warningTextBuilder.toString()

            text = warningTxt
            callback.invoke(true)

            CommonUtils.playSound(
                R.raw.speed_alert,
                context,
                volumeLevel
            )
        }
    } else {
        text = context.getString(R.string.not_in_surge_text)
        callback.invoke(true)
    }
}
