package co.nayan.c3views.dragsplit

import android.graphics.*
import androidx.annotation.Keep
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationValue
import co.nayan.c3views.crop.CropPoint
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import timber.log.Timber
import kotlin.math.min


@Keep
data class SplitCropping(
    val touchDown: CropPoint,
    val releasePoint: CropPoint,
    val segmentRatioList: MutableList<Float>,
    var inputList: MutableList<String>,
    val paintColor: String? = null
) {
    private lateinit var displayRect: RectF
    var isSelected = false
    var selectedSegmentIndex = -1

    private var center: CropPoint = getCropCenter()
    var bottomLeft = CropPoint(touchDown.rawX, releasePoint.rawY)
    var topRight = CropPoint(releasePoint.rawX, touchDown.rawY)

    private var leftCenter = CropPoint(touchDown.rawX, center.rawY)
    private var topCenter = CropPoint(center.rawX, touchDown.rawY)
    private var rightCenter = CropPoint(releasePoint.rawX, center.rawY)
    private var bottomCenter = CropPoint(center.rawX, releasePoint.rawY)

    private fun getCropCenter() = CropPoint(
        (touchDown.rawX + releasePoint.rawX) / 2F,
        (touchDown.rawY + releasePoint.rawY) / 2F
    )

    fun draw(
        canvas: Canvas,
        toSet: RectF,
        isInLabelingMode: Boolean,
        dragSplitPaints: DragSplitPaints,
        showLabel: Boolean = true
    ) {
        displayRect = toSet
        if (displayRectIsInitialized()) {

            drawRect(canvas, dragSplitPaints.innerRectPaint, displayRect)
            drawRect(canvas, dragSplitPaints.outerRectPaint, displayRect)

            drawSegments(canvas, dragSplitPaints, isInLabelingMode)
            if (showLabel) {
                drawLabel(canvas, displayRect)
            }
            if (isSelected && isInLabelingMode.not()) {
                drawMovablePoints(canvas, dragSplitPaints.movablePointPaint, displayRect)
            }
        }
    }

    private fun textSizeWRTX(point1: CropPoint, point2: CropPoint): Float {
        return ((point2.transform(displayRect).x - point1.transform(displayRect).x) / 3)
            .coerceAtLeast(40f)
    }

    private fun textSizeWRTY(point1: CropPoint, point2: CropPoint): Float {
        return ((point2.transform(displayRect).y - point1.transform(displayRect).y) / 3)
            .coerceAtLeast(40f)
    }

    private fun getSegmentValueOfText(
        point1: CropPoint, point2: CropPoint, textSize: Float
    ): Float {
        val width = point2.transform(displayRect).x - point1.transform(displayRect).x
        return textSize / width
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

    private fun drawLabel(canvas: Canvas, displayRect: RectF) {
        val segmentPoints = getSegmentPoints()
        if (segmentPoints.isEmpty()) {
            val input = inputList.firstOrNull()
            if (input.isNullOrEmpty()) {
                return
            } else {
                val textSize = min(
                    textSizeWRTY(touchDown, releasePoint), textSizeWRTX(touchDown, topRight)
                )
                val segmentValueOfText = getSegmentValueOfText(touchDown, topRight, textSize)
                val subPoint = subPoint(touchDown, topRight, segmentValueOfText, 1)

                val path = Path()
                path.moveTo(
                    touchDown.transform(displayRect).x,
                    touchDown.transform(displayRect).y
                )
                path.lineTo(
                    subPoint.transform(displayRect).x,
                    subPoint.transform(displayRect).y
                )
                path.lineTo(
                    subPoint.transform(displayRect).x,
                    subPoint.transform(displayRect).y + textSize + 6
                )
                path.lineTo(
                    touchDown.transform(displayRect).x,
                    touchDown.transform(displayRect).y + textSize + 6
                )
                path.lineTo(
                    touchDown.transform(displayRect).x,
                    touchDown.transform(displayRect).y
                )
                canvas.drawPath(path, textRectPaint)

                textPaint.textSize = textSize
                canvas.drawText(
                    input,
                    touchDown.transform(displayRect).x + 4,
                    touchDown.transform(displayRect).y + textSize,
                    textPaint
                )
            }
        } else {
            for (index in inputList.indices) {
                val input = inputList.elementAtOrNull(index)
                when (index) {
                    0 -> {
                        if (input.isNullOrEmpty()) {
                            return
                        } else {
                            val textSize = min(
                                textSizeWRTY(touchDown, segmentPoints[index].second),
                                textSizeWRTX(touchDown, segmentPoints[index].first)
                            )
                            val segmentValueOfText = getSegmentValueOfText(
                                touchDown,
                                segmentPoints[index].first,
                                textSize
                            )
                            val subPoint = subPoint(
                                touchDown,
                                segmentPoints[index].first,
                                segmentValueOfText,
                                1
                            )

                            val path = Path()
                            path.moveTo(
                                touchDown.transform(displayRect).x,
                                touchDown.transform(displayRect).y
                            )
                            path.lineTo(
                                subPoint.transform(displayRect).x,
                                subPoint.transform(displayRect).y
                            )
                            path.lineTo(
                                subPoint.transform(displayRect).x,
                                subPoint.transform(displayRect).y + textSize + 6
                            )
                            path.lineTo(
                                touchDown.transform(displayRect).x,
                                touchDown.transform(displayRect).y + textSize + 6
                            )
                            path.lineTo(
                                touchDown.transform(displayRect).x,
                                touchDown.transform(displayRect).y
                            )
                            canvas.drawPath(path, textRectPaint)

                            textPaint.textSize = textSize
                            canvas.drawText(
                                inputList[index],
                                touchDown.transform(displayRect).x + 4,
                                touchDown.transform(displayRect).y + textSize,
                                textPaint
                            )
                        }
                    }
                    inputList.size - 1 -> {
                        if (input.isNullOrEmpty()) {
                            return
                        } else {
                            val textSize = min(
                                textSizeWRTY(segmentPoints[index - 1].first, releasePoint),
                                textSizeWRTX(segmentPoints[index - 1].first, topRight)
                            )
                            val segmentValueOfText = getSegmentValueOfText(
                                segmentPoints[index - 1].first,
                                topRight,
                                textSize
                            )
                            val subPoint = subPoint(
                                segmentPoints[index - 1].first,
                                topRight,
                                segmentValueOfText,
                                1
                            )

                            val path = Path()
                            path.moveTo(
                                segmentPoints[index - 1].first.transform(displayRect).x,
                                segmentPoints[index - 1].first.transform(displayRect).y
                            )
                            path.lineTo(
                                subPoint.transform(displayRect).x,
                                subPoint.transform(displayRect).y
                            )
                            path.lineTo(
                                subPoint.transform(displayRect).x,
                                subPoint.transform(displayRect).y + textSize + 6
                            )
                            path.lineTo(
                                segmentPoints[index - 1].first.transform(displayRect).x,
                                segmentPoints[index - 1].first.transform(displayRect).y + textSize + 6
                            )
                            path.lineTo(
                                segmentPoints[index - 1].first.transform(displayRect).x,
                                segmentPoints[index - 1].first.transform(displayRect).y
                            )
                            canvas.drawPath(path, textRectPaint)

                            textPaint.textSize = textSize
                            canvas.drawText(
                                inputList[index],
                                segmentPoints[index - 1].first.transform(displayRect).x + 4,
                                segmentPoints[index - 1].first.transform(displayRect).y + textSize,
                                textPaint
                            )
                        }
                    }
                    else -> {
                        if (input.isNullOrEmpty()) {
                            return
                        } else {
                            val textSize = min(
                                textSizeWRTY(
                                    segmentPoints[index - 1].first,
                                    segmentPoints[index].second
                                ),
                                textSizeWRTX(
                                    segmentPoints[index - 1].first,
                                    segmentPoints[index].first
                                )
                            )
                            val segmentValueOfText = getSegmentValueOfText(
                                segmentPoints[index - 1].first,
                                segmentPoints[index].first,
                                textSize
                            )
                            val subPoint = subPoint(
                                segmentPoints[index - 1].first,
                                segmentPoints[index].first,
                                segmentValueOfText,
                                1
                            )

                            val path = Path()
                            path.moveTo(
                                segmentPoints[index - 1].first.transform(displayRect).x,
                                segmentPoints[index - 1].first.transform(displayRect).y
                            )
                            path.lineTo(
                                subPoint.transform(displayRect).x,
                                subPoint.transform(displayRect).y
                            )
                            path.lineTo(
                                subPoint.transform(displayRect).x,
                                subPoint.transform(displayRect).y + textSize + 6
                            )
                            path.lineTo(
                                segmentPoints[index - 1].first.transform(displayRect).x,
                                segmentPoints[index - 1].first.transform(displayRect).y + textSize + 6
                            )
                            path.lineTo(
                                segmentPoints[index - 1].first.transform(displayRect).x,
                                segmentPoints[index - 1].first.transform(displayRect).y
                            )
                            canvas.drawPath(path, textRectPaint)

                            textPaint.textSize = textSize
                            canvas.drawText(
                                inputList[index],
                                segmentPoints[index - 1].first.transform(displayRect).x + 4,
                                segmentPoints[index - 1].first.transform(displayRect).y + textSize,
                                textPaint
                            )
                        }
                    }
                }
            }
        }
    }

    private fun drawSegments(
        canvas: Canvas,
        dragSplitPaints: DragSplitPaints,
        inLabelingMode: Boolean
    ) {
        val segmentPoints = getSegmentPoints()
        val size = segmentPoints.size
        segmentPoints.forEach { segment ->
            val path = Path()
            path.moveTo(
                segment.first.transform(displayRect).x,
                segment.first.transform(displayRect).y
            )
            path.lineTo(
                segment.second.transform(displayRect).x,
                segment.second.transform(displayRect).y
            )

            canvas.drawPath(path, dragSplitPaints.innerRectPaint)
            canvas.drawPath(path, dragSplitPaints.outerRectPaint)
        }

        if (inLabelingMode) {
            if (selectedSegmentIndex == 0) {
                drawRect(canvas, dragSplitPaints.selectedSegmentPaint, displayRect)
            } else {
                for (index in 1..selectedSegmentIndex) {
                    if (index == selectedSegmentIndex && segmentPoints.isNotEmpty()) {
                        val selectedPath = Path()
                        when (index) {
                            1 -> {
                                selectedPath.moveTo(
                                    touchDown.transform(displayRect).x,
                                    touchDown.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    segmentPoints[index - 1].first.transform(displayRect).x,
                                    segmentPoints[index - 1].first.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    segmentPoints[index - 1].second.transform(displayRect).x,
                                    segmentPoints[index - 1].second.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    bottomLeft.transform(displayRect).x,
                                    bottomLeft.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    touchDown.transform(displayRect).x,
                                    touchDown.transform(displayRect).y
                                )
                                canvas.drawPath(selectedPath, dragSplitPaints.selectedSegmentPaint)
                            }
                            size + 1 -> {
                                selectedPath.moveTo(
                                    segmentPoints[index - 2].first.transform(displayRect).x,
                                    segmentPoints[index - 2].first.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    topRight.transform(displayRect).x,
                                    topRight.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    releasePoint.transform(displayRect).x,
                                    releasePoint.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    segmentPoints[index - 2].second.transform(displayRect).x,
                                    segmentPoints[index - 2].second.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    segmentPoints[index - 2].first.transform(displayRect).x,
                                    segmentPoints[index - 2].first.transform(displayRect).y
                                )
                                canvas.drawPath(selectedPath, dragSplitPaints.selectedSegmentPaint)
                            }
                            else -> {
                                selectedPath.moveTo(
                                    segmentPoints[index - 2].first.transform(displayRect).x,
                                    segmentPoints[index - 2].first.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    segmentPoints[index - 1].first.transform(displayRect).x,
                                    segmentPoints[index - 1].first.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    segmentPoints[index - 1].second.transform(displayRect).x,
                                    segmentPoints[index - 1].second.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    segmentPoints[index - 2].second.transform(displayRect).x,
                                    segmentPoints[index - 2].second.transform(displayRect).y
                                )
                                selectedPath.lineTo(
                                    segmentPoints[index - 2].first.transform(displayRect).x,
                                    segmentPoints[index - 2].first.transform(displayRect).y
                                )
                                canvas.drawPath(selectedPath, dragSplitPaints.selectedSegmentPaint)
                            }
                        }
                    }
                }
            }
        }

        if (isSelected && inLabelingMode.not()) {
            getSegmentCenterPoints().forEach {
                drawSegmentMovablePoint(canvas, dragSplitPaints.outerRectPaint, it)
            }
        }
    }

    private fun drawSegmentMovablePoint(canvas: Canvas, paint: Paint, segmentCenter: CropPoint) {
        val radius = 6f
        canvas.drawCircle(
            segmentCenter.transform(displayRect).x,
            segmentCenter.transform(displayRect).y,
            radius,
            paint
        )
    }

    private fun drawRect(canvas: Canvas, rectPaint: Paint, displayRect: RectF) {
        if (paintColor.isNullOrEmpty().not()) {
            rectPaint.color = Color.parseColor(paintColor)
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
        canvas.drawPath(path, rectPaint)
    }

    private fun drawMovablePoints(canvas: Canvas, paint: Paint, displayRect: RectF) {
        val radius = 14f
        canvas.drawCircle(
            leftCenter.transform(displayRect).x,
            leftCenter.transform(displayRect).y,
            radius,
            paint
        )
        canvas.drawCircle(
            topCenter.transform(displayRect).x,
            topCenter.transform(displayRect).y,
            radius,
            paint
        )
        canvas.drawCircle(
            rightCenter.transform(displayRect).x,
            rightCenter.transform(displayRect).y,
            radius,
            paint
        )
        canvas.drawCircle(
            bottomCenter.transform(displayRect).x,
            bottomCenter.transform(displayRect).y,
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
        val answer = AnnotationData(
            points = points,
            segmentRatioList = segmentRatioList,
            inputList = inputList,
            type = DrawType.SPLIT_BOX
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

    private fun getCenterOf(point1: CropPoint, point2: CropPoint): CropPoint {
        return CropPoint(
            (point1.rawX + point2.rawX) / 2F,
            (point1.rawY + point2.rawY) / 2F
        )
    }

    fun resizeRect(rawX: Float, rawY: Float) {
        if (displayRectIsInitialized()) {
            val currentTouchPoint = CropPoint(rawX, rawY).transform(displayRect)
            getControlPointList(currentTouchPoint)
                .minByOrNull { pair -> pair.second }
                ?.let { nearest_pair ->
                    if (nearest_pair.second < 6000f) {
                        when (nearest_pair.first) {
                            leftCenter -> {
                                val moveX = currentTouchPoint.rawX - leftCenter.rawX
                                val moveY = currentTouchPoint.rawY - leftCenter.rawY

                                if (touchDown.isInside(displayRect, moveX, moveY) &&
                                    bottomLeft.isInside(displayRect, moveX, moveY) &&
                                    currentTouchPoint.x < rightCenter.x &&
                                    currentTouchPoint.y < bottomCenter.y &&
                                    currentTouchPoint.y > topCenter.y
                                ) {
                                    leftCenter.rawX += moveX
                                    leftCenter.rawY += moveY

                                    touchDown.rawX += moveX
                                    touchDown.rawY += moveY

                                    bottomLeft.rawX += moveX
                                    bottomLeft.rawY += moveY

                                    topCenter.rawX = getCenterOf(touchDown, topRight).rawX
                                    topCenter.rawY = getCenterOf(touchDown, topRight).rawY

                                    bottomCenter.rawX = getCenterOf(bottomLeft, releasePoint).rawX
                                    bottomCenter.rawY = getCenterOf(bottomLeft, releasePoint).rawY
                                } else {
                                    return
                                }
                            }
                            topCenter -> {
                                val moveX = currentTouchPoint.rawX - topCenter.rawX
                                val moveY = currentTouchPoint.rawY - topCenter.rawY

                                if (touchDown.isInside(displayRect, moveX, moveY) &&
                                    topRight.isInside(displayRect, moveX, moveY) &&
                                    currentTouchPoint.y < bottomCenter.y &&
                                    currentTouchPoint.x > leftCenter.x &&
                                    currentTouchPoint.y < rightCenter.y
                                ) {
                                    topCenter.rawX += moveX
                                    topCenter.rawY += moveY

                                    topRight.rawX += moveX
                                    topRight.rawY += moveY

                                    touchDown.rawX += moveX
                                    touchDown.rawY += moveY

                                    rightCenter.rawX = getCenterOf(releasePoint, topRight).rawX
                                    rightCenter.rawY = getCenterOf(releasePoint, topRight).rawY

                                    leftCenter.rawX = getCenterOf(touchDown, bottomLeft).rawX
                                    leftCenter.rawY = getCenterOf(touchDown, bottomLeft).rawY
                                } else {
                                    return
                                }
                            }
                            rightCenter -> {
                                val moveX = currentTouchPoint.rawX - rightCenter.rawX
                                val moveY = currentTouchPoint.rawY - rightCenter.rawY

                                if (topRight.isInside(displayRect, moveX, moveY) &&
                                    releasePoint.isInside(displayRect, moveX, moveY) &&
                                    currentTouchPoint.x > leftCenter.x &&
                                    currentTouchPoint.y < bottomCenter.y &&
                                    currentTouchPoint.y > topCenter.y
                                ) {
                                    rightCenter.rawX += moveX
                                    rightCenter.rawY += moveY

                                    releasePoint.rawX += moveX
                                    releasePoint.rawY += moveY

                                    topRight.rawX += moveX
                                    topRight.rawY += moveY

                                    bottomCenter.rawX = getCenterOf(releasePoint, bottomLeft).rawX
                                    bottomCenter.rawY = getCenterOf(releasePoint, bottomLeft).rawY

                                    topCenter.rawX = getCenterOf(touchDown, topRight).rawX
                                    topCenter.rawY = getCenterOf(touchDown, topRight).rawY
                                } else {
                                    return
                                }
                            }
                            bottomCenter -> {
                                val moveX = currentTouchPoint.rawX - bottomCenter.rawX
                                val moveY = currentTouchPoint.rawY - bottomCenter.rawY

                                if (releasePoint.isInside(displayRect, moveX, moveY) &&
                                    bottomLeft.isInside(displayRect, moveX, moveY) &&
                                    currentTouchPoint.y > topCenter.y &&
                                    currentTouchPoint.x > leftCenter.x &&
                                    currentTouchPoint.x < rightCenter.x
                                ) {
                                    bottomCenter.rawX += moveX
                                    bottomCenter.rawY += moveY

                                    bottomLeft.rawX += moveX
                                    bottomLeft.rawY += moveY

                                    releasePoint.rawX += moveX
                                    releasePoint.rawY += moveY

                                    leftCenter.rawX = getCenterOf(touchDown, bottomLeft).rawX
                                    leftCenter.rawY = getCenterOf(touchDown, bottomLeft).rawY

                                    rightCenter.rawX = getCenterOf(releasePoint, topRight).rawX
                                    rightCenter.rawY = getCenterOf(releasePoint, topRight).rawY
                                } else {
                                    return
                                }
                            }
                        }

                        val segmentCenterPoints = getSegmentCenterPoints()
                        val segmentLengths = getAllSegmentLengths()

                        for (i in segmentCenterPoints.indices) {
                            val point = segmentCenterPoints[i]

                            if (nearest_pair.first.rawX == point.rawX && nearest_pair.first.rawY == point.rawY) {
                                val moveX = currentTouchPoint.transform(displayRect).x -
                                        point.transform(displayRect).x
                                val distance1 = segmentLengths[i] + moveX
                                val totalRatio = segmentRatioList[i] + segmentRatioList[i + 1]

                                segmentRatioList[i] =
                                    (distance1 * segmentRatioList.sum()) / segmentLengths.sum()
                                segmentRatioList[i + 1] = totalRatio - segmentRatioList[i]
                                break
                            }
                        }
                    }
                    center = getCropCenter()
                    getAbsoluteCropPoints()
                }
        }
    }

    private fun getAllSegmentLengths(): List<Float> {
        val lengths = mutableListOf<Float>()
        val totalLength = CropPoint.diagonalDistance(leftCenter, rightCenter, displayRect)
        segmentRatioList.forEach {
            val distance = (totalLength * it) / segmentRatioList.sum()
            lengths.add(distance)
        }
        return lengths
    }

    private fun getControlPointList(touchPoint: CropPoint): List<Pair<CropPoint, Float>> {
        val controlPoints = mutableListOf<Pair<CropPoint, Float>>()
        controlPoints.add(
            Pair(
                topCenter.transform(displayRect),
                lengthSquare(touchPoint, topCenter)
            )
        )
        controlPoints.add(
            Pair(
                leftCenter.transform(displayRect),
                lengthSquare(touchPoint, leftCenter)
            )
        )
        controlPoints.add(
            Pair(
                rightCenter.transform(displayRect),
                lengthSquare(touchPoint, rightCenter)
            )
        )
        controlPoints.add(
            Pair(
                bottomCenter.transform(displayRect),
                lengthSquare(touchPoint, bottomCenter)
            )
        )
        getSegmentCenterPoints().forEach { segmentCenter ->
            controlPoints.add(
                Pair(
                    segmentCenter.transform(displayRect),
                    lengthSquare(touchPoint, segmentCenter)
                )
            )
        }
        Timber.e("${controlPoints.size}")
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
        points.add(topRight)
        points.add(releasePoint)
        points.add(bottomLeft)
        return points
    }

    fun getAbsoluteCropPoints(): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        if (displayRectIsInitialized()) {
            points.add(touchDown.absolute(displayRect))
            points.add(topRight.absolute(displayRect))
            points.add(releasePoint.absolute(displayRect))
            points.add(bottomLeft.absolute(displayRect))
        }
        return points
    }

    private fun displayRectIsInitialized() = this@SplitCropping::displayRect.isInitialized

    fun getSegmentPoints(): List<Pair<CropPoint, CropPoint>> {
        val segmentPoints = mutableListOf<Pair<CropPoint, CropPoint>>()
        var segment = segmentRatioList.firstOrNull() ?: 0f

        for (i in 1 until segmentRatioList.size) {
            val point1 = subPoint(
                CropPoint(touchDown.rawX, touchDown.rawY),
                CropPoint(topRight.rawX, topRight.rawY),
                segment,
                segmentRatioList.size
            ).transform(displayRect)
            val point2 = subPoint(
                CropPoint(bottomLeft.rawX, bottomLeft.rawY),
                CropPoint(releasePoint.rawX, releasePoint.rawY),
                segment,
                segmentRatioList.size
            ).transform(displayRect)
            segmentPoints.add(Pair(point1, point2))
            segment += segmentRatioList[i]
        }
        return segmentPoints
    }

    private fun getSegmentCenterPoints(): List<CropPoint> {
        val points = mutableListOf<CropPoint>()
        getSegmentPoints().forEach {
            points.add(getCenterOf(it.first, it.second))
        }
        return points
    }

    private fun subPoint(
        startPoint: CropPoint,
        endPoint: CropPoint,
        segment: Float,
        totalSegments: Int
    ): CropPoint {
        val midX = (startPoint.rawX + ((endPoint.rawX - startPoint.rawX) / totalSegments) * segment)
        val midY = (startPoint.rawY + ((endPoint.rawY - startPoint.rawY) / totalSegments) * segment)

        return CropPoint(midX, midY)
    }

    fun setOtherPoints(topRight: CropPoint, bottomLeft: CropPoint) {
        this.topRight = topRight
        this.bottomLeft = bottomLeft
        updateCenterPoints()
    }

    private fun updateCenterPoints() {
        leftCenter = getCenterOf(touchDown, bottomLeft)
        topCenter = getCenterOf(touchDown, topRight)
        rightCenter = getCenterOf(releasePoint, topRight)
        bottomCenter = getCenterOf(bottomLeft, releasePoint)
    }

    fun resetSegmentRatio() {
        for (i in segmentRatioList.indices) {
            segmentRatioList[i] = 1f
        }
    }

    fun getSegmentCrop(
        i: Int, segmentPoint: List<Pair<CropPoint, CropPoint>>, size: Int
    ): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        if (displayRectIsInitialized()) {
            when (i) {
                0 -> {
                    points.add(touchDown.absolute(displayRect))
                    points.add(segmentPoint[i].first.absolute(displayRect))
                    points.add(segmentPoint[i].second.absolute(displayRect))
                    points.add(bottomLeft.absolute(displayRect))
                }
                size -> {
                    points.add(segmentPoint[i - 1].first.absolute(displayRect))
                    points.add(topRight.absolute(displayRect))
                    points.add(releasePoint.absolute(displayRect))
                    points.add(segmentPoint[i - 1].second.absolute(displayRect))
                }
                else -> {
                    points.add(segmentPoint[i].first.absolute(displayRect))
                    points.add(segmentPoint[i - 1].first.absolute(displayRect))
                    points.add(segmentPoint[i - 1].second.absolute(displayRect))
                    points.add(segmentPoint[i].second.absolute(displayRect))
                }
            }
        }
        return points
    }
}