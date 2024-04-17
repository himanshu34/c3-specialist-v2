package com.nayan.nayancamv2.env

import android.graphics.Point
import android.util.Size
import android.view.Display
import timber.log.Timber
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Collections

/** Standard High Definition size for pictures and video */
val SIZE_1080P: Size = Size(1920, 1080)

/** Returns a [Size] object for the given [Display] */
fun getDisplaySmartSize(display: Display): Size {
    val outPoint = Point()
    display.getRealSize(outPoint)
    return Size(outPoint.x, outPoint.y)
}

/**
 * In this sample, we choose a video size with 16x9 aspect ratio. Also, we don't use sizes
 * larger than 1920p, since MediaRecorder cannot handle such a high-resolution video.
 *
 * @param choices The list of available sizes
 * @return The video size
 */
fun chooseVideoSize(choices: Array<Size>) = choices.firstOrNull {
    it.height == it.width * 9 / 16 && it.width <= SIZE_1080P.width
} ?: choices[0]

/**
 * Given `choices` of `Size`s supported by a camera, choose the smallest one that
 * is at least as large as the respective texture view size, and that is at most as large as
 * the respective max size, and whose aspect ratio matches with the specified value. If such
 * size doesn't exist, choose the largest one that is at most as large as the respective max
 * size, and whose aspect ratio matches with the specified value.
 *
 * @param choices           The list of sizes that the camera supports for the intended
 *                          output class
 * @param maxPreviewSize    The size of the texture view if available else current screen size
 * @param maxAllowedSize    The maximum allowed size for a video (HD)
 * @param aspectRatio       The aspect ratio
 * @return The optimal `Size`, or an arbitrary one if none were big enough
 */
fun chooseOptimalSize(
    choices: Array<Size>,
    maxPreviewSize: Size,
    maxAllowedSize: Size,
    aspectRatio: Size
): Size {
    // Collect the supported resolutions that are at least as big as the preview Surface
    val bigEnough = ArrayList<Size>()
    // Collect the supported resolutions that are smaller than the preview Surface
    val notBigEnough = ArrayList<Size>()
    val df = DecimalFormat("#.##").apply { roundingMode = RoundingMode.DOWN }
    val aspectRatioFactor =
        df.format(aspectRatio.height.toFloat() / aspectRatio.width.toFloat()).toFloat()
    choices.filter { option ->
        val optionFactor = df.format(option.height.toFloat() / option.width.toFloat()).toFloat()
        option.width <= maxAllowedSize.width && option.height <= maxAllowedSize.height && optionFactor == aspectRatioFactor
    }.map { option ->
        if (option.width >= maxPreviewSize.width && option.height >= maxPreviewSize.height)
            bigEnough.add(option)
        else notBigEnough.add(option)
    }

    // Pick the smallest of those big enough. If there is no one big enough, pick the
    // largest of those not big enough.
    return if (bigEnough.size > 0) {
        Collections.min(bigEnough, CompareSizesByArea())
    } else if (notBigEnough.size > 0) {
        Collections.max(notBigEnough, CompareSizesByArea())
    } else {
        Timber.e("Couldn't find any suitable preview size")
        choices.firstOrNull { option ->
            option.width <= maxAllowedSize.width && option.height <= maxAllowedSize.height
        } ?: choices[0]
    }
}