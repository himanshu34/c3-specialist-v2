package co.nayan.c3views.paint

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3views.R
import com.github.chrisbanes.photoview.OnScaleChangedListener
import com.github.chrisbanes.photoview.PhotoView
import timber.log.Timber
import java.util.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

class PaintPhotoView(context: Context, attrs: AttributeSet?) : PhotoView(context, attrs) {

    var strokeWidth = 10f
    var thicknessRatio = 10f

    var isCurveSelectedForConnectedLines: Boolean = false
    private var hideSelectedStroke: Boolean = false
    private var paintClassificationInterface: PaintClassificationInterface? = null
    private var inSingleSelectionMode: Boolean = false
    private var lineDrawnListener: LineDrawnListener? = null
    var isLineInEditMode: Boolean = false
    var lineCurvePoint: List<Float> = listOf()

    // Default paint instance
    private val textPaint = Paint()
    private val bgTextPaint = Paint()

    private var bitmapWidth: Float? = null
    private var bitmapHeight: Float? = null

    private var zoomView: co.nayan.c3views.ZoomView? = null
    private val zoomPoint: PointF = PointF(0f, 0f)
    private var isZooming = false
    var showHint: Boolean = false

    // Data list for all drawings
    val paintDataList: MutableList<PaintData> = mutableListOf()

    private val hintData: MutableList<PaintData> = mutableListOf()

    // For maintaining user operations for UNDO feature
    private val paintDataOperations: MutableList<PaintDataOperation> = mutableListOf()

    // Current active draw data
    var stroke =
        PaintData(
            mutableListOf(),
            DrawType.CONNECTED_LINE,
            paintGenerator(),
            thicknessRatio = thicknessRatio
        )

    private val scaleChangeListener = OnScaleChangedListener { _, _, _ ->
        strokeWidth = getStrokeWidth(stroke.thicknessRatio)
        invalidate()
        Timber.e("Scale :${paintViewAttacher.scale}, $strokeWidth")
    }

    private val touchListener = object : PaintTouchListener {
        override fun touchDownPoint(rawX: Float, rawY: Float, x: Float, y: Float) {
            lineDrawnListener?.onTouch()
            lineDrawnListener?.showZoom(true, x, y)
            zoomPoint.x = rawX
            zoomPoint.y = rawY
            isZooming = true
            invalidate()
        }

        override fun releasePoint(rawX: Float, rawY: Float, x: Float, y: Float) {
            lineDrawnListener?.showZoom(false, x, y)
            isZooming = false
            invalidate()
        }

        override fun movePoint(rawX: Float, rawY: Float, x: Float, y: Float) {
            lineDrawnListener?.showZoom(true, x, y)
            zoomPoint.x = rawX
            zoomPoint.y = rawY
            isZooming = true
            invalidate()
        }
    }

    private val onPaintDrawListener = object : PaintDrawListener {
        override fun discardStroke() {
            if (stroke.type == DrawType.PENCIL) {
                stroke = PaintData(
                    mutableListOf(),
                    stroke.type,
                    paintGenerator(),
                    thicknessRatio = stroke.thicknessRatio
                )
            }
        }

        override fun touchPoints(x: Float, y: Float, isDragging: Boolean) {
            var shouldAddPoint = true
            if (stroke.type == DrawType.CONNECTED_LINE && stroke.points.size in listOf(2, 3)) {
                if (isLineInEditMode) {
                    val touchPoint = listOf(x, y)
                    val thresholdX = 50 / paintViewAttacher.displayRect.width()
                    val thresholdY = 50 / paintViewAttacher.displayRect.height()

                    if (stroke.connectedPoints.size == 0) {
                        if (calculateDistance(touchPoint, stroke.points[1]) < thresholdX ||
                            calculateDistance(touchPoint, stroke.points[1]) < thresholdY
                        ) {
                            stroke.points.removeAt(1)
                        } else if (calculateDistance(touchPoint, stroke.points[0]) < thresholdX ||
                            calculateDistance(touchPoint, stroke.points[0]) < thresholdY
                        ) {
                            stroke.points[0] = stroke.points[1]
                            stroke.points.removeAt(1)
                        } else {
                            if (isCurveSelectedForConnectedLines) {
                                lineCurvePoint = listOf(x, y)
                            }
                            shouldAddPoint = false
                        }
                    } else {
                        val firstPoint = stroke.connectedPoints.first()
                        if (isDragging.not() &&
                            calculateDistance(touchPoint, firstPoint) < thresholdX ||
                            calculateDistance(touchPoint, firstPoint) < thresholdY
                        ) {
                            val points = stroke.connectedPoints.reversed()
                            stroke.connectedPoints.clear()
                            stroke.connectedPoints.addAll(points)
                            stroke.points.clear()
                            stroke.points.add(stroke.connectedPoints.last())
                            shouldAddPoint = false
                        } else {
                            if (calculateDistance(touchPoint, stroke.points[1]) < thresholdX ||
                                calculateDistance(touchPoint, stroke.points[1]) < thresholdY
                            ) {
                                stroke.points.removeAt(1)
                            } else {
                                if (isCurveSelectedForConnectedLines) {
                                    lineCurvePoint = listOf(x, y)
                                }
                                shouldAddPoint = false
                            }
                        }
                    }
                } else {
                    // If Line tool is selected then just remove the releasePoint point and add the latest one later
                    stroke.points.removeAt(1)
                }
                invalidate()

            } else if (stroke.type == DrawType.SELECT) {
                // If Select tool is selected then just remove all points and add the latest one later
                stroke.points.clear()
            }

            if (shouldAddPoint) {
                // Adding points to the current active draw data
                stroke.points.add(listOf(x, y))
                if (isLineInEditMode) {
                    updateLineCurvePoints(isCurveSelectedForConnectedLines.not())
                } else {
                    updateLineCurvePoints(true)
                }
            }

            // If the Select tool is selected then do not invalidate the image
            if (stroke.type != DrawType.SELECT) {
                invalidate()
            }
        }

        override fun closeStroke() {
            if (stroke.type == DrawType.SELECT) {
                val selectedStroke = selectStroke()
                if (selectedStroke != null) {
                    strokeWidth = getStrokeWidth(selectedStroke.thicknessRatio)
                    paintClassificationInterface?.selectedStroke(selectedStroke)
                }

                stroke = PaintData(
                    mutableListOf(),
                    stroke.type,
                    paintGenerator(),
                    thicknessRatio = stroke.thicknessRatio
                )
            } else {
                // Add current drawn line to data list only if the line is a valid Line
                if (stroke.isValidLine(bitmapWidth ?: 0f, bitmapHeight ?: 0f)) {
                    isLineInEditMode = true
                    val x1 = stroke.points.first().first()
                    val x2 = stroke.points.last().first()
                    val y1 = stroke.points.first().last()
                    val y2 = stroke.points.last().last()
                    if (lineCurvePoint.isEmpty()) {
                        lineCurvePoint = listOf((x1 + x2) / 2, (y1 + y2) / 2)
                    }
                    lineDrawnListener?.onConnectedLineDrawn()
                }
            }
        }

        override fun hideZoomView() {
            lineDrawnListener?.showZoom(false, 0f, 0f)
        }
    }

    fun updateLineCurvePoints(shouldUpdate: Boolean = true) {
        if (stroke.type == DrawType.CONNECTED_LINE && stroke.points.isNotEmpty() && shouldUpdate) {
            val x1 = stroke.points.first().first()
            val x2 = stroke.points.last().first()
            val y1 = stroke.points.first().last()
            val y2 = stroke.points.last().last()
            lineCurvePoint = listOf((x1 + x2) / 2, (y1 + y2) / 2)
        }
    }

    private val paintViewAttacher: PaintViewAttacher =
        PaintViewAttacher(this, onPaintDrawListener, touchListener)

    private fun selectStroke(): PaintData? {
        var selectedStroke: PaintData? = null
        // If Select tool is selected and the point selected is out of view bounds then do not execute further
        if (stroke.points.isEmpty()) {
            return selectedStroke
        }

        var found = false

        // For Line drawing data
        val thresholdLineX = 10 / paintViewAttacher.displayRect.width()
        val thresholdLineY = 10 / paintViewAttacher.displayRect.height()

        // Check if the currently taped coordinates are contained under a threshold limit of any drawn points
        for (data in paintDataList) {
            when (data.type) {
                DrawType.CONNECTED_LINE -> {
                    for (index in 0 until data.points.lastIndex) {
                        // Touch coordinates
                        val touchX = stroke.points[0].first()
                        val touchY = stroke.points[0].last()
                        // Point 1 coordinates
                        val x1 = data.points[index].first()
                        val y1 = data.points[index].last()
                        // Point 2 coordinates
                        val x2 = data.points[index + 1].first()
                        val y2 = data.points[index + 1].last()

                        // Find Area of triangle formed by given three points
                        val area =
                            abs((touchX * (y1 - y2) + x1 * (y2 - touchY) + x2 * (touchY - y1)) / 2)

                        // If the angle at touch point is an obtuse angle and
                        // area of triangle formed is under the threshold limit,
                        // then the current line is found to be selected
                        if (calculateAngle(
                                stroke.points[0],
                                data.points[index],
                                data.points[index + 1]
                            ) > 90 &&
                            (area in -thresholdLineX..thresholdLineX ||
                                    area in -thresholdLineY..thresholdLineY)
                        ) {
                            data.isSelected = data.isSelected.not()
                            found = true

                            break
                        }
                    }
                    val isLineSelected = paintDataList.any { it.isSelected }
                    lineDrawnListener?.onLineSelected(isLineSelected)
                }
            }
            // If a selected line is found then just break the loop
            if (found) {
                selectedStroke = data
                break
            }
        }
        // If found a drawing data invalidate the view
        if (found) {
            invalidate()
        }
        return selectedStroke
    }

    init {
        textPaint.style = Paint.Style.STROKE
        textPaint.color = ContextCompat.getColor(context, R.color.translucentOverlay)
        textPaint.strokeWidth = 2f
        textPaint.textSize = 40f
        bgTextPaint.style = Paint.Style.FILL_AND_STROKE
        bgTextPaint.color = ContextCompat.getColor(context, R.color.white)
        bgTextPaint.strokeWidth = 4f
        bgTextPaint.textSize = 40f

        paintViewAttacher.setOnScaleChangeListener(scaleChangeListener)
        paintViewAttacher.scaleType = ScaleType.FIT_CENTER
    }

    fun setBitmapAttributes(width: Int, height: Int) {
        bitmapWidth = width.toFloat()
        bitmapHeight = height.toFloat()
    }

    private fun getStrokeWidth(thicknessRatio: Float?): Float {
        return (thicknessRatio ?: 10f) * paintViewAttacher.scale
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        zoomView?.let {
            it.setZooming(isZooming, zoomPoint, paintViewAttacher.scale)
            it.invalidate()
        }

        drawLinesCount(canvas)

        if (stroke.points.size > 0) {
            if (isLineInEditMode && lineCurvePoint.isNotEmpty()) {
                drawCircleAtLineEnds(stroke, canvas, lineCurvePoint)
                drawCircleAtConnectedLineEnds(stroke.points, canvas)
            }
            if (lineCurvePoint.isNotEmpty()) {
                val points = stroke.points
                val linePoints = mutableListOf(points.first(), lineCurvePoint, points.last())
                editPaint.strokeWidth = getStrokeWidth(stroke.thicknessRatio)
                canvas.drawPath(drawCurveLine(linePoints), editPaint)
            }
            if (stroke.connectedPoints.size > 0) {
                editPaint.strokeWidth = getStrokeWidth(stroke.thicknessRatio)
                canvas.drawPath(drawConnectedLine(stroke.connectedPoints), editPaint)
                val points = stroke.points
                drawCircleAtConnectedLineEnds(points, canvas)
            }
        }

        for (stroke in paintDataList) {
            val paintForStroke = if (stroke.isSelected) {
                selectedPaint.strokeWidth = getStrokeWidth(stroke.thicknessRatio)
                selectedPaint
            } else {
                stroke.paint.strokeWidth = getStrokeWidth(stroke.thicknessRatio)
                stroke.paint
            }

            when (stroke.type) {
                DrawType.CONNECTED_LINE -> {
                    canvas.drawPath(
                        drawConnectedLine(
                            stroke.points
                        ), paintForStroke
                    )
                }
                else -> {
                    canvas.drawPath(
                        drawStroke(stroke.points),
                        paintForStroke
                    )
                }
            }
        }

        if (showHint) {
            for (stroke in hintData) {
                val paintForStroke = hintPaint
                hintPaint.strokeWidth = getStrokeWidth(stroke.thicknessRatio)

                when (stroke.type) {
                    DrawType.CONNECTED_LINE -> {
                        canvas.drawPath(
                            drawConnectedLine(
                                stroke.points
                            ), paintForStroke
                        )
                    }
                    else -> {
                        canvas.drawPath(
                            drawStroke(stroke.points),
                            paintForStroke
                        )
                    }
                }
            }
        }
    }

    private val selectedPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.selectedOverlay)
    }
    private val editPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.edit_overlay_color)
    }
    private val endPointsPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.endPointsOverlay)
    }
    private val hintPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.hint_overlay)
    }

    fun paintGenerator(): Paint {
        val paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.color = getRandomHexCode()
        return paint
    }

    private fun getRandomHexCode(): Int {
        val random = Random()
        val randNum = random.nextInt(0xffffff + 1)
        return Color.parseColor(String.format("#%06x", randNum))
    }

    /*****
     * This method converts a points array into Path object (Stroke)
     * @param points : an array of all the points constituting a stroke
     * @return Path object
     */
    private fun drawStroke(points: MutableList<List<Float>>): Path {
        val stroke = Path()
        val rect = paintViewAttacher.displayRect

        if (points.isEmpty() || rect == null) {
            return stroke
        }
        stroke.moveTo(
            rect.left + rect.width() * points[0].first(),
            rect.top + rect.height() * points[0].last()
        )
        for (i in 1 until points.size) {
            stroke.lineTo(
                rect.left + rect.width() * points[i].first(),
                rect.top + rect.height() * points[i].last()
            )
        }
        return stroke
    }

    private fun drawCurveLine(points: MutableList<List<Float>>): Path {
        val stroke = Path()
        val rect = paintViewAttacher.displayRect

        if (points.isEmpty() || rect == null) {
            return stroke
        }
        stroke.moveTo(
            rect.left + rect.width() * points[0].first(),
            rect.top + rect.height() * points[0].last()
        )

        stroke.cubicTo(
            rect.left + rect.width() * points[0].first(),
            rect.top + rect.height() * points[0].last(),
            rect.left + rect.width() * points[1].first(),
            rect.top + rect.height() * points[1].last(),
            rect.left + rect.width() * points[2].first(),
            rect.top + rect.height() * points[2].last()
        )
        return stroke
    }

    private fun drawConnectedLine(points: MutableList<List<Float>>): Path {
        val stroke = Path()
        val rect = paintViewAttacher.displayRect

        if (points.isEmpty() || rect == null) {
            return stroke
        }
        stroke.moveTo(
            rect.left + rect.width() * points[0].first(),
            rect.top + rect.height() * points[0].last()
        )
        var index = 0
        while (index < points.size - 2) {
            stroke.cubicTo(
                rect.left + rect.width() * points[index].first(),
                rect.top + rect.height() * points[index].last(),
                rect.left + rect.width() * points[index + 1].first(),
                rect.top + rect.height() * points[index + 1].last(),
                rect.left + rect.width() * points[index + 2].first(),
                rect.top + rect.height() * points[index + 2].last()
            )
            index += 2
        }
        return stroke
    }

    private fun drawCircleAtConnectedLineEnds(points: MutableList<List<Float>>, canvas: Canvas) {
        endPointsPaint.strokeWidth = getStrokeWidth(stroke.thicknessRatio)
        paintViewAttacher.displayRect?.let { rect ->
            if (points.isEmpty()) {
                return
            }

            canvas.drawCircle(
                rect.left + rect.width() * points.last().first(),
                rect.top + rect.height() * points.last().last(),
                10f,
                endPointsPaint
            )
        }
    }

    private fun drawCircleAtLineEnds(
        paintData: PaintData, canvas: Canvas, lineCurvePoint: List<Float>
    ) {
        endPointsPaint.strokeWidth = getStrokeWidth(stroke.thicknessRatio)
        paintViewAttacher.displayRect?.let { rect ->
            if (paintData.points.isEmpty() || paintData.isValidLine(
                    bitmapWidth ?: 0f,
                    bitmapHeight ?: 0f
                ).not()
            ) {
                return
            }

            canvas.drawCircle(
                rect.left + rect.width() * paintData.points[1].first(),
                rect.top + rect.height() * paintData.points[1].last(),
                10f,
                endPointsPaint
            )

            if (stroke.connectedPoints.size < 3) {
                canvas.drawCircle(
                    rect.left + rect.width() * paintData.points[0].first(),
                    rect.top + rect.height() * paintData.points[0].last(),
                    10f,
                    endPointsPaint
                )
            }
            if (isCurveSelectedForConnectedLines) {
                canvas.drawCircle(
                    rect.left + rect.width() * lineCurvePoint.first(),
                    rect.top + rect.height() * lineCurvePoint.last(),
                    10f,
                    endPointsPaint
                )
            }
        }
    }

    private fun drawLinesCount(canvas: Canvas) {
        paintViewAttacher.displayRect?.let { rect ->
            canvas.drawText("${paintDataList.size} Lines", rect.left + 40f, rect.top + 40f, bgTextPaint)
        }
    }

    /****
     * Clear a recent stroke from bitmap canvas
     */
    fun undo() {
        if (paintDataList.isNotEmpty()) {
            paintDataList.remove(paintDataList.last())
            invalidate()
        }
    }

    fun reset() {
        paintDataList.clear()
        paintDataOperations.clear()
    }

    fun resetScale() {
        paintViewAttacher.scale = 1F
    }

    fun setStrokeMode(drawType: String) {
        paintViewAttacher.setSelectModeEnabled(drawType == DrawType.SELECT)
        stroke =
            PaintData(mutableListOf(), drawType, paintGenerator(), thicknessRatio = thicknessRatio)
    }

    fun updateThicknessRatio() {
        val scale = paintViewAttacher.scale
        thicknessRatio = strokeWidth / scale
        if (stroke.type == DrawType.SELECT) {
            paintDataList.forEach {
                if (it.isSelected) {
                    it.thicknessRatio = thicknessRatio
                }
            }
        } else {
            stroke.thicknessRatio = thicknessRatio
        }
        Timber.e("thickness : ${stroke.thicknessRatio}")
        invalidate()
    }

    private fun lengthSquare(p1: List<Float>, p2: List<Float>): Float {
        val xDiff = p1.first() - p2.first()
        val yDiff = p1.last() - p2.last()
        return xDiff * xDiff + yDiff * yDiff
    }

    private fun calculateDistance(tapPoint: List<Float>, point: List<Float>): Float {
        val tx = tapPoint.first()
        val ty = tapPoint.last()
        val x = point.first()
        val y = point.last()

        val dx = tx - x
        val dy = ty - y
        //equation constants

        return sqrt(dx * dx + dy * dy)
    }

    private fun calculateAngle(
        tapPoint: List<Float>, linePoint1: List<Float>, linePoint2: List<Float>
    ): Float {
        // Square of lengths be a2, b2, c2
        val a2 = lengthSquare(linePoint1, linePoint2)
        val b2 = lengthSquare(tapPoint, linePoint2)
        val c2 = lengthSquare(tapPoint, linePoint1)

        // length of sides be b, c
        val b = sqrt(b2)
        val c = sqrt(c2)

        // From Cosine law
        var alpha = acos((b2 + c2 - a2) / (2f * b * c))

        // Converting to degree
        alpha = (alpha * 180 / PI).toFloat()

        return alpha
    }

    fun deleteSelectedStrokes() {
        val deletedStrokes: MutableList<PaintData> = mutableListOf()
        for (paintData in paintDataList) {
            if (paintData.isSelected) {
                deletedStrokes.add(paintData)
            }
        }
        paintDataList.removeAll(deletedStrokes)
        invalidate()
    }

    fun cancelSelection() {
        for (paintData in paintDataList) {
            if (paintData.isSelected) {
                paintData.isSelected = false
            }
        }
        invalidate()
    }

    fun closeCurrentLineStroke(isNewLineAdded: Boolean = false) {
        if (isNewLineAdded) {
            if (lineCurvePoint.isNotEmpty() && stroke.points.isNotEmpty()) {
                val points = stroke.points
                val linePoints = listOf(points.first(), lineCurvePoint, points.last())
                stroke.points.clear()
                stroke.points.addAll(linePoints)
                lineCurvePoint = listOf()
                if (stroke.connectedPoints.isNotEmpty()) {
                    stroke.connectedPoints.removeAt(stroke.connectedPoints.size - 1)
                }
                stroke.connectedPoints.addAll(linePoints)

                stroke = PaintData(
                    mutableListOf(stroke.connectedPoints.last()),
                    stroke.type,
                    stroke.paint,
                    stroke.connectedPoints,
                    thicknessRatio = stroke.thicknessRatio
                )
                Timber.e("${stroke.points}\n${stroke.connectedPoints}")
                invalidate()
            }
        } else {
            if (stroke.type == DrawType.CONNECTED_LINE) {
                stroke.points.clear()
                lineCurvePoint = listOf()
                stroke.points.addAll(stroke.connectedPoints)
                stroke.connectedPoints.clear()
            }
            if (stroke.points.isNotEmpty()) {
                paintDataList.add(stroke)
                paintDataOperations.add(PaintDataOperation(mutableListOf(stroke), false))
            }
            // Clear all drawn points from current active draw data
            stroke = PaintData(
                mutableListOf(),
                stroke.type,
                paintGenerator(),
                thicknessRatio = stroke.thicknessRatio
            )
            isLineInEditMode = false
            invalidate()
        }
    }

    fun clearCurrentLineStroke() {
        if (stroke.points.isNotEmpty()) {
            stroke = if (stroke.connectedPoints.isNotEmpty()) {
                PaintData(
                    mutableListOf(stroke.connectedPoints.last()),
                    stroke.type,
                    paintGenerator(),
                    stroke.connectedPoints,
                    thicknessRatio = stroke.thicknessRatio
                )
            } else {
                PaintData(
                    mutableListOf(),
                    stroke.type,
                    paintGenerator(),
                    thicknessRatio = stroke.thicknessRatio
                )
            }
            lineCurvePoint = listOf()
            invalidate()
        }
    }

    fun setLineDrawnListener(lineDrawnListener: LineDrawnListener) {
        this.lineDrawnListener = lineDrawnListener
    }

    fun setZoomView(zoomView: co.nayan.c3views.ZoomView) {
        this.zoomView = zoomView
    }

    fun editMode(isEnabled: Boolean) {
        paintViewAttacher.setEditModeEnabled(isEnabled)
    }

    fun touchEnabled(isEnabled: Boolean) {
        paintViewAttacher.isTouchable(isEnabled)
    }

    fun getAnnotatedLines(
        bitmapWidth: Float, bitmapHeight: Float
    ): List<AnnotationObjectsAttribute> {
        val annotationObjectsAttributes = mutableListOf<AnnotationObjectsAttribute>()
        paintDataList.forEach {
            val annotationObjectAttr =
                it.getAnnotationAttribute(bitmapWidth, bitmapHeight)
            annotationObjectsAttributes.add(annotationObjectAttr)
        }
        return annotationObjectsAttributes
    }

    fun addHintData(toAdd: List<PaintData>) {
        hintData.clear()
        hintData.addAll(toAdd)
    }
}

interface LineDrawnListener {
    fun onConnectedLineDrawn()
    fun onLineSelected(isSelected: Boolean)
    fun showZoom(shouldShow: Boolean, x: Float, y: Float)
    fun onTouch()
}