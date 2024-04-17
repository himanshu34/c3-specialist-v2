package co.nayan.canvas.views.videoplayer

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import kotlin.math.sqrt

class CustomZoomableTextureView(
    context: Context
) : ZoomableTextureView(context) {

    private var isPinched: Boolean = false
    private lateinit var initialPoint : Pair<Float,Float>
    private var zoomableViewInteractor: ZoomableViewInteractor?  = null

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                initialPoint = Pair(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {

                if (event.pointerCount > 1 || isPointerMoved(initialPoint, Pair(event.x, event.y))) {
                    isPinched = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (event.pointerCount == 1 && !isPinched) {
                    zoomableViewInteractor?.onTapped()
                }
                isPinched = false
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun isPointerMoved(
        initialPoint: Pair<Float, Float>,
        nextPoint: Pair<Float, Float>
    ): Boolean {
      return sqrt(
            ((nextPoint.first - initialPoint.first)*(nextPoint.first - initialPoint.first)
                    + (nextPoint.second - initialPoint.second)*(nextPoint.second - initialPoint.second)).toDouble()
        ) > 100
    }

    fun getPlaybackScreenshot(): Bitmap? {
        return getBitmap(1920, 1080)
    }

    fun setZoomableViewInteractor(zoomableViewInteractor: ZoomableViewInteractor) {
        this.zoomableViewInteractor = zoomableViewInteractor
    }
}