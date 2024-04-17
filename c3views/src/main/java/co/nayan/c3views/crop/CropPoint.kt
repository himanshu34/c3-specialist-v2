package co.nayan.c3views.crop

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot

class CropPoint(var rawX: Float, var rawY: Float) {
    var x = rawX
    var y = rawY

    fun transform(displayRect: RectF): CropPoint {
        x = rawX * displayRect.width() + displayRect.left
        y = rawY * displayRect.height() + displayRect.top
        return this
    }

    fun transformWrtBitmap(bitmapWidth: Float, bitmapHeight: Float) {
        x = rawX * bitmapWidth
        y = rawY * bitmapHeight
    }

    fun transformWrtBitmap(bitmap: Bitmap): CropPoint {
        rawX = x / bitmap.width
        rawY = y / bitmap.height
        return this
    }

    fun absolute(displayRect: RectF): Pair<Float, Float> {
        transform(displayRect)
        return Pair(this.x, this.y)
    }

    fun copy(): CropPoint {
        return CropPoint(rawX, rawY)
    }

    fun clear() {
        rawX = 0F
        rawY = 0F
    }

    fun minus(point: CropPoint) {
        rawX -= point.rawX
        rawY -= point.rawY
    }

    fun isInside(displayRect: RectF, moveX: Float, moveY: Float): Boolean {
        val point = CropPoint(rawX + moveX, rawY + moveY).transform(displayRect)
        return displayRect.contains(point.x, point.y)
    }

    companion object {
        fun minus(first: CropPoint, second: CropPoint): CropPoint {
            return CropPoint(
                first.rawX - second.rawX,
                first.rawY - second.rawY
            )
        }

        fun add(first: CropPoint, second: CropPoint): CropPoint {
            return CropPoint(
                first.rawX + second.rawX,
                first.rawY + second.rawY
            )
        }

        fun diagonalDistance(first: CropPoint, second: CropPoint, displayRect: RectF): Float {
            first.transform(displayRect)
            second.transform(displayRect)
            return hypot(abs(second.y - first.y), abs(second.x - first.x))
        }

        fun distance(first: CropPoint, second: CropPoint, displayRect: RectF): Pair<Float, Float> {
            first.transform(displayRect)
            second.transform(displayRect)
            return Pair(abs(second.y - first.y), abs(second.x - first.x))
        }
    }
}