package co.nayan.c3views.paint

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import com.github.chrisbanes.photoview.PhotoViewAttacher

class PaintViewAttacher(
    imageView: ImageView,
    listener: PaintDrawListener,
    touchListener: PaintTouchListener
) : PhotoViewAttacher(imageView) {

    private var multiTouch = false
    private var canTouch = true

    private var isEditModeEnabled: Boolean = false
    private var isSelectModeEnabled: Boolean = false

    private val onPencilDrawListener = listener
    private val onTouchListener = touchListener

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, ev: MotionEvent): Boolean {
        if (isEditModeEnabled) {
            when (ev.pointerCount) {
                1 -> {
                    sendEventToTouchListener(ev)
                    when (ev.action) {
                        MotionEvent.ACTION_UP -> {
                            if (multiTouch) {
                                onPencilDrawListener.discardStroke()
                                onPencilDrawListener.hideZoomView()
                                multiTouch = false
                            } else {
                                onPencilDrawListener.closeStroke()
                            }
                        }

                        MotionEvent.ACTION_DOWN -> {
                            if (!multiTouch && isSelectModeEnabled) {
                                sendEventToListener(ev)
                            } else {
                                super.onTouch(v, ev)
                            }
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (!multiTouch) {
                                sendEventToListener(ev)
                            } else {
                                super.onTouch(v, ev)
                            }
                        }
                    }
                }

                2 -> {
                    multiTouch = true
                    onPencilDrawListener.discardStroke()
                    super.onTouch(v, ev)
                }
            }
        } else {
            if (canTouch) {
                super.onTouch(v, ev)
            } else {
                return false
            }
        }
        return true
    }

    private fun sendEventToListener(ev: MotionEvent) {
        val displayRect = displayRect
        if (displayRect != null) {
            val x = ev.x
            val y = ev.y
            if (displayRect.contains(x, y)) {
                val xResult = (x - displayRect.left) / displayRect.width()
                val yResult = (y - displayRect.top) / displayRect.height()
                onPencilDrawListener.touchPoints(xResult, yResult, ev.action == MotionEvent.ACTION_MOVE)
            }
        }
    }

    fun setEditModeEnabled(isEnabled: Boolean) {
        isEditModeEnabled = isEnabled
    }

    fun setSelectModeEnabled(isEnabled: Boolean) {
        isSelectModeEnabled = isEnabled
    }

    fun isTouchable(canTouch: Boolean) {
        this.canTouch = canTouch
    }

    private fun sendEventToTouchListener(ev: MotionEvent) {
        val displayRect = displayRect
        if (displayRect != null) {
            val x = ev.x
            val y = ev.y
            if (!multiTouch) {
                val rawX = (x - displayRect.left) / displayRect.width()
                val rawY = (y - displayRect.top) / displayRect.height()

                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        onTouchListener.touchDownPoint(rawX, rawY, x, y)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        onTouchListener.movePoint(rawX, rawY, x, y)
                    }

                    MotionEvent.ACTION_UP -> {
                        onTouchListener.releasePoint(rawX, rawY, x, y)
                    }
                }
            }
        }
    }
}
