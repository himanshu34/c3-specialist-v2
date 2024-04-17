package co.nayan.c3views.polygon

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationValue
import co.nayan.c3views.DrawListener
import co.nayan.c3views.R
import co.nayan.c3views.ZoomView
import com.github.chrisbanes.photoview.PhotoView
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import kotlin.math.abs

class PolygonPhotoView(context: Context, attrs: AttributeSet?) : PhotoView(context, attrs) {

    private var canRedo: Boolean = false
    private var lastPoint: PointF? = null
    private var drawPolygon: Boolean = false

    val points: MutableList<PointF> = mutableListOf()
    private val pointsPaint = Paint()
    private val linePaint = Paint()
    private val polyPaint = Paint()

    private val hintPointsPaint = Paint()
    private val hintLinePaint = Paint()
    private val hintPolyPaint = Paint()

    private var zoomView: co.nayan.c3views.ZoomView? = null
    private val zoomPoint: PointF = PointF(0f, 0f)
    private var isZooming = false

    private val polyPath = Path()
    private val hintPolyPath = Path()
    private var polygonInteractionInterface: PolygonInteractionInterface? = null

    var showHint: Boolean = false
    private val hintData = mutableListOf<PointF>()

    private val drawListener = object : DrawListener {

        override fun touchDownPoint(rawX: Float, rawY: Float, x: Float, y: Float) {
            polygonInteractionInterface?.setUpZoomView(true, x, y)
            zoomPoint.x = rawX
            zoomPoint.y = rawY
            isZooming = true
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
            isZooming = false
            polygonInteractionInterface?.setUpZoomView(false, x, y)

            if (validPoints(PointF(x, y), displayRect)) {
                checkExistingPoints(PointF(rawX, rawY))
                polygonInteractionInterface?.onDraw()
            }
            polygonInteractionInterface?.updatePoints()
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
            polygonInteractionInterface?.setUpZoomView(true, x, y)
            zoomPoint.x = rawX
            zoomPoint.y = rawY
            isZooming = true

            invalidate()
        }

        override fun unselect() {
            //Will be implemented if needed
        }

        override fun hideZoomView() {
            polygonInteractionInterface?.setUpZoomView(false, 0f, 0f)
        }
    }

    private val polygonPhotoViewAttacher =
        co.nayan.c3views.CanvasPhotoViewAttacher(this, drawListener)

    init {
        pointsPaint.style = Paint.Style.FILL
        pointsPaint.color = ContextCompat.getColor(context, R.color.polygonGradient)
        pointsPaint.strokeWidth = 1F
        linePaint.style = Paint.Style.STROKE
        linePaint.color = ContextCompat.getColor(context, R.color.polygonGradient)
        linePaint.strokeWidth = 5F
        polyPaint.color = ContextCompat.getColor(context, R.color.polygonGradient)
        polyPaint.style = Paint.Style.FILL

        hintPointsPaint.style = Paint.Style.FILL
        hintPointsPaint.color = ContextCompat.getColor(context, R.color.hint_overlay)
        hintPointsPaint.strokeWidth = 1F
        hintLinePaint.style = Paint.Style.STROKE
        hintLinePaint.color = ContextCompat.getColor(context, R.color.hint_overlay)
        hintLinePaint.strokeWidth = 5F
        hintPolyPaint.color = ContextCompat.getColor(context, R.color.hint_overlay)
        hintPolyPaint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        zoomView?.let {
            it.setZooming(isZooming, zoomPoint, polygonPhotoViewAttacher.scale)
            it.invalidate()
        }

        if (points.isEmpty()) return

        drawPolygon = if (points.size < 3) false
        else points.first() == points.last()

        polyPath.reset()
        polygonPhotoViewAttacher.displayRect?.let { imageRect ->
            polyPath.moveTo(
                points[0].x * imageRect.width() + imageRect.left,
                points[0].y * imageRect.height() + imageRect.top
            )

            for (point in points) {
                if (!drawPolygon) {
                    canvas.drawCircle(
                        point.x * imageRect.width() + imageRect.left,
                        point.y * imageRect.height() + imageRect.top,
                        10f, pointsPaint
                    )
                }

                polyPath.lineTo(
                    point.x * imageRect.width() + imageRect.left,
                    point.y * imageRect.height() + imageRect.top
                )
            }
        }

        if (drawPolygon) canvas.drawPath(polyPath, polyPaint)
        else canvas.drawPath(polyPath, linePaint)

        if (showHint) {
            polygonPhotoViewAttacher.displayRect?.let { imageRect ->
                hintPolyPath.reset()
                hintPolyPath.moveTo(
                    hintData[0].x * imageRect.width() + imageRect.left,
                    hintData[0].y * imageRect.height() + imageRect.top
                )

                for (point in hintData) {
                    canvas.drawCircle(
                        point.x * imageRect.width() + imageRect.left,
                        point.y * imageRect.height() + imageRect.top,
                        10f, hintPointsPaint
                    )

                    hintPolyPath.lineTo(
                        point.x * imageRect.width() + imageRect.left,
                        point.y * imageRect.height() + imageRect.top
                    )
                }
            }
        }
    }

    fun checkExistingPoints(point: PointF): Boolean {
        if (points.size > 1) {
            if (abs(width * points.first().x - width * point.x) < 10
                && abs(width * points.first().y - width * point.y) < 10
            ) {
                drawPolygon = true
                points.add(points[0])
                return false
            }
        }
        points.add(point)
        polygonInteractionInterface?.onDraw()
        drawPolygon = false
        return true
    }

    fun undo() {
        if (points.size > 0) {
            lastPoint = points.last()

            points.removeAt(points.lastIndex)

            canRedo = true
            if (points.size > 1) {
                drawPolygon = points.first() == points[points.size - 1]
            }
            invalidate()
        } else {
            lastPoint = null
        }

        polygonInteractionInterface?.onDraw()
    }

    fun reset() {
        points.clear()
        canRedo = false
        lastPoint = null
    }

    fun resetScale() {
        polygonPhotoViewAttacher.scale = 1.0F
    }

    fun setZoomView(zoomView: ZoomView) {
        this.zoomView = zoomView
    }

    private fun validPoints(point: PointF, displayRect: RectF): Boolean {
        return point.x > displayRect.left &&
                point.x < displayRect.right &&
                point.y > displayRect.top &&
                point.y < displayRect.bottom
    }

    fun setPolygonInteractionInterface(toSet: PolygonInteractionInterface) {
        polygonInteractionInterface = toSet
    }

    fun getPolygon(bitmapWidth: Float, bitmapHeight: Float): List<AnnotationObjectsAttribute> {
        val isValidPolygon = points.size > 3 && points.first() == points.last()
        return if (isValidPolygon) {
            val coordinates = mutableListOf<ArrayList<Float>>()
            points.forEach { point ->
                coordinates.add(
                    arrayListOf(bitmapWidth * point.x, bitmapHeight * point.y)
                )
            }
            val data = AnnotationData(type = DrawType.POLYGON, points = coordinates)
            val gson =
                GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create()
            val answer = gson.toJson(data)
            return listOf(AnnotationObjectsAttribute(AnnotationValue(answer = answer)))
        } else {
            emptyList()
        }
    }

    fun editMode(isEnabled: Boolean) {
        polygonPhotoViewAttacher.setEditMode(isEnabled)
    }

    fun touchEnabled(isEnabled: Boolean) {
        polygonPhotoViewAttacher.setTouchEnabled(isEnabled)
    }

    fun setOverlayColor(color: String) {
        polyPaint.color = Color.parseColor(color)
    }

    fun addHintData(toAdd: List<PointF>) {
        hintData.clear()
        hintData.addAll(toAdd)
    }
}

interface PolygonInteractionInterface {
    fun onDraw()
    fun setUpZoomView(isShowing: Boolean, x: Float, y: Float)
    fun updatePoints()
}