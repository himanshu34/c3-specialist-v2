package com.nayan.nayancamv2.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import org.opencv.android.Utils
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import timber.log.Timber

// Convert bitmap from JPEG to ARGB8888 format
private fun jpegToARGB8888(input: Bitmap): Bitmap {
    val size = input.width * input.height
    val pixels = IntArray(size)
    input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
    val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
    output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
    return output // ARGB_8888 formatted bitmap
}

@Synchronized
fun jpegToBitmap(image: Image?): Bitmap? {
    return image?.let {
        try {
            val buffer = it.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer[bytes]
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    } ?: run { null }
}

// Get image Mat from Bitmap
fun bitmapToMat(bitmap: Bitmap): Mat {
    val bitmapARGB8888 = jpegToARGB8888(bitmap)
    val imageMat = Mat()
    Utils.bitmapToMat(bitmapARGB8888, imageMat)
    return imageMat
}

// Convert camera Image data to OpenCV image Mat(rix)
fun imageToMat(image: Image?): Mat? {
    // check image
    if (image == null) return null
    // store image to bytes array
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.capacity())
    buffer[bytes]
    // get bitmap from bytes and convert it to Mat
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return bitmapToMat(bitmap)
}

// Inverse conversion after image processing to show it on device screen
fun matToBitmap(image: Mat): Bitmap? {
    var bitmap: Bitmap? = null
    val convertedMat = Mat(image.height(), image.width(), CvType.CV_8U, Scalar(4.0))
    try {
        Imgproc.cvtColor(image, convertedMat, Imgproc.COLOR_GRAY2RGBA, 4)
        bitmap = Bitmap.createBitmap(
            convertedMat.cols(),
            convertedMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(convertedMat, bitmap)
    } catch (e: CvException) {
        Timber.e(e)
    }
    return bitmap
}