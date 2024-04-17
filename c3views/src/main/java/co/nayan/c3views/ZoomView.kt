package co.nayan.c3views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import timber.log.Timber

class ZoomView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val paint = Paint()
    private val textPaint = Paint()
    private var isZooming = false
    private var bitmapWidth = 0f
    private var bitmapHeight = 0f
    private val zoomMatrix = Matrix()
    private val zoomPoint = PointF()
    private var scaleValue = 3f

    init {
        textPaint.color = ContextCompat.getColor(context, R.color.selectedEndPointOverlay)
        textPaint.strokeWidth = 15f
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 65f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isZooming) {
            try {
                zoomMatrix.reset()
                zoomPoint.set(transformWrtBitmap(zoomPoint, scaleValue))
                zoomMatrix.postScale(scaleValue, scaleValue)
                zoomMatrix.postTranslate(
                    width / 2f - zoomPoint.x,
                    height / 2f - zoomPoint.y
                )
                paint.shader.setLocalMatrix(zoomMatrix)

                canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)
                canvas.drawText("+", width / 2f - 14, height / 2f + 16, textPaint)
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        }
    }

    fun setZooming(isZooming: Boolean, zoomPoint: PointF, scaleFactor: Float) {
        this.isZooming = isZooming
        this.zoomPoint.set(zoomPoint)
        scaleValue = scaleFactor * 4f
    }

    fun setImageAssets(bitmap: Bitmap) {
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        this.bitmapWidth = bitmap.width.toFloat()
        this.bitmapHeight = bitmap.height.toFloat()
    }

    private fun transformWrtBitmap(zoomPoint: PointF, scaleValue: Float): PointF {
        zoomPoint.x = zoomPoint.x * bitmapWidth * scaleValue
        zoomPoint.y = zoomPoint.y * bitmapHeight * scaleValue
        return zoomPoint
    }
}