package co.nayan.c3views.crop

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.Keep
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationState
import co.nayan.c3v2.core.models.AnnotationValue
import co.nayan.c3views.Constants.Dimension.MIN_DIAGONAL_DISTANCE
import co.nayan.c3views.Constants.Dimension.MIN_DIMENSION
import co.nayan.c3views.utils.initials
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

@Keep
data class Cropping(
    var touchDown: CropPoint,
    var releasePoint: CropPoint,
    var input: String? = null,
    var tags: List<String>? = null,
    var paintColor: String? = null,
    var isInterpolated: Boolean = false,
    var id: String? = null,
    var name: String? = null,
    @SerializedName("annotation_state")
    var annotationState: AnnotationState? = AnnotationState.DEFAULT,
    @Transient var shouldRemove: Boolean = false
) {
    private lateinit var displayRect: RectF
    var isSelected = false

    private var center: CropPoint = getCropCenter()
    var touchSymmetric = CropPoint(releasePoint.rawX, touchDown.rawY)
    var releaseSymmetric = CropPoint(touchDown.rawX, releasePoint.rawY)

    private var topLeftCenter = getSegmentsCenter(1f / 6f, 1f / 6f)
    private var topMiddleCenter = getSegmentsCenter(1f / 2f, 1f / 6f)
    private var topRightCenter = getSegmentsCenter(5f / 6f, 1f / 6f)

    private var middleLeftCenter = getSegmentsCenter(1f / 6f, 1f / 2f)
    private var middleRightCenter = getSegmentsCenter(5f / 6f, 1f / 2f)

    private var bottomLeftCenter = getSegmentsCenter(1f / 6f, 5f / 6f)
    private var bottomMiddleCenter = getSegmentsCenter(1f / 2f, 5f / 6f)
    private var bottomRightCenter = getSegmentsCenter(5f / 6f, 5f / 6f)

    private fun getSegmentsCenter(multipleOfX: Float, multipleOfY: Float) =
        CropPoint(
            touchDown.rawX + multipleOfX * (releasePoint.rawX - touchDown.rawX),
            touchDown.rawY + multipleOfY * (releasePoint.rawY - touchDown.rawY)
        )

    private fun getCropCenter() = CropPoint(
        (touchDown.rawX + releasePoint.rawX) / 2F,
        (touchDown.rawY + releasePoint.rawY) / 2F
    )

    fun draw(
        canvas: Canvas,
        toSet: RectF,
        croppingPaints: CroppingPaints,
        isInLabelingMode: Boolean = false,
        showLabel: Boolean = true,
        isAIEnabled: Boolean = false
    ) {
        displayRect = toSet
        if (displayRectIsInitialized()) {
            if (showLabel) {
                if (input.isNullOrEmpty().not())
                    input?.let { drawText(it, canvas, displayRect) }
                else if (tags.isNullOrEmpty().not())
                    tags?.initials()?.let { drawText(it, canvas, displayRect) }
            }

            if (isSelected) {
                if (isInLabelingMode)
                    drawRect(canvas, croppingPaints.selectedCropPaint, displayRect)
                else {
                    drawRect(canvas, croppingPaints.innerCropPaint, displayRect)
                    drawRect(canvas, croppingPaints.outerCropPaint, displayRect)
                    drawMovableCorners(canvas, croppingPaints.movablePointsPaint, displayRect)
                    drawDashedLines(canvas, croppingPaints.dashedLinePaint, displayRect)
                }
            } else {
                if (tags.isNullOrEmpty()) {
                    if (isAIEnabled && shouldRemove) {
                        drawRect(canvas, croppingPaints.aiDrawnInnerCropPaint, displayRect)
                        drawRect(canvas, croppingPaints.aiDrawnOuterCropPaint, displayRect)
                    } else {
                        drawRect(canvas, croppingPaints.innerCropPaint, displayRect)
                        drawRect(canvas, croppingPaints.outerCropPaint, displayRect)
                    }
                } else {
                    drawRect(canvas, croppingPaints.taggedInnerCropPaint, displayRect)
                    drawRect(canvas, croppingPaints.taggedOuterCropPaint, displayRect)
                }
            }
        }
    }

    private fun drawDashedLines(canvas: Canvas, dashedLinePaint: Paint, displayRect: RectF) {
        drawFirstHorizontalDashedLine(canvas, dashedLinePaint, displayRect)
        drawSecondHorizontalDashedLine(canvas, dashedLinePaint, displayRect)
        drawFirstVerticalDashedLine(canvas, dashedLinePaint, displayRect)
        drawSecondVerticalDashedLine(canvas, dashedLinePaint, displayRect)
    }

    private fun drawRect(canvas: Canvas, paint: Paint, displayRect: RectF) {
        val finalPaint = Paint().apply {
            color = when {
                (paintColor.isNullOrEmpty().not()) -> Color.parseColor(paintColor)
                else -> paint.color
            }
            strokeWidth = paint.strokeWidth
            style = paint.style
        }

        val path = Path()
        path.moveTo(touchDown.transform(displayRect).x, touchDown.transform(displayRect).y)
        path.lineTo(
            touchSymmetric.transform(displayRect).x,
            touchSymmetric.transform(displayRect).y
        )
        path.lineTo(
            releasePoint.transform(displayRect).x,
            releasePoint.transform(displayRect).y
        )
        path.lineTo(
            releaseSymmetric.transform(displayRect).x,
            releaseSymmetric.transform(displayRect).y
        )
        path.lineTo(touchDown.transform(displayRect).x, touchDown.transform(displayRect).y)
        canvas.drawPath(path, finalPaint)
    }

    private fun drawMovableCorners(canvas: Canvas, paint: Paint, displayRect: RectF) {
        val xBorder = (releasePoint.x - touchDown.x) / 8
        val yBorder = (releasePoint.y - touchDown.y) / 8
        drawTopLeftCorner(canvas, paint, displayRect, xBorder, yBorder)
        drawTopRightCorner(canvas, paint, displayRect, xBorder, yBorder)
        drawBottomRightCorner(canvas, paint, displayRect, xBorder, yBorder)
        drawBottomLeftCorner(canvas, paint, displayRect, xBorder, yBorder)
    }

    private fun drawTopLeftCorner(
        canvas: Canvas, paint: Paint, displayRect: RectF, xBorder: Float, yBorder: Float
    ) {
        val path = Path()
        path.moveTo(
            touchDown.transform(displayRect).x,
            touchDown.transform(displayRect).y + yBorder
        )
        path.lineTo(
            touchDown.transform(displayRect).x,
            touchDown.transform(displayRect).y
        )
        path.lineTo(
            touchDown.transform(displayRect).x + xBorder,
            touchDown.transform(displayRect).y
        )
        canvas.drawPath(path, paint)
    }

    private fun drawTopRightCorner(
        canvas: Canvas, paint: Paint, displayRect: RectF, xBorder: Float, yBorder: Float
    ) {
        val path = Path()
        path.moveTo(
            touchSymmetric.transform(displayRect).x - xBorder,
            touchSymmetric.transform(displayRect).y
        )
        path.lineTo(
            touchSymmetric.transform(displayRect).x,
            touchSymmetric.transform(displayRect).y
        )
        path.lineTo(
            touchSymmetric.transform(displayRect).x,
            touchSymmetric.transform(displayRect).y + yBorder
        )
        canvas.drawPath(path, paint)
    }

    private fun drawBottomRightCorner(
        canvas: Canvas, paint: Paint, displayRect: RectF, xBorder: Float, yBorder: Float
    ) {
        val path = Path()
        path.moveTo(
            releasePoint.transform(displayRect).x,
            releasePoint.transform(displayRect).y - yBorder
        )
        path.lineTo(
            releasePoint.transform(displayRect).x,
            releasePoint.transform(displayRect).y
        )
        path.lineTo(
            releasePoint.transform(displayRect).x - xBorder,
            releasePoint.transform(displayRect).y
        )
        canvas.drawPath(path, paint)
    }

    private fun drawBottomLeftCorner(
        canvas: Canvas, paint: Paint, displayRect: RectF, xBorder: Float, yBorder: Float
    ) {
        val path = Path()
        path.moveTo(
            releaseSymmetric.transform(displayRect).x + xBorder,
            releaseSymmetric.transform(displayRect).y
        )
        path.lineTo(
            releaseSymmetric.transform(displayRect).x,
            releaseSymmetric.transform(displayRect).y
        )
        path.lineTo(
            releaseSymmetric.transform(displayRect).x,
            releaseSymmetric.transform(displayRect).y - yBorder
        )
        canvas.drawPath(path, paint)
    }

    private fun drawFirstHorizontalDashedLine(canvas: Canvas, paint: Paint, displayRect: RectF) {
        val path = Path()
        val y =
            (releaseSymmetric.transform(displayRect).y - touchDown.transform(displayRect).y) / 3 +
                    touchDown.transform(displayRect).y
        path.moveTo(touchDown.transform(displayRect).x, y)
        path.lineTo(touchSymmetric.transform(displayRect).x, y)
        canvas.drawPath(path, paint)
    }

    private fun drawSecondHorizontalDashedLine(canvas: Canvas, paint: Paint, displayRect: RectF) {
        val path = Path()
        val y =
            2 * (releaseSymmetric.transform(displayRect).y - touchDown.transform(displayRect).y) / 3 +
                    touchDown.transform(displayRect).y
        path.moveTo(touchDown.transform(displayRect).x, y)
        path.lineTo(touchSymmetric.transform(displayRect).x, y)
        canvas.drawPath(path, paint)
    }

    private fun drawFirstVerticalDashedLine(canvas: Canvas, paint: Paint, displayRect: RectF) {
        val path = Path()
        val x = (touchSymmetric.transform(displayRect).x - touchDown.transform(displayRect).x) / 3 +
                touchDown.transform(displayRect).x
        path.moveTo(x, touchDown.transform(displayRect).y)
        path.lineTo(x, releasePoint.transform(displayRect).y)
        canvas.drawPath(path, paint)
    }

    private fun drawSecondVerticalDashedLine(canvas: Canvas, paint: Paint, displayRect: RectF) {
        val path = Path()
        val x =
            2 * (touchSymmetric.transform(displayRect).x - touchDown.transform(displayRect).x) / 3 +
                    touchDown.transform(displayRect).x
        path.moveTo(x, touchDown.transform(displayRect).y)
        path.lineTo(x, releasePoint.transform(displayRect).y)
        canvas.drawPath(path, paint)
    }

    private fun drawText(text: String, canvas: Canvas, displayRect: RectF) {
        val length = text.length
        val textSize = min(
            textSizeWRTY(length, touchDown, releasePoint),
            textSizeWRTX(length, touchDown, releasePoint)
        )

        val right = min(touchDown.transform(displayRect).x, releasePoint.transform(displayRect).x) +
                when (length) {
                    in 0..10 -> ((4f / 5) * length * textSize)
                    else -> (3f / 5 * length * textSize)
                }
        val bottom =
            min(touchDown.transform(displayRect).y, releasePoint.transform(displayRect).y) +
                    textSize

        val textRect = RectF(
            min(touchDown.transform(displayRect).x, releasePoint.transform(displayRect).x),
            min(touchDown.transform(displayRect).y, releasePoint.transform(displayRect).y),
            right,
            bottom + 6
        )
        canvas.drawRect(textRect, textRectPaint)

        textPaint.textSize = textSize
        canvas.drawText(
            text,
            min(touchDown.transform(displayRect).x, releasePoint.transform(displayRect).x) + 4,
            bottom,
            textPaint
        )
    }

    private fun textSizeWRTX(textLength: Int, point1: CropPoint, point2: CropPoint): Float {
        return when (val displayArea =
            max(point1.transform(displayRect).x, point2.transform(displayRect).x) -
                    min(point1.transform(displayRect).x, point2.transform(displayRect).x)) {
            in 0f..200f -> displayArea / 4
            in 201f..400f -> displayArea / 8
            else -> {
                when (textLength) {
                    in 0..8 -> displayArea / 10
                    else -> displayArea / (textLength + textLength / 3)
                }
            }
        }.coerceAtLeast(20f)
    }

    private fun textSizeWRTY(textLength: Int, point1: CropPoint, point2: CropPoint): Float {
        return when (val displayArea =
            max(point1.transform(displayRect).y, point2.transform(displayRect).y) -
                    min(point1.transform(displayRect).y, point2.transform(displayRect).y)) {
            in 0f..200f -> displayArea / 4
            in 201f..400f -> displayArea / 8
            else -> {
                when (textLength) {
                    in 0..8 -> displayArea / 10
                    else -> displayArea / (textLength + textLength / 3)
                }
            }
        }.coerceAtLeast(20f)
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#ffffff")
        style = Paint.Style.FILL
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val textRectPaint = Paint().apply {
        color = Color.parseColor("#60000000")
        style = Paint.Style.FILL
    }

    private fun getAnnotationAttribute(): AnnotationObjectsAttribute {
        val points = mutableListOf(
            arrayListOf(min(touchDown.x, releasePoint.x), min(touchDown.y, releasePoint.y)),
            arrayListOf(max(touchDown.x, releasePoint.x), max(touchDown.y, releasePoint.y))
        )
        val answer = AnnotationData(
            points = points,
            input = input,
            tags = tags,
            type = DrawType.BOUNDING_BOX,
            objectIndex = id,
            objectName = name,
            paintColor = paintColor,
            annotationState = annotationState,
            shouldRemove = shouldRemove
        )
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        return AnnotationObjectsAttribute(AnnotationValue(answer = gson.toJson(answer)))
    }

    fun transformWrtBitmap(bitmapWidth: Float, bitmapHeight: Float): AnnotationObjectsAttribute {
        getCropPoints().forEach {
            it.transformWrtBitmap(bitmapWidth, bitmapHeight)
        }
        return getAnnotationAttribute()
    }

    fun resizeRect(rawX: Float, rawY: Float, scale: Float) {
        if (displayRectIsInitialized()) {
            val currentTouchPoint = CropPoint(rawX, rawY).transform(displayRect)
            getControlPointList(currentTouchPoint)
                .minByOrNull { pair -> pair.second }
                ?.let { nearestPair ->
                    if (nearestPair.second < 6000f) {
                        when (nearestPair.first) {
                            touchDown -> {
                                val moveX = currentTouchPoint.rawX - touchDown.rawX
                                val moveY = currentTouchPoint.rawY - touchDown.rawY
                                nearestPair.first.rawX += moveX
                                nearestPair.first.rawY += moveY

                                releaseSymmetric.rawX = nearestPair.first.rawX
                                touchSymmetric.rawY = nearestPair.first.rawY
                            }

                            releasePoint -> {
                                val moveX = currentTouchPoint.rawX - releasePoint.rawX
                                val moveY = currentTouchPoint.rawY - releasePoint.rawY
                                nearestPair.first.rawX += moveX
                                nearestPair.first.rawY += moveY

                                touchSymmetric.rawX = nearestPair.first.rawX
                                releaseSymmetric.rawY = nearestPair.first.rawY
                            }

                            releaseSymmetric -> {
                                val moveX = currentTouchPoint.rawX - releaseSymmetric.rawX
                                val moveY = currentTouchPoint.rawY - releaseSymmetric.rawY
                                nearestPair.first.rawX += moveX
                                nearestPair.first.rawY += moveY

                                touchDown.rawX = nearestPair.first.rawX
                                releasePoint.rawY = nearestPair.first.rawY
                            }

                            touchSymmetric -> {
                                val moveX = currentTouchPoint.rawX - touchSymmetric.rawX
                                val moveY = currentTouchPoint.rawY - touchSymmetric.rawY
                                nearestPair.first.rawX += moveX
                                nearestPair.first.rawY += moveY

                                releasePoint.rawX = nearestPair.first.rawX
                                touchDown.rawY = nearestPair.first.rawY
                            }

                            else -> move(currentTouchPoint, nearestPair.first, scale)
                        }
                    }
                    center = getCropCenter()
                    topLeftCenter = getSegmentsCenter(1f / 6f, 1f / 6f)
                    topMiddleCenter = getSegmentsCenter(1f / 2f, 1f / 6f)
                    topRightCenter = getSegmentsCenter(5f / 6f, 1f / 6f)
                    middleLeftCenter = getSegmentsCenter(1f / 6f, 1f / 2f)
                    middleRightCenter = getSegmentsCenter(5f / 6f, 1f / 2f)
                    bottomLeftCenter = getSegmentsCenter(1f / 6f, 5f / 6f)
                    bottomMiddleCenter = getSegmentsCenter(1f / 2f, 5f / 6f)
                    bottomRightCenter = getSegmentsCenter(5f / 6f, 5f / 6f)
                    getAbsoluteCropPoints()
                }
        }
    }

    private fun move(currentTouchPoint: CropPoint, centerPoint: CropPoint, scale: Float) {
        val dragBy = CropPoint.minus(centerPoint, currentTouchPoint)
        val topLeft = CropPoint(
            min(touchDown.rawX, releasePoint.rawX),
            min(touchDown.rawY, releasePoint.rawY)
        )
        val bottomRight = CropPoint(
            max(touchDown.rawX, releasePoint.rawX),
            max(touchDown.rawY, releasePoint.rawY)
        )
        if (displayRectIsInitialized()
            && validDistance(topLeft, bottomRight, displayRect, scale)
            && validDimensions(dragBy, topLeft, bottomRight, displayRect)
        ) {
            touchDown.minus(dragBy)
            releasePoint.minus(dragBy)
            touchSymmetric.minus(dragBy)
            releaseSymmetric.minus(dragBy)
        }
    }

    fun isMovedAboveThreshold(rawTouchPoint: CropPoint, scale: Float): Boolean {
        var status = false
        if (displayRectIsInitialized()) {
            val currentTouchPoint = rawTouchPoint.transform(displayRect)
            getEdgesPointList(currentTouchPoint)
                .minByOrNull { pair -> pair.second }
                ?.let { nearestPair ->
                    when (nearestPair.first) {
                        touchDown -> releasePoint
                        releasePoint -> touchDown
                        releaseSymmetric -> touchSymmetric
                        touchSymmetric -> releaseSymmetric
                        else -> null
                    }?.let { releasePoint ->
                        status = validDistance(rawTouchPoint, releasePoint, displayRect, scale)
                    } ?: run { status = false }
                }
        }

        return status
    }

    private fun validDistance(
        touchDown: CropPoint,
        releasePoint: CropPoint,
        displayRect: RectF,
        scaleFactor: Float
    ): Boolean {
        val diagonalDistance = CropPoint.diagonalDistance(touchDown, releasePoint, displayRect)
        val distance = CropPoint.distance(touchDown, releasePoint, displayRect)
        Timber.e("#### Current cropView scaleFactor $scaleFactor ####")
        Timber.e("#### Diagonal Distance $diagonalDistance//${(MIN_DIAGONAL_DISTANCE * scaleFactor)} ####")
        Timber.e("#### Crop Height Edge ${distance.first}//${(MIN_DIMENSION * scaleFactor)} ####")
        Timber.e("#### Crop Width Edge ${distance.second}//${(MIN_DIMENSION * scaleFactor)} ####")
        return diagonalDistance > (MIN_DIAGONAL_DISTANCE * scaleFactor)
                && distance.first >= (MIN_DIMENSION * scaleFactor)
                && distance.second >= (MIN_DIMENSION * scaleFactor)
    }

    private fun validDimensions(
        dragBy: CropPoint, first: CropPoint, second: CropPoint, displayRect: RectF
    ): Boolean {
        return CropPoint.minus(first, dragBy).transform(displayRect).x > displayRect.left &&
                CropPoint.minus(first, dragBy).transform(displayRect).y > displayRect.top &&
                CropPoint.minus(second, dragBy).transform(displayRect).x < displayRect.right &&
                CropPoint.minus(second, dragBy).transform(displayRect).y < displayRect.bottom
    }

    private fun getEdgesPointList(touchPoint: CropPoint): List<Pair<CropPoint, Float>> {
        val controlPoints = mutableListOf<Pair<CropPoint, Float>>()
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
                touchSymmetric.transform(displayRect),
                lengthSquare(touchPoint, touchSymmetric)
            )
        )
        controlPoints.add(
            Pair(
                releaseSymmetric.transform(displayRect),
                lengthSquare(touchPoint, releaseSymmetric)
            )
        )

        return controlPoints
    }

    private fun getControlPointList(touchPoint: CropPoint): List<Pair<CropPoint, Float>> {
        val controlPoints = mutableListOf<Pair<CropPoint, Float>>()
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
                touchSymmetric.transform(displayRect),
                lengthSquare(touchPoint, touchSymmetric)
            )
        )
        controlPoints.add(
            Pair(
                releaseSymmetric.transform(displayRect),
                lengthSquare(touchPoint, releaseSymmetric)
            )
        )
        controlPoints.add(
            Pair(
                center.transform(displayRect),
                lengthSquare(touchPoint, center)
            )
        )
        controlPoints.add(
            Pair(
                topLeftCenter.transform(displayRect),
                lengthSquare(touchPoint, topLeftCenter)
            )
        )
        controlPoints.add(
            Pair(
                topMiddleCenter.transform(displayRect),
                lengthSquare(touchPoint, topMiddleCenter)
            )
        )
        controlPoints.add(
            Pair(
                topRightCenter.transform(displayRect),
                lengthSquare(touchPoint, topRightCenter)
            )
        )
        controlPoints.add(
            Pair(
                middleLeftCenter.transform(displayRect),
                lengthSquare(touchPoint, middleLeftCenter)
            )
        )
        controlPoints.add(
            Pair(
                middleRightCenter.transform(displayRect),
                lengthSquare(touchPoint, middleRightCenter)
            )
        )
        controlPoints.add(
            Pair(
                bottomLeftCenter.transform(displayRect),
                lengthSquare(touchPoint, bottomLeftCenter)
            )
        )
        controlPoints.add(
            Pair(
                bottomMiddleCenter.transform(displayRect),
                lengthSquare(touchPoint, bottomMiddleCenter)
            )
        )
        controlPoints.add(
            Pair(
                bottomRightCenter.transform(displayRect),
                lengthSquare(touchPoint, bottomRightCenter)
            )
        )
        return controlPoints
    }

    private fun lengthSquare(p1: CropPoint, p2: CropPoint): Float {
        val xDiff = p1.x - p2.x
        val yDiff = p1.y - p2.y
        return xDiff * xDiff + yDiff * yDiff
    }

    private fun getCropPoints(): List<CropPoint> {
        val points = mutableListOf<CropPoint>()
        points.add(touchDown)
        points.add(touchSymmetric)
        points.add(releasePoint)
        points.add(releaseSymmetric)
        return points
    }

    fun getAbsoluteCropPoints(): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        if (displayRectIsInitialized()) {
            points.add(touchDown.absolute(displayRect))
            points.add(touchSymmetric.absolute(displayRect))
            points.add(releasePoint.absolute(displayRect))
            points.add(releaseSymmetric.absolute(displayRect))
        }
        return points
    }

    private fun displayRectIsInitialized() = this@Cropping::displayRect.isInitialized
}