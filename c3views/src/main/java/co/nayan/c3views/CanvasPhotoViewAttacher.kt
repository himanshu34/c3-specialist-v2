package co.nayan.c3views

import android.annotation.SuppressLint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import com.github.chrisbanes.photoview.PhotoViewAttacher

class CanvasPhotoViewAttacher(
    imageView: ImageView,
    private val listener: DrawListener
) : PhotoViewAttacher(imageView) {

    private var multiTouch: Boolean = false
    private lateinit var initialTouch: Pair<Float, Float>
    private var isInEditMode = false
    private var touchEnabled = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View?, event: MotionEvent): Boolean {
        if (touchEnabled) {
            when {
                isInEditMode -> {
                    when (event.pointerCount) {
                        1 -> {
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    super.onTouch(view, event)
                                    initialTouch = Pair(event.x, event.y)
                                    captureInitialPoint(event)
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (multiTouch) {
                                        super.onTouch(view, event)
                                    } else {
                                        captureShiftEvent(event)
                                    }
                                }
                                MotionEvent.ACTION_UP -> {
                                    captureReleasePoint(event)
                                }
                            }
                        }
                        2 -> {
                            multiTouch = true
                            listener.unselect()
                            super.onTouch(view, event)
                        }
                    }
                }
                else -> {
                    super.onTouch(view, event)
                }
            }
            return true
        }
        return false
    }

    private fun captureShiftEvent(ev: MotionEvent) {
        val displayRect = displayRect
        if (displayRect != null) {
            val x = ev.x
            val y = ev.y
            if (!multiTouch && displayRect.contains(x, y)) {
                val rawX = (x - displayRect.left) / displayRect.width()
                val rawY = (y - displayRect.top) / displayRect.height()
                listener.movePoint(rawX, rawY, x, y, displayRect, scale)
            }
        }
    }

    private fun captureInitialPoint(ev: MotionEvent) {
        val displayRect = displayRect
        if (displayRect != null) {
            val x = ev.x
            val y = ev.y
            if (!multiTouch && displayRect.contains(x, y)) {
                val rawX = (x - displayRect.left) / displayRect.width()
                val rawY = (y - displayRect.top) / displayRect.height()
                listener.touchDownPoint(rawX, rawY, x, y)
                initialTouch = Pair(rawX, rawY)
            }
        }
    }

    private fun captureReleasePoint(ev: MotionEvent) {
        val displayRect = displayRect
        if (displayRect != null) {
            val x = ev.x
            val y = ev.y
            if (multiTouch) {
                multiTouch = false
            } else {
                val rawX = (x - displayRect.left) / displayRect.width()
                val rawY = (y - displayRect.top) / displayRect.height()
                initialTouch = Pair(rawX, rawY)
                listener.releasePoint(rawX, rawY, x, y, displayRect, scale)
            }
            listener.hideZoomView()
        }
    }

    fun setEditMode(toSet: Boolean) {
        isInEditMode = toSet
    }

    fun setTouchEnabled(toSet: Boolean) {
        touchEnabled = toSet
    }
}

interface DrawListener {
    fun touchDownPoint(rawX: Float, rawY: Float, x: Float, y: Float)
    fun releasePoint(rawX: Float, rawY: Float, x: Float, y: Float, displayRect: RectF, scale: Float)
    fun movePoint(rawX: Float, rawY: Float, x: Float, y: Float, displayRect: RectF, scale: Float)
    fun unselect()
    fun hideZoomView()
}

