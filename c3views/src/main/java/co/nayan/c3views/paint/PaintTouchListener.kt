package co.nayan.c3views.paint

interface PaintTouchListener {
    fun touchDownPoint(rawX: Float, rawY: Float, x: Float, y: Float)
    fun releasePoint(rawX: Float, rawY: Float, x: Float, y: Float)
    fun movePoint(rawX: Float, rawY: Float, x: Float, y: Float)
}