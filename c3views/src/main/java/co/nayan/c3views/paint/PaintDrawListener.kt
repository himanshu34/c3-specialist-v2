package co.nayan.c3views.paint

interface PaintDrawListener {
    fun touchPoints(x: Float, y: Float, isDragging: Boolean)
    fun closeStroke()
    fun discardStroke()
    fun hideZoomView()
}
