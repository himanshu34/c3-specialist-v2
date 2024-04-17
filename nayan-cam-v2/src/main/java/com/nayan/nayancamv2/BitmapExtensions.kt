package com.nayan.nayancamv2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Context.loadNotificationImage(image: String, dimension: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        return@withContext try {
            Glide.with(this@loadNotificationImage).asBitmap().load(image).circleCrop()
                .submit(dimension, dimension).get()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

suspend fun Context.loadAmountImage(image: Int, dimension: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        return@withContext try {
            Glide.with(this@loadAmountImage).asBitmap().load(image).circleCrop()
                .submit(dimension, dimension).get()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

fun String.textToBitmap(
    bitmapWidth: Int,
    bitmapHeight: Int,
    size: Float,
    textColor: Int = Color.WHITE,
    backgroundColor: Int = Color.BLACK
): Bitmap {
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw the circular background
    val centerX = bitmapWidth / 2f
    val centerY = bitmapHeight / 2f
    val radius =
        (bitmapWidth.coerceAtMost(bitmapHeight) / 2f).coerceAtLeast(0f) // Ensure radius is non-negative
    val circlePaint = Paint().apply {
        color = backgroundColor
        isAntiAlias = true
    }
    canvas.drawCircle(centerX, centerY, radius, circlePaint)


    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = size
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textHeightOffset = (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(this, centerX, centerY - textHeightOffset, textPaint)

    return bitmap
}

fun <T> MutableList<T>.repeat(times: Int): MutableList<T> {
    val result = mutableListOf<T>()
    repeat(times) {
        result.addAll(this)
    }
    return result
}

fun getActualBitmapList(imageBitmap: Bitmap?, textBitmap: Bitmap?): MutableList<Bitmap> {
    return when {
        (imageBitmap != null && textBitmap != null) -> {
            mutableListOf(imageBitmap, textBitmap).repeat(3)
        }

        (imageBitmap != null) -> mutableListOf(imageBitmap).repeat(3)
        (textBitmap != null) -> mutableListOf(textBitmap).repeat(3)
        else -> mutableListOf()
    }
}