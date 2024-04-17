package co.nayan.canvas.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import java.io.File

fun Int?.getFormattedTimeStamp(): String {
    val timeInMillis = (this ?: 0) * 30
    val sec = timeInMillis / 1000
    val ms = (timeInMillis % 1000)
    var msString = ms.toString()
    if (msString.length == 1) {
        msString += "00"
    } else if (msString.length == 2) {
        msString += "0"
    }
    return String.format("$sec:$msString")
}

fun String.isVideo(): Boolean {
    val extension = MimeTypeMap.getFileExtensionFromUrl(this)
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    return mimeType != null && mimeType.startsWith("video")
}

fun getBitmapFromDirectory(totalFrames: Int, currentFrame: Int, framesDir: File?): Bitmap? {
    val frameCount = if (currentFrame > totalFrames) totalFrames else currentFrame
    val file = File(framesDir, "out$frameCount.jpg")
    return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
}