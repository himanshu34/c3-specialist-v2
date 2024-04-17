package com.nayan.nayancamv2.helper

import android.graphics.Bitmap
import android.media.ImageReader
import android.util.Size
import java.io.File

abstract class BaseCameraPreviewListener : ImageReader.OnImageAvailableListener {

    var onFrameAvailable: OnFrameAvailable? = null
    var onOpticalFlowMotionDetected: OnOpticalFlowMotionDetected? = null

    override fun onImageAvailable(imageReader: ImageReader?) {}

    open fun scheduleSampling() {}
    open suspend fun processOpticalFlow(bitmap: Bitmap) {}
    open suspend fun processAI(bitmap: Bitmap) {}
    open fun setIsManualRecording(isManualRecording: Boolean) {}
    open fun setScreenRotation(orientation: Int) {}
    open fun captureStillImage(file: File) {}
    abstract fun onPreviewSizeChosen(size: Size?)

    interface OnFrameAvailable {
        fun onFrameAvailable()
    }

    interface OnOpticalFlowMotionDetected {
        fun onMotionlessContentDetected()
        fun onMotionContentDetected()
    }
}