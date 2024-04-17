package com.nayan.nayancamv2.helper

import android.media.Image
import android.media.ImageReader
import android.util.Size
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import timber.log.Timber
import java.io.File

class CameraPreviewScoutListener : BaseCameraPreviewListener() {

    private val tag = CameraPreviewScoutListener::class.java.simpleName
    private var previewWidth = 0
    private var previewHeight = 0
    private var validImage: Image? = null

    override fun onImageAvailable(imageReader: ImageReader?) {
        Timber.e("**********************************[onImageAvailable]*************************")
        val image = imageReader?.acquireLatestImage() ?: run { null }
        try {
            if (previewWidth == 0 || previewHeight == 0) {
                image?.close()
                return
            }

            validImage = image
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image?.close()
        }
    }

    override fun onPreviewSizeChosen(size: Size?) {
        size?.let {
            previewWidth = it.width
            previewHeight = it.height
        }
    }

    override fun captureStillImage(file: File) {
        // Pick image from Image Available function
        Timber.tag(tag).d("Capture scout image called")
        validImage?.let { image -> ImageSaver(image, file) }
    }
}