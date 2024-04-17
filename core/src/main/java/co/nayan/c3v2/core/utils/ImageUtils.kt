package co.nayan.c3v2.core.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.SeekBar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlin.math.min

class ImageUtils {

    companion object {
        // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
        // are normalized to eight bits.
        private const val kMaxChannelValue = 262143
        fun getColorMatrix(progress: Int): ColorMatrixColorFilter {
            val contrast = if (progress < 50) {
                progress.toFloat() / 50F
            } else {
                (((progress - 50).toFloat() / 5F) + 1)
            }
            val brightness = 0F
            return ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0F, 0F, 0F, brightness,
                        0F, contrast, 0F, 0F, brightness,
                        0F, 0F, contrast, 0F, brightness,
                        0F, 0F, 0F, 1F, 0F
                    )
                )
            )
        }

        private fun YUV2RGB(y: Int, u: Int, v: Int): Int {
            // Adjust and check YUV values
            var y = y
            var u = u
            var v = v
            y = if (y - 16 < 0) 0 else y - 16
            u -= 128
            v -= 128

            // This is the floating point equivalent. We do the conversion in integer
            // because some Android devices do not have floating point in hardware.
            // nR = (int)(1.164 * nY + 2.018 * nU);
            // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
            // nB = (int)(1.164 * nY + 1.596 * nV);
            val y1192 = 1192 * y
            var r = y1192 + 1634 * v
            var g = y1192 - 833 * v - 400 * u
            var b = y1192 + 2066 * u

            // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
            r = if (r > kMaxChannelValue) kMaxChannelValue else if (r < 0) 0 else r
            g = if (g > kMaxChannelValue) kMaxChannelValue else if (g < 0) 0 else g
            b = if (b > kMaxChannelValue) kMaxChannelValue else if (b < 0) 0 else b
            return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
        }

        fun convertYUV420ToARGB8888(
            yData: ByteArray,
            uData: ByteArray,
            vData: ByteArray,
            width: Int,
            height: Int,
            yRowStride: Int,
            uvRowStride: Int,
            uvPixelStride: Int,
            out: IntArray
        ) {
            var yp = 0
            for (j in 0 until height) {
                val pY = yRowStride * j
                val pUV = uvRowStride * (j shr 1)
                for (i in 0 until width) {
                    val uv_offset = pUV + (i shr 1) * uvPixelStride
                    out[yp++] = YUV2RGB(
                        0xff and yData[pY + i].toInt(),
                        0xff and uData[uv_offset].toInt(),
                        0xff and vData[uv_offset].toInt()
                    )
                }
            }
        }

        fun convertYUV420SPToARGB8888(input: ByteArray, width: Int, height: Int, output: IntArray) {
            val frameSize = width * height
            var j = 0
            var yp = 0
            while (j < height) {
                var uvp = frameSize + (j shr 1) * width
                var u = 0
                var v = 0
                var i = 0
                while (i < width) {
                    val y = 0xff and input[yp].toInt()
                    if (i and 1 == 0) {
                        v = 0xff and input[uvp++].toInt()
                        u = 0xff and input[uvp++].toInt()
                    }
                    output[yp] = YUV2RGB(y, u, v)
                    i++
                    yp++
                }
                j++
            }
        }
    }
}

open class OnSeekBarChangeListener : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {}
    override fun onStartTrackingTouch(p0: SeekBar?) {}
    override fun onStopTrackingTouch(p0: SeekBar?) {}
}

fun Bitmap.mergedBitmap(answer: String?): Bitmap {
    return if (answer.isNullOrEmpty()) this
    else {
        try {
            val textBitmap = textBitmap(answer)
            val mergedBitmap =
                Bitmap.createBitmap(width, height + textBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mergedBitmap)
            canvas.drawBitmap(this, 0f, 0f, null)
            canvas.drawBitmap(textBitmap, 0f, height.toFloat(), null)
            mergedBitmap
        } catch (e: OutOfMemoryError) {
            Firebase.crashlytics.recordException(e)
            this
        }
    }
}

private fun Bitmap.textBitmap(text: String): Bitmap {
    val bitmap = Bitmap.createBitmap(width, 140, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    when (text.length) {
        in 0..11 -> textPaint.textSize = 90f
        in 11..21 -> textPaint.textSize = 65f
        else -> textPaint.textSize = 40f
    }
    canvas.drawARGB(60, 64, 180, 64)
    val xPos = bitmap.width.toFloat() / 2
    val yPos = (bitmap.height.toFloat() / 2) - ((textPaint.descent() + textPaint.ascent()) / 2)
    canvas.drawText(text, xPos, yPos, textPaint)
    return bitmap
}

private val textPaint = Paint().apply {
    color = Color.parseColor("#666666")
    style = Paint.Style.FILL
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
}

fun Bitmap.scaledBitmap(): Bitmap {
    val scaleValue = min(1080f / width, 1080f / height)
    return if (scaleValue > 1) {
        try {
            Bitmap.createScaledBitmap(
                this, (width * scaleValue).toInt(), (height * scaleValue).toInt(), true
            )
        } catch (e: OutOfMemoryError) {
            Firebase.crashlytics.recordException(e)
            this
        }
    } else {
        this
    }
}

fun Bitmap.overlayBitmap(maskBitmaps: List<Bitmap>): Bitmap {
    val targetBitmap = Bitmap.createBitmap(this)
    val canvas = Canvas(targetBitmap)
    maskBitmaps.forEach {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(it, 0f, 0f, paint)
    }
    return targetBitmap
}