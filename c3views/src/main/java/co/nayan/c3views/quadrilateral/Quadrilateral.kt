package co.nayan.c3views.quadrilateral

import android.graphics.*
import androidx.annotation.Keep
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationValue
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder

@Keep
data class Quadrilateral(
    var touchDown: QuadrilateralPoint,
    var releasePoint: QuadrilateralPoint,
    val templateName: String? = "",
    val paintColor: String? = null
) {

    constructor(
        touchDown: QuadrilateralPoint, topRight: QuadrilateralPoint,
        releasePoint: QuadrilateralPoint, bottomLeft: QuadrilateralPoint, templateName: String?
    ) : this(touchDown, releasePoint, templateName) {
        this.topRight.rawX = topRight.rawX
        this.topRight.rawY = topRight.rawY
        this.bottomLeft.rawX = bottomLeft.rawX
        this.bottomLeft.rawY = bottomLeft.rawY
    }

    private lateinit var displayRect: RectF

    var isSelected = false
    private var center: QuadrilateralPoint = getCenter()
    var bottomLeft: QuadrilateralPoint =
        QuadrilateralPoint(touchDown.rawX, releasePoint.rawY)

    var topRight: QuadrilateralPoint = QuadrilateralPoint(releasePoint.rawX, touchDown.rawY)

    private fun getCenter(): QuadrilateralPoint {
        return QuadrilateralPoint(
            (touchDown.rawX + releasePoint.rawX) / 2F,
            (touchDown.rawY + releasePoint.rawY) / 2F
        )
    }

    fun draw(canvas: Canvas, displayRect: RectF, quadrilateralPaints: QuadrilateralPaints) {
        this.displayRect = displayRect
        drawRect(canvas, quadrilateralPaints.innerQuadPaint, displayRect)
        drawRect(canvas, quadrilateralPaints.outerQuadPaint, displayRect)
        if (isSelected) {
            drawMovablePoints(canvas, quadrilateralPaints.movablePointsPaint, displayRect)
        }
    }

    private fun drawRect(canvas: Canvas, paint: Paint, displayRect: RectF) {
        if (paintColor.isNullOrEmpty().not()) {
            paint.color = Color.parseColor(paintColor)
        }
        val path = Path()
        path.moveTo(touchDown.transform(displayRect).x, touchDown.transform(displayRect).y)
        path.lineTo(
            topRight.transform(displayRect).x,
            topRight.transform(displayRect).y
        )
        path.lineTo(
            releasePoint.transform(displayRect).x,
            releasePoint.transform(displayRect).y
        )
        path.lineTo(
            bottomLeft.transform(displayRect).x,
            bottomLeft.transform(displayRect).y
        )
        path.lineTo(touchDown.transform(displayRect).x, touchDown.transform(displayRect).y)
        canvas.drawPath(path, paint)
    }

    private fun drawMovablePoints(canvas: Canvas, paint: Paint, displayRect: RectF) {
        val radius = 12f
        canvas.drawCircle(
            touchDown.transform(displayRect).x,
            touchDown.transform(displayRect).y,
            radius,
            paint
        )
        canvas.drawCircle(
            releasePoint.transform(displayRect).x,
            releasePoint.transform(displayRect).y,
            radius,
            paint
        )
        canvas.drawCircle(
            topRight.transform(displayRect).x,
            topRight.transform(displayRect).y,
            radius,
            paint
        )
        canvas.drawCircle(
            bottomLeft.transform(displayRect).x,
            bottomLeft.transform(displayRect).y,
            radius,
            paint
        )
        canvas.drawCircle(
            center.transform(displayRect).x,
            center.transform(displayRect).y,
            radius,
            paint
        )
    }

    private fun getAnnotationAttribute(): AnnotationObjectsAttribute {
        val points = mutableListOf(
            arrayListOf(touchDown.x, touchDown.y),
            arrayListOf(topRight.x, topRight.y),
            arrayListOf(releasePoint.x, releasePoint.y),
            arrayListOf(bottomLeft.x, bottomLeft.y)
        )
        val answer = AnnotationData(points = points, type = DrawType.QUADRILATERAL)
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        return AnnotationObjectsAttribute(AnnotationValue(answer = gson.toJson(answer)))
    }

    fun transformWrtBitmap(bitmapWidth: Float, bitmapHeight: Float): AnnotationObjectsAttribute {
        getQuadrilateralPoints().forEach {
            it.transformWrtBitmap(bitmapWidth, bitmapHeight)
        }
        return getAnnotationAttribute()
    }

    fun resizeRect(rawX: Float, rawY: Float): MovablePoint? {
        var movablePoint: MovablePoint? = null
        val touchPoint = QuadrilateralPoint(rawX, rawY).transform(displayRect)
        getControlPointList(touchPoint)
            .minByOrNull { pair -> pair.second }?.let { nearest_pair ->
                if (nearest_pair.second < 1500f) {
                    when (nearest_pair.first) {
                        center -> {
                            move(
                                touchDown,
                                releasePoint,
                                topRight,
                                bottomLeft,
                                touchPoint
                            )
                            movablePoint = MovablePoint.CENTER
                        }
                        touchDown -> {
                            if ((touchPoint.y > releasePoint.y && touchPoint.x > bottomLeft.x) ||
                                touchPoint.y > bottomLeft.y ||
                                touchPoint.x > topRight.x ||
                                (touchPoint.x > releasePoint.x && touchPoint.y > topRight.y)
                            ) {
                                return null
                            } else {
                                movablePoint = MovablePoint.TOUCH_DOWN
                                nearest_pair.first.rawX = touchPoint.rawX
                                nearest_pair.first.rawY = touchPoint.rawY
                            }
                        }
                        releasePoint -> {
                            if ((touchPoint.x < touchDown.x && touchPoint.y < bottomLeft.y) ||
                                touchPoint.x < bottomLeft.x ||
                                (touchPoint.y < touchDown.y && touchPoint.x < topRight.x) ||
                                touchPoint.y < topRight.y
                            ) {
                                return null
                            } else {
                                movablePoint = MovablePoint.RELEASE_POINT
                                nearest_pair.first.rawX = touchPoint.rawX
                                nearest_pair.first.rawY = touchPoint.rawY
                            }
                        }
                        bottomLeft -> {
                            if ((touchPoint.x > topRight.x && touchPoint.y < releasePoint.y) ||
                                touchPoint.x > releasePoint.x ||
                                touchPoint.y < touchDown.y ||
                                (touchPoint.y < topRight.y && touchPoint.x > touchDown.x)
                            ) {
                                return null
                            } else {
                                movablePoint = MovablePoint.BOTTOM_LEFT
                                nearest_pair.first.rawX = touchPoint.rawX
                                nearest_pair.first.rawY = touchPoint.rawY
                            }
                        }
                        topRight -> {
                            if (touchPoint.x < touchDown.x ||
                                (touchPoint.x < bottomLeft.x && touchPoint.y > touchDown.y) ||
                                (touchPoint.y > bottomLeft.y && touchPoint.x < releasePoint.x) ||
                                touchPoint.y > releasePoint.y
                            ) {
                                return null
                            } else {
                                movablePoint = MovablePoint.TOP_RIGHT
                                nearest_pair.first.rawX = touchPoint.rawX
                                nearest_pair.first.rawY = touchPoint.rawY
                            }
                        }
                    }
                    center = getCenter()
                    getAbsoluteQuadrilateralPoints()
                }
            }
        return movablePoint
    }

    private fun move(
        first: QuadrilateralPoint,
        second: QuadrilateralPoint,
        third: QuadrilateralPoint,
        fourth: QuadrilateralPoint,
        touchPoint: QuadrilateralPoint
    ) {
        val dragBy = QuadrilateralPoint.minus(center, touchPoint)
        if (validDimensions(dragBy, first, second)) {
            first.minus(dragBy)
            second.minus(dragBy)
            third.minus(dragBy)
            fourth.minus(dragBy)
        }
    }

    private fun validDimensions(
        dragBy: QuadrilateralPoint, first: QuadrilateralPoint, second: QuadrilateralPoint
    ) = QuadrilateralPoint.minus(first, dragBy).transform(displayRect).x > displayRect.left &&
            QuadrilateralPoint.minus(first, dragBy).transform(displayRect).y > displayRect.top &&
            QuadrilateralPoint.minus(second, dragBy).transform(displayRect).x < displayRect.right &&
            QuadrilateralPoint.minus(second, dragBy).transform(displayRect).y < displayRect.bottom

    private fun getControlPointList(touchPoint: QuadrilateralPoint): List<Pair<QuadrilateralPoint, Float>> {
        val controlPoints = mutableListOf<Pair<QuadrilateralPoint, Float>>()
        controlPoints.add(
            Pair(
                releasePoint.transform(displayRect),
                lengthSquare(touchPoint, releasePoint)
            )
        )
        controlPoints.add(
            Pair(
                touchDown.transform(displayRect),
                lengthSquare(touchPoint, touchDown)
            )
        )
        controlPoints.add(
            Pair(
                topRight.transform(displayRect),
                lengthSquare(touchPoint, topRight)
            )
        )
        controlPoints.add(
            Pair(
                bottomLeft.transform(displayRect),
                lengthSquare(touchPoint, bottomLeft)
            )
        )
        controlPoints.add(
            Pair(
                center.transform(displayRect),
                lengthSquare(touchPoint, center)
            )
        )
        return controlPoints
    }

    private fun lengthSquare(p1: QuadrilateralPoint, p2: QuadrilateralPoint): Float {
        val xDiff = p1.x - p2.x
        val yDiff = p1.y - p2.y
        return xDiff * xDiff + yDiff * yDiff
    }

    private fun getQuadrilateralPoints(): List<QuadrilateralPoint> {
        val points = mutableListOf<QuadrilateralPoint>()
        points.add(touchDown)
        points.add(topRight)
        points.add(releasePoint)
        points.add(bottomLeft)
        return points
    }

    fun getAbsoluteQuadrilateralPoints(): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        if (this@Quadrilateral::displayRect.isInitialized) {
            points.add(touchDown.absolute(displayRect))
            points.add(topRight.absolute(displayRect))
            points.add(releasePoint.absolute(displayRect))
            points.add(bottomLeft.absolute(displayRect))
        }
        return points
    }

    fun setOtherPoints(topRight: QuadrilateralPoint, bottomLeft: QuadrilateralPoint) {
        this.topRight = topRight
        this.bottomLeft = bottomLeft
    }

    enum class MovablePoint {
        CENTER, TOUCH_DOWN, TOP_RIGHT, RELEASE_POINT, BOTTOM_LEFT
    }
}