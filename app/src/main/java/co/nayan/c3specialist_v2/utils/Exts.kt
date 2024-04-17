package co.nayan.c3specialist_v2.utils

import com.github.marlonlom.utilities.timeago.TimeAgo
import com.github.marlonlom.utilities.timeago.TimeAgoMessages
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.source
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

fun File.getMD5(): String {
    return this.source().buffer().use { source ->
        HashingSink.md5(blackholeSink()).use { sink ->
            source.readAll(sink)
            sink.hash.hex()
        }
    }
}

fun ByteArray.fileURLToMD5(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(this)
    return bytes.toHex()
}

fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}

fun Date.timeString(): String {
    val timeDifference = System.currentTimeMillis() - this.time
    val maxDiff = 7 * 24 * 60 * 60 * 1000

    return if (timeDifference > maxDiff) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        dateFormat.format(this)
    } else {
        val localByLanguageTag = Locale.forLanguageTag("en")
        val messages = TimeAgoMessages.Builder().withLocale(localByLanguageTag).build()
        TimeAgo.using(this.time, messages)
    }
}