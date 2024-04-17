package com.nayan.nayancamv2.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.source
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

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

inline fun <reified T> Context.isServiceRunning(): Boolean {
    val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Integer.MAX_VALUE)
        .any { it.service.className == T::class.java.name }
}

inline fun <reified T> Context.isActivityRunning(): Boolean {
    val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningTasks(Integer.MAX_VALUE)
        .any { it.topActivity?.className == T::class.java.name }
}

fun String.fromBase64(): String {
    return String(
        android.util.Base64.decode(this, android.util.Base64.DEFAULT),
        StandardCharsets.UTF_8
    )
}

fun String.toBase64(): String {
    return String(
        android.util.Base64.encode(this.toByteArray(), android.util.Base64.DEFAULT),
        StandardCharsets.UTF_8
    )
}

