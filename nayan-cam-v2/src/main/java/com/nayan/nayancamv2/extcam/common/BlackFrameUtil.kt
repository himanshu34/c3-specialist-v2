package com.nayan.nayancamv2.extcam.common

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import timber.log.Timber
import java.nio.ByteBuffer

object BlackFrameUtil {

    private val _isBlackFrame = MutableLiveData<Boolean>()
    val isBlackFrame: LiveData<Boolean> = _isBlackFrame
    fun processCameraStream(buffer: ByteBuffer, width: Int, height: Int) {
        try {
            // Process the H.264 frames
            val isBlackFrame = isBlackFrameYUV420(buffer, width, height)
            updateUI(isBlackFrame)
        } catch (e: Exception) {
            Timber.tag("BlackFrameUtil").e(e, "Error processing camera stream")
        }
    }

    private fun isBlackFrameYUV420(data: ByteBuffer, width: Int, height: Int): Boolean {
        // Calculate the size of the Y component (luminance)
        val ySize = width * height
        // Check the first few bytes of the Y component to determine if the frame is black
        var totalLuminance = 0
        for (i in 0 until minOf(ySize, 10)) {
            val luminance = data[i].toInt() and 0xFF  // Convert to unsigned int
            totalLuminance += luminance
        }

        // Calculate average luminance
        val averageLuminance = totalLuminance / minOf(ySize, 10)
        // Define a threshold value for black level (adjust as needed)
        val blackThreshold = 16
        // Check if the average luminance is below the threshold
        return averageLuminance <= blackThreshold
    }

    fun updateUI(isBlackFrame: Boolean) {
        // This function runs on the main thread and can be used to update the UI
        if (isBlackFrame.not()) {
            _isBlackFrame.postValue(false)
        }
    }

    fun reset() {
        _isBlackFrame.postValue(true)
    }
}