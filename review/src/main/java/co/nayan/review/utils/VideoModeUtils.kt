package co.nayan.review.utils

import android.webkit.MimeTypeMap

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