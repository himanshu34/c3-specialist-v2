package com.nayan.nayancamv2.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

fun Float.between(min: Int, max: Int): Boolean {
    return this > min && this < max
}

fun String.textAsBitmap(): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = 12F
    paint.color = Color.BLACK
    paint.textAlign = Paint.Align.CENTER

    val baseline: Float = -paint.ascent() // ascent() is negative
    var width = (paint.measureText(this) + 10.0f) // round
    var height = (baseline + paint.descent() + 0.0f)

    if (width > height) height = width else width = height
    val image = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawColor(Color.WHITE)
    canvas.drawText(this, width / 2, height / 2, paint)
    return image
}

fun YuvImage.yuvImageToBitmap(width: Int, height: Int): Bitmap {
    val out = ByteArrayOutputStream()
    this.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    val options = BitmapFactory.Options().apply { inMutable = true }
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
}

fun ByteArray.createYuvImage(width: Int, height: Int): YuvImage? {
    if (this.size < width * height) {
        return null
    }

    val length = width * height
    val u = ByteArray(width * height / 4)
    val v = ByteArray(width * height / 4)
    for (i in u.indices) {
        u[i] = this[length + i]
        v[i] = this[length + u.size + i]
    }
    for (i in u.indices) {
        this[length + 2 * i] = v[i]
        this[length + (2 * i) + 1] = u[i]
    }

    return YuvImage(this, ImageFormat.NV21, width, height, null)
}