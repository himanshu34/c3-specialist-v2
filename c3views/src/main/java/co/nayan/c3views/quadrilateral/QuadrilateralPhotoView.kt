package co.nayan.c3views.quadrilateral

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3views.CanvasPhotoViewAttacher
import co.nayan.c3views.Constants.Dimension.MIN_DIAGONAL_DISTANCE
import co.nayan.c3views.Constants.Dimension.MIN_DIMENSION
import co.nayan.c3views.DrawListener
import co.nayan.c3views.R
import com.github.chrisbanes.photoview.PhotoView
import timber.log.Timber
import kotlin.math.sqrt

class QuadrilateralPhotoView(context: Context, attrs: AttributeSet?) : PhotoView(context, attrs) {

    private val quadrilateralPaints = QuadrilateralPaints()
    var selectedQuadrilateralIndex: Int = -1
    private var quadrilateralInteractionInterface: QuadrilateralInteractionInterface? = null
    val quadrilaterals = mutableListOf<Quadrilateral>()

    private var touchDown = QuadrilateralPoint(0F, 0F)
    private var quadrilateralSelected = false
    private var selectedQuadrilateral: Quadrilateral? = null


    private var isDrawingQuadrilateral = false
    private var currentTouchDown = QuadrilateralPoint(0F, 0F)
    private var zoomView: co.nayan.c3views.ZoomView? = null
    private val zoomPoint: PointF = PointF(0f, 0f)
    private var isZooming = false

    var showHint: Boolean = false
    private val hintData = mutableListOf<Quadrilateral>()

    private var bitmapWidth: Float? = null
    private var bitmapHeight: Float? = null
    private var currentQuadrilateral = Quadrilateral(touchDown, currentTouchDown)

    private var moveStartPoint: QuadrilateralPoint? = null
    private var movablePoint: Quadrilateral.MovablePoint? = null
    private val detectedEdgePoints = mutableListOf<QuadrilateralPoint>()

    private val drawListener = object : DrawListener {
        override fun touchDownPoint(rawX: Float, rawY: Float, x: Float, y: Float) {
            zoomPoint.x = rawX
            zoomPoint.y = rawY
            isZooming = true
            quadrilateralInteractionInterface?.setUpZoomView(true, x, y)
            if (quadrilateralSelected) {
                touchDown.clear()
                moveStartPoint = QuadrilateralPoint(rawX, rawY)
            } else {
                quadrilaterals.forEachIndexed { index, it ->
                    if (QuadrilateralUtils.isDragging(Pair(x, y), it)) {
                        selectedQuadrilateralIndex = index
                        selectedQuadrilateral = it
                        it.isSelected = true
                        quadrilateralSelected = true
                        quadrilateralInteractionInterface?.selected(true)
                        return
                    }
                }

                if (!quadrilateralSelected) {
                    touchDown.rawX = rawX
                    touchDown.rawY = rawY
                }
            }
            invalidate()
        }

        override fun releasePoint(
            rawX: Float,
            rawY: Float,
            x: Float,
            y: Float,
            displayRect: RectF,
            scale: Float
        ) {
            quadrilateralInteractionInterface?.setUpZoomView(false, x, y)
            val releasePoint = QuadrilateralPoint(rawX, rawY)
            if (quadrilateralSelected) {
                moveStartPoint?.let {
                    val distance = distance(it, releasePoint)
                    if (distance > 50) {
                        updateSelectedQuadrilateral()
                    }
                }
            } else {
                if (validDimensions(
                        releasePoint,
                        displayRect
                    ) && touchDown.rawX != 0F && touchDown.rawY != 0F
                ) {
                    if (quadrilaterals.isNotEmpty()) {
                        quadrilaterals.remove(quadrilaterals.last())
                    }

                    val rect = Quadrilateral(touchDown.copy(), releasePoint)
                    quadrilaterals.add(getFinalRect(rect))
                    touchDown.clear()
                    quadrilateralInteractionInterface?.onQuadrilateralDrawn()
                }
            }
            isZooming = false
            isDrawingQuadrilateral = false
            quadrilateralInteractionInterface?.onUpdateQuadrilaterals()
            invalidate()
        }

        override fun movePoint(
            rawX: Float,
            rawY: Float,
            x: Float,
            y: Float,
            displayRect: RectF,
            scale: Float
        ) {
            zoomPoint.x = rawX
            zoomPoint.y = rawY
            isZooming = true

            if (quadrilateralSelected && validDimensions(
                    QuadrilateralPoint(rawX, rawY),
                    displayRect
                )
            ) {
                quadrilateralInteractionInterface?.setUpZoomView(true, x, y)
                movablePoint = selectedQuadrilateral?.resizeRect(rawX, rawY)
            } else {
                currentTouchDown.rawX = rawX
                currentTouchDown.rawY = rawY
                isDrawingQuadrilateral = true
                currentQuadrilateral = Quadrilateral(touchDown, currentTouchDown)
            }
            invalidate()
        }

        override fun unselect() {
            unSelect()
        }

        override fun hideZoomView() {
            quadrilateralInteractionInterface?.setUpZoomView(false, 0f, 0f)
        }
    }

    private fun distance(first: QuadrilateralPoint, second: QuadrilateralPoint): Float {
        first.transform(quadrilateralViewAttacher.displayRect)
        second.transform(quadrilateralViewAttacher.displayRect)
        return sqrt(
            (second.x - first.x) * (second.x - first.x)
                    + (second.y - first.y) * (second.y - first.y)
        )
    }

    private fun updateSelectedQuadrilateral() {
        selectedQuadrilateral?.apply {
            val quad = getFinalRect(this)
            when (movablePoint) {
                Quadrilateral.MovablePoint.TOUCH_DOWN -> {
                    this.touchDown = quad.touchDown
                }
                Quadrilateral.MovablePoint.RELEASE_POINT -> {
                    this.releasePoint = quad.releasePoint
                }
                Quadrilateral.MovablePoint.TOP_RIGHT -> {
                    this.topRight = quad.topRight
                }
                Quadrilateral.MovablePoint.BOTTOM_LEFT -> {
                    this.bottomLeft = quad.bottomLeft
                }
                Quadrilateral.MovablePoint.CENTER -> {
                    this.touchDown = quad.touchDown
                    this.releasePoint = quad.releasePoint
                    this.topRight = quad.topRight
                    this.bottomLeft = quad.bottomLeft
                }
                else -> {

                }
            }
        }
    }

    private fun getFinalRect(rect: Quadrilateral): Quadrilateral {
        var finalTouchDownPoint = rect.touchDown.copy()
        var finalTopRight = rect.topRight.copy()
        var finalReleasePoint = rect.releasePoint.copy()
        var finalBottomLeft = rect.bottomLeft.copy()

        var minDistanceTouchDown = Float.MAX_VALUE
        var minDistanceTopRight = Float.MAX_VALUE
        var minDistanceReleasePoint = Float.MAX_VALUE
        var minDistanceBottomLeft = Float.MAX_VALUE

        detectedEdgePoints.forEach {
            val distanceTouchDown = distance(it, rect.touchDown)
            if (minDistanceTouchDown > distanceTouchDown && distanceTouchDown < 35) {
                minDistanceTouchDown = distanceTouchDown
                finalTouchDownPoint = it.copy()
            }

            val distanceTopRight = distance(it, rect.topRight)
            if (minDistanceTopRight > distanceTopRight && distanceTopRight < 35) {
                minDistanceTopRight = distanceTopRight
                finalTopRight = it.copy()
            }

            val distanceReleasePoint = distance(it, rect.releasePoint)
            if (minDistanceReleasePoint > distanceReleasePoint && distanceReleasePoint < 35) {
                minDistanceReleasePoint = distanceReleasePoint
                finalReleasePoint = it.copy()
            }

            val distanceBottomLeft = distance(it, rect.bottomLeft)
            if (minDistanceBottomLeft > distanceBottomLeft && distanceBottomLeft < 35) {
                minDistanceBottomLeft = distanceBottomLeft
                finalBottomLeft = it.copy()
            }
        }

        return Quadrilateral(
            finalTouchDownPoint,
            finalTopRight,
            finalReleasePoint,
            finalBottomLeft,
            null
        )
    }

    init {
        val innerQuadColor = ContextCompat.getColor(context, R.color.quad_inner_overlay)
        val outerQuadColor = ContextCompat.getColor(context, R.color.quad_outer_overlay)
        val hintColor = ContextCompat.getColor(context, R.color.hint_overlay)

        quadrilateralPaints.apply {
            innerQuadPaint.apply {
                color = innerQuadColor
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            outerQuadPaint.apply {
                color = outerQuadColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            hintPaint.apply {
                color = hintColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            movablePointsPaint.apply {
                color = outerQuadColor
                movablePointsPaint.style = Paint.Style.FILL
            }
        }

        scaleType = ScaleType.FIT_CENTER
    }

    private val quadrilateralViewAttacher = CanvasPhotoViewAttacher(this, drawListener)

    private fun validDimensions(releasePoint: QuadrilateralPoint, displayRect: RectF): Boolean {
        val diagonalDistance =
            QuadrilateralPoint.diagonalDistance(touchDown, releasePoint, displayRect)
        val distance = QuadrilateralPoint.distance(touchDown, releasePoint, displayRect)
        Timber.e("#### Diagonal Distance $diagonalDistance ####")
        Timber.e("#### Quadrilateral Height ${distance.first} ####")
        Timber.e("#### Quadrilateral Width ${distance.second} ####")
        return diagonalDistance > MIN_DIAGONAL_DISTANCE
                && distance.first >= MIN_DIMENSION && distance.second >= MIN_DIMENSION
                && touchDown.y < releasePoint.y && touchDown.x < releasePoint.x
                && validPoints(releasePoint, displayRect)
    }

    private fun validPoints(releasePoint: QuadrilateralPoint, displayRect: RectF): Boolean {
        return releasePoint.x > displayRect.left &&
                releasePoint.x < displayRect.right &&
                releasePoint.y > displayRect.top &&
                releasePoint.y < displayRect.bottom
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        zoomView?.let {
            it.setZooming(isZooming, zoomPoint, quadrilateralViewAttacher.scale)
            it.invalidate()
        }

        quadrilateralPaints.setStrokeWidth(strokeWidth())

        quadrilaterals.forEach { quadrilateralPings ->
            quadrilateralViewAttacher.displayRect?.let { displayRect ->
                quadrilateralPings.draw(
                    canvas,
                    displayRect,
                    quadrilateralPaints
                )
            }
        }

        if (isDrawingQuadrilateral && displayRect != null) {
            touchDown.transform(displayRect)
            currentTouchDown.transform(displayRect)

            if (currentTouchDown.y > touchDown.y
                && validPoints(currentTouchDown, quadrilateralViewAttacher.displayRect)
                && touchDown.x < currentTouchDown.x
            ) {
                quadrilateralViewAttacher.displayRect?.let { displayRect ->
                    currentQuadrilateral.draw(
                        canvas,
                        displayRect,
                        quadrilateralPaints
                    )
                }
            }
        }

        if (showHint) {
            hintData.forEach { quadrilateral ->
                quadrilateralViewAttacher.displayRect?.let { displayRect ->
                    quadrilateral.draw(
                        canvas,
                        displayRect,
                        quadrilateralPaints
                    )
                }
            }
        }
    }

    fun setBitmapAttributes(bitmapWidth: Float, bitmapHeight: Float) {
        this.bitmapWidth = bitmapWidth
        this.bitmapHeight = bitmapHeight
    }

    fun setQuadrilateralInteractionInterface(quadrilateralInteractionInterface: QuadrilateralInteractionInterface) {
        this.quadrilateralInteractionInterface = quadrilateralInteractionInterface
    }

    fun reset() {
        quadrilaterals.clear()
        unSelect()
    }

    fun resetScale() {
        quadrilateralViewAttacher.scale = 1.0F
    }

    fun delete() {
        if (selectedQuadrilateralIndex != -1 && selectedQuadrilateralIndex < quadrilaterals.size) {
            quadrilaterals.removeAt(selectedQuadrilateralIndex)

            quadrilateralSelected = false
            invalidate()
            quadrilateralInteractionInterface?.selected(false)
        }
    }

    fun unSelect() {
        if (quadrilateralSelected) {
            selectedQuadrilateral?.let {
                it.isSelected = false
            }
            quadrilateralSelected = false
            selectedQuadrilateralIndex = -1
            quadrilateralInteractionInterface?.selected(false)
            invalidate()
        }
    }

    fun getAnnotatedQuadrilateral(
        bitmapWidth: Float, bitmapHeight: Float
    ): List<AnnotationObjectsAttribute> {
        val annotationObjectsAttributes = mutableListOf<AnnotationObjectsAttribute>()
        quadrilaterals.forEach {
            annotationObjectsAttributes.add(it.transformWrtBitmap(bitmapWidth, bitmapHeight))
        }
        return annotationObjectsAttributes
    }

    fun setZoomView(zoomView: co.nayan.c3views.ZoomView) {
        this.zoomView = zoomView
    }

    fun editMode(isEnabled: Boolean) {
        quadrilateralViewAttacher.setEditMode(isEnabled)
    }

    fun touchEnabled(isEnabled: Boolean) {
        quadrilateralViewAttacher.setTouchEnabled(isEnabled)
    }

    fun addHintData(toAdd: List<Quadrilateral>) {
        hintData.clear()
        hintData.addAll(toAdd)
    }

    fun undo() {
        reset()
        invalidate()
    }

    private fun strokeWidth(): Float {
        quadrilateralViewAttacher.displayRect?.let {
            return when {
                width < 512 && height < 512 -> {
                    4f
                }
                else -> {
                    8f
                }
            }
        }
        return 8f
    }

    fun addQuadrilateralsIfNotExist(toAdd: List<Quadrilateral>?) {
        toAdd?.firstOrNull()?.let { quadrilateral ->
            val isExist =
                quadrilaterals.any {
                    it.touchDown.x == quadrilateral.touchDown.x && it.touchDown.y == quadrilateral.touchDown.y &&
                            it.releasePoint.x == quadrilateral.releasePoint.x && it.releasePoint.y == quadrilateral.releasePoint.y
                }
            if (isExist.not()) {
                quadrilaterals.clear()
                quadrilaterals.add(quadrilateral)
            }
        }
    }

    fun addDetectedEdgePoints(toAdd: MutableList<QuadrilateralPoint>) {
        detectedEdgePoints.clear()
        detectedEdgePoints.addAll(toAdd)
    }
}

interface QuadrilateralInteractionInterface {
    fun selected(status: Boolean)
    fun onQuadrilateralDrawn()
    fun setUpZoomView(isShowing: Boolean, x: Float, y: Float)
    fun onUpdateQuadrilaterals()
}

data class QuadrilateralPaints(
    val innerQuadPaint: Paint = Paint(),
    val outerQuadPaint: Paint = Paint(),
    val movablePointsPaint: Paint = Paint(),
    val hintPaint: Paint = Paint()
) {
    fun setStrokeWidth(width: Float) {
        outerQuadPaint.strokeWidth = width / 2
        innerQuadPaint.strokeWidth = width
        hintPaint.strokeWidth = width
    }
}