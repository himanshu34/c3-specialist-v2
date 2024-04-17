package co.nayan.c3views.quadrilateral

import android.graphics.Bitmap
import android.graphics.RectF
import co.nayan.c3views.crop.CropPoint
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class QuadrilateralPoint(var rawX: Float, var rawY: Float) {

    var x = rawX
    var y = rawY

    fun transform(displayRect: RectF): QuadrilateralPoint {
        x = rawX * displayRect.width() + displayRect.left
        y = rawY * displayRect.height() + displayRect.top
        return this
    }

    fun transformWrtBitmap(bitmapWidth: Float, bitmapHeight: Float) {
        x = rawX * bitmapWidth
        y = rawY * bitmapHeight
    }

    fun transformWrtBitmap(bitmap: Bitmap): QuadrilateralPoint {
        rawX = x / bitmap.width.toFloat()
        rawY = y / bitmap.height.toFloat()
        return this
    }

    fun absolute(displayRect: RectF): Pair<Float, Float> {
        transform(displayRect)
        return Pair(this.x, this.y)
    }

    fun copy(): QuadrilateralPoint {
        return QuadrilateralPoint(rawX, rawY)
    }

    fun clear() {
        rawX = 0F
        rawY = 0F
    }

    fun minus(point: QuadrilateralPoint) {
        rawX -= point.rawX
        rawY -= point.rawY
    }

    companion object {
        fun minus(first: QuadrilateralPoint, second: QuadrilateralPoint): QuadrilateralPoint {
            return QuadrilateralPoint(
                first.rawX - second.rawX,
                first.rawY - second.rawY
            )
        }

        fun add(first: QuadrilateralPoint, second: QuadrilateralPoint): QuadrilateralPoint {
            return QuadrilateralPoint(
                first.rawX + second.rawX,
                first.rawY + second.rawY
            )
        }

        fun diagonalDistance(first: QuadrilateralPoint, second: QuadrilateralPoint, displayRect: RectF): Float {
            first.transform(displayRect)
            second.transform(displayRect)
            return hypot(abs(second.y - first.y), abs(second.x - first.x))
        }

        fun distance(first: QuadrilateralPoint, second: QuadrilateralPoint, displayRect: RectF): Pair<Float, Float> {
            first.transform(displayRect)
            second.transform(displayRect)
            return Pair(abs(second.y - first.y), abs(second.x - first.x))
        }
    }
}