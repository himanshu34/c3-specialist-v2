package co.nayan.c3views.crop

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationState
import co.nayan.c3v2.core.models.Template
import co.nayan.c3views.CanvasPhotoViewAttacher
import co.nayan.c3views.Constants.Dimension.MIN_DIAGONAL_DISTANCE
import co.nayan.c3views.Constants.Dimension.MIN_DIMENSION
import co.nayan.c3views.DrawListener
import co.nayan.c3views.R
import co.nayan.c3views.ZoomView
import co.nayan.c3views.utils.scaleFactor
import com.github.chrisbanes.photoview.PhotoView
import timber.log.Timber

class CropPhotoView(context: Context, attributeSet: AttributeSet?) :
    PhotoView(context, attributeSet) {

    private val croppingPaints = CroppingPaints()

    var showLabel: Boolean = true
    var isAIEnabled: Boolean = false
    private var cropInteractionInterface: CropInteractionInterface? = null
    val crops = mutableListOf<Cropping>()
    private var touchDown = CropPoint(0F, 0F)
    private var cropSelected = false
    var selectedCropIndex: Int = -1
    var selectedCropping: Cropping? = null

    private var isDrawingCrop = false
    private var currentTouchDown = CropPoint(0F, 0F)

    private var zoomView: ZoomView? = null
    private val zoomPoint: PointF = PointF(0f, 0f)
    private var isZooming = false

    private var currentCrop = Cropping(touchDown, currentTouchDown)

    private var bitmapWidth: Float? = null
    private var bitmapHeight: Float? = null

    var maxCrops: Int = MaxCrops.MULTI_CROP

    var showHint: Boolean = false
    private val hintData = mutableListOf<Cropping>()
    var isInLabelingMode: Boolean = false
    var isClassificationMode: Boolean = false
    var selectedLabel: Template? = null

    private var moveStartPoint: CropPoint? = null
    private val detectedEdgePoints = mutableListOf<CropPoint>()

    private val drawListener = object : DrawListener {
        override fun touchDownPoint(rawX: Float, rawY: Float, x: Float, y: Float) {
            Timber.d("touch: ($rawX, $rawY)")
            if (isInLabelingMode.not()) {
                zoomPoint.x = rawX
                zoomPoint.y = rawY
                isZooming = true
                cropInteractionInterface?.showZoom(true, x, y)
                if (cropSelected) {
                    touchDown.clear()
                    moveStartPoint = CropPoint(rawX, rawY)
                } else {
                    crops.forEachIndexed { index, cropping ->
                        selectedLabel?.let {
                            if (it.templateName != cropping.input)
                                return@forEachIndexed
                        }
                        if (CropUtils.isDragging(Pair(x, y), cropping)) {
                            selectedCropIndex = index
                            selectedCropping = cropping
                            cropping.isSelected = true
                            cropSelected = true
                            cropInteractionInterface?.selected(true)
                            return
                        }
                    }
                    if (!cropSelected) {
                        touchDown.rawX = rawX
                        touchDown.rawY = rawY
                    }
                }
                invalidate()
            } else {
                crops.forEachIndexed { index, cropping ->
                    if (CropUtils.isDragging(Pair(x, y), cropping)) {
                        crops.forEach { it.isSelected = false }
                        selectedCropIndex = index
                        selectedCropping = cropping
                        cropping.isSelected = true
                        cropSelected = true
                        cropInteractionInterface?.selected(
                            true,
                            isClassificationMode = isInLabelingMode
                        )
                        invalidate()
                        return
                    }
                }
            }
        }

        override fun releasePoint(
            rawX: Float,
            rawY: Float,
            x: Float,
            y: Float,
            displayRect: RectF,
            scale: Float
        ) {
            Timber.d("release: ($rawX, $rawY)")
            if (isInLabelingMode.not()) {
                cropInteractionInterface?.showZoom(false, x, y)
                val releasePoint = CropPoint(rawX, rawY)
                if (cropSelected) {
                    moveStartPoint?.let {
                        if (validDistance(it, releasePoint, displayRect, scaleFactor(scale)))
                            updateSelectedCrop()
                    }
                } else {
                    if (validDimensions(touchDown, releasePoint, displayRect, scale)) {
                        if (crops.isNotEmpty()) {
                            when (maxCrops) {
                                MaxCrops.CROP -> crops.remove(crops.last())
                                MaxCrops.BINARY_CROP -> {
                                    if (crops.size == 2) crops.remove(crops.last())
                                }
                            }
                        }

                        val rect = Cropping(touchDown.copy(), releasePoint)
                        crops.add(getFinalRect(rect))
                        touchDown.clear()
                        cropInteractionInterface?.onCropDrawn()

                        selectedCropIndex = crops.size - 1
                        selectedCropping = crops[selectedCropIndex]
                        selectedCropping?.isSelected = true
                        cropSelected = true
                        cropInteractionInterface?.selected(
                            true,
                            isClassificationMode = isClassificationMode
                        )
                    }
                }

                selectedCropping?.annotationState = AnnotationState.MANUAL
                selectedCropping?.shouldRemove = false
                cropInteractionInterface?.updateCrops()
                isZooming = false
                isDrawingCrop = false
                invalidate()
            }
        }

        override fun movePoint(
            rawX: Float,
            rawY: Float,
            x: Float,
            y: Float,
            displayRect: RectF,
            scale: Float
        ) {
            if (isInLabelingMode.not()) {
                zoomPoint.x = rawX
                zoomPoint.y = rawY
                isZooming = true

                cropInteractionInterface?.showZoom(true, x, y)
                val currentTouchPoint = CropPoint(rawX, rawY)
                if (cropSelected) {
                    if (selectedCropping?.isMovedAboveThreshold(
                            currentTouchPoint,
                            scaleFactor(scale)
                        ) == true
                    ) selectedCropping?.resizeRect(rawX, rawY, scaleFactor(scale))
                } else {
                    currentTouchDown.rawX = rawX
                    currentTouchDown.rawY = rawY
                    isDrawingCrop = true
                    currentCrop = Cropping(touchDown, currentTouchDown, "")
                }
                invalidate()
            }
        }

        override fun unselect() {
            if (isInLabelingMode.not()) unSelect()
        }

        override fun hideZoomView() {
            cropInteractionInterface?.showZoom(false, 0f, 0f)
        }
    }

    private fun validDistance(
        touchDown: CropPoint,
        releasePoint: CropPoint,
        displayRect: RectF,
        scaleFactor: Float
    ): Boolean {
        val diagonalDistance = CropPoint.diagonalDistance(touchDown, releasePoint, displayRect)
        val distance = CropPoint.distance(touchDown, releasePoint, displayRect)
        return diagonalDistance > (MIN_DIAGONAL_DISTANCE * scaleFactor)
                && distance.first >= (MIN_DIMENSION * scaleFactor)
                && distance.second >= (MIN_DIMENSION * scaleFactor)
    }

    private fun validDimensions(
        touchDown: CropPoint,
        releasePoint: CropPoint,
        displayRect: RectF,
        scale: Float
    ): Boolean {
        return validDistance(touchDown, releasePoint, displayRect, scaleFactor(scale))
                && validPoints(releasePoint, displayRect)
    }

    private fun validPoints(releasePoint: CropPoint, displayRect: RectF): Boolean {
        return releasePoint.x > displayRect.left &&
                releasePoint.x < displayRect.right &&
                releasePoint.y > displayRect.top &&
                releasePoint.y < displayRect.bottom
    }

    private fun updateSelectedCrop() {
        selectedCropping?.apply {
            val crop = getFinalRect(this)
            this.touchDown = crop.touchDown
            this.releasePoint = crop.releasePoint
            this.releaseSymmetric = crop.releaseSymmetric
            this.touchSymmetric = crop.touchSymmetric
        }
    }

    private fun getFinalRect(rect: Cropping): Cropping {
        var finalTouchDownPoint = rect.touchDown.copy()
        var finalReleasePoint = rect.releasePoint.copy()
        var finalTouchSymmetricPoint = rect.touchSymmetric.copy()
        var finalReleaseSymmetricPoint = rect.releaseSymmetric.copy()

        var minDistanceTouchDown = Float.MAX_VALUE
        var minDistanceReleasePoint = Float.MAX_VALUE
        var minDistanceTouchSymmetric = Float.MAX_VALUE
        var minDistanceReleaseSymmetric = Float.MAX_VALUE

        detectedEdgePoints.forEach {
            val distanceTouchDown =
                CropPoint.diagonalDistance(it, rect.touchDown, cropViewAttacher.displayRect)
            if (minDistanceTouchDown > distanceTouchDown && distanceTouchDown < 80) {
                minDistanceTouchDown = distanceTouchDown
                finalTouchDownPoint = it.copy()
            }

            val distanceTouchSymmetric =
                CropPoint.diagonalDistance(it, rect.touchSymmetric, cropViewAttacher.displayRect)
            if (minDistanceTouchSymmetric > distanceTouchSymmetric && distanceTouchSymmetric < 80) {
                minDistanceTouchSymmetric = distanceTouchSymmetric
                finalTouchSymmetricPoint = it.copy()
            }

            val distanceReleasePoint =
                CropPoint.diagonalDistance(it, rect.releasePoint, cropViewAttacher.displayRect)
            if (minDistanceReleasePoint > distanceReleasePoint && distanceReleasePoint < 80) {
                minDistanceReleasePoint = distanceReleasePoint
                finalReleasePoint = it.copy()
            }

            val distanceReleaseSymmetric =
                CropPoint.diagonalDistance(it, rect.releaseSymmetric, cropViewAttacher.displayRect)
            if (minDistanceReleaseSymmetric > distanceReleaseSymmetric && distanceReleaseSymmetric < 80) {
                minDistanceReleaseSymmetric = distanceReleaseSymmetric
                finalReleaseSymmetricPoint = it.copy()
            }
        }

        val diagonalOneMinDistance = minDistanceTouchDown + minDistanceReleasePoint
        val diagonalTwoMinDistance = minDistanceTouchSymmetric + minDistanceReleaseSymmetric

        if (diagonalOneMinDistance > diagonalTwoMinDistance) {
            finalTouchDownPoint.rawX = finalReleaseSymmetricPoint.rawX
            finalTouchDownPoint.rawY = finalTouchSymmetricPoint.rawY
            finalReleasePoint.rawY = finalReleaseSymmetricPoint.rawY
            finalReleasePoint.rawX = finalTouchSymmetricPoint.rawX
            Cropping(
                finalTouchDownPoint,
                finalReleasePoint,
                null
            )
        }

        return Cropping(
            finalTouchDownPoint,
            finalReleasePoint,
            null
        )
    }

    init {
        val innerCropColor = ContextCompat.getColor(context, R.color.crop_inner_overlay)
        val outerCropColor = ContextCompat.getColor(context, R.color.crop_outer_overlay)
        val hintColor = ContextCompat.getColor(context, R.color.hint_overlay)
        val selectedCropColor = ContextCompat.getColor(context, R.color.selected_crop_overlay)
        val aiDrawnInnerCropColor = ContextCompat.getColor(context, R.color.aiInnerEndPointOverlay)
        val aiDrawnOuterCropColor = ContextCompat.getColor(context, R.color.aiOuterEndPointOverlay)
        val innerTaggedCropColor =
            ContextCompat.getColor(context, R.color.crop_tagged_inner_overlay)
        val outerTaggedCropColor =
            ContextCompat.getColor(context, R.color.crop_tagged_outer_overlay)

        croppingPaints.apply {
            innerCropPaint.apply {
                color = innerCropColor
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            outerCropPaint.apply {
                color = outerCropColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            taggedInnerCropPaint.apply {
                color = innerTaggedCropColor
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            taggedOuterCropPaint.apply {
                color = outerTaggedCropColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            movablePointsPaint.apply {
                color = innerCropColor
                strokeWidth = 10f
                style = Paint.Style.STROKE
            }
            dashedLinePaint.apply {
                color = outerCropColor
                strokeWidth = 2f
                style = Paint.Style.STROKE

                val intervals = FloatArray(2)
                intervals[0] = 10.0F
                intervals[1] = 10.0F
                val dashPathEffect = DashPathEffect(intervals, 0F)

                pathEffect = dashPathEffect
            }
            hintPaint.apply {
                color = hintColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            selectedCropPaint.apply {
                color = selectedCropColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            aiDrawnInnerCropPaint.apply {
                color = aiDrawnInnerCropColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            aiDrawnOuterCropPaint.apply {
                color = aiDrawnOuterCropColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
        }

        scaleType = ScaleType.FIT_CENTER
    }

    private val cropViewAttacher = CanvasPhotoViewAttacher(this, listener = drawListener)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        zoomView?.let {
            it.setZooming(isZooming, zoomPoint, cropViewAttacher.scale)
            it.invalidate()
        }

        croppingPaints.setStrokeWidth(strokeWidth())

        val cropsList = selectedLabel?.let { label ->
            crops.filter { label.templateName == it.input }
        } ?: run { crops }
        cropsList.forEach { cropping ->
            cropViewAttacher.displayRect?.let { displayRect ->
                cropping.draw(
                    canvas,
                    displayRect,
                    croppingPaints,
                    isInLabelingMode,
                    showLabel,
                    isAIEnabled
                )
            }
        }

        if (isDrawingCrop && displayRect != null) {
            touchDown.transform(displayRect)
            currentTouchDown.transform(displayRect)
            cropViewAttacher.displayRect?.let { displayRect ->
                currentCrop.draw(
                    canvas,
                    displayRect,
                    croppingPaints,
                    isInLabelingMode,
                    showLabel,
                    isAIEnabled
                )
            }
        }

        if (showHint) {
            hintData.forEach { cropping ->
                cropViewAttacher.displayRect?.let { displayRect ->
                    cropping.draw(
                        canvas,
                        displayRect,
                        croppingPaints,
                        isInLabelingMode,
                        showLabel,
                        isAIEnabled
                    )
                }
            }
        }
    }

    fun reset() {
        crops.clear()
        unSelect()
    }

    fun delete() {
        if (selectedCropIndex != -1 && selectedCropIndex < crops.size) {
            crops[selectedCropIndex].isSelected = false
            crops.removeAt(selectedCropIndex)
            cropSelected = false
            invalidate()
            cropInteractionInterface?.selected(false)
        }
    }

    fun unSelect() {
        if (cropSelected) {
            selectedCropping?.let { it.isSelected = false }
            cropSelected = false
            selectedCropIndex = -1
            cropInteractionInterface?.selected(false)
            invalidate()
        }
    }

    fun undo() {
        if (crops.size > 0) {
            crops.remove(crops.last())
            invalidate()
        }
    }

    fun resetScale() {
        cropViewAttacher.scale = 1.0F
    }

    fun setBitmapAttributes(width: Int, height: Int) {
        bitmapWidth = width.toFloat()
        bitmapHeight = height.toFloat()
    }

    fun setZoomView(toSet: ZoomView) {
        zoomView = toSet
    }

    fun setCropInteractionInterface(toSet: CropInteractionInterface) {
        cropInteractionInterface = toSet
    }

    fun getAnnotatedCrops(
        bitmapWidth: Float,
        bitmapHeight: Float,
        isSubmittingRecord: Boolean
    ): List<AnnotationObjectsAttribute> {
        val annotationObjectsAttributes = mutableListOf<AnnotationObjectsAttribute>()
        crops.forEach {
            if (isSubmittingRecord) it.shouldRemove = false
            annotationObjectsAttributes.add(it.transformWrtBitmap(bitmapWidth, bitmapHeight))
        }
        return annotationObjectsAttributes
    }

    fun editMode(isEnabled: Boolean) {
        cropViewAttacher.setEditMode(isEnabled)
    }

    fun touchEnabled(isEnabled: Boolean) {
        cropViewAttacher.setTouchEnabled(isEnabled)
    }

    fun addHintData(toAdd: List<Cropping>) {
        hintData.clear()
        hintData.addAll(toAdd)
    }

    fun selectNext() {
        var isSelected = false
        crops.forEach { it.isSelected = false }
        crops.forEachIndexed { index, cropping ->
            if (cropping.input.isNullOrEmpty()) {
                selectedCropIndex = index
                selectedCropping = cropping
                cropSelected = true
                cropping.isSelected = true
                isSelected = true
                return
            }
        }
        if (!isSelected) {
            selectedCropIndex = -1
            selectedCropping = null
        }
    }

    fun getTransparentVisibility() = if (crops.filter { it.input.isNullOrEmpty() }.isNullOrEmpty())
        View.VISIBLE else View.GONE

    fun updateInputValue(value: String) {
        if (crops.size == 1) {
            val selectedCrop = crops[0]
            selectedCrop.input = value
        } else {
            crops.forEachIndexed { index, cropping ->
                if (index == selectedCropIndex) {
                    cropping.input = value
                }
            }
        }
    }

    private fun strokeWidth(): Float {
        cropViewAttacher.displayRect?.let {
            return when {
                width < 512 && height < 512 -> 4f
                else -> 8f
            }
        }
        return 8f
    }

    fun addCropsIfNotExist(toAdd: List<Cropping>?) {
        toAdd?.forEach { crop ->
            val isExist = crops.any {
                it.touchDown.x == crop.touchDown.x && it.touchDown.y == crop.touchDown.y &&
                        it.releasePoint.x == crop.releasePoint.x && it.releasePoint.y == crop.releasePoint.y
            }

            if (isExist.not()) crops.add(crop)
        }
    }

    fun addDetectedEdgePoints(toAdd: MutableList<CropPoint>) {
        detectedEdgePoints.clear()
        detectedEdgePoints.addAll(toAdd)
    }

    fun isMultiTagMode(): Boolean {
        return crops.any { it.tags.isNullOrEmpty().not() }
    }
}

interface CropInteractionInterface {
    fun selected(status: Boolean, isClassificationMode: Boolean = false)
    fun onCropDrawn()
    fun showZoom(isShowing: Boolean, x: Float, y: Float)
    fun updateCrops()
}

object MaxCrops {
    const val MULTI_CROP = Int.MAX_VALUE - 1
    const val BINARY_CROP = 2
    const val CROP = 1
}

data class CroppingPaints(
    val innerCropPaint: Paint = Paint(),
    val outerCropPaint: Paint = Paint(),
    val taggedInnerCropPaint: Paint = Paint(),
    val taggedOuterCropPaint: Paint = Paint(),
    val movablePointsPaint: Paint = Paint(),
    val dashedLinePaint: Paint = Paint(),
    val hintPaint: Paint = Paint(),
    val selectedCropPaint: Paint = Paint(),
    val aiDrawnInnerCropPaint: Paint = Paint(),
    val aiDrawnOuterCropPaint: Paint = Paint()
) {
    fun setStrokeWidth(width: Float) {
        outerCropPaint.strokeWidth = width / 2
        innerCropPaint.strokeWidth = width
        taggedOuterCropPaint.strokeWidth = width / 2
        taggedInnerCropPaint.strokeWidth = width
        hintPaint.strokeWidth = width
        selectedCropPaint.strokeWidth = width
        aiDrawnInnerCropPaint.strokeWidth = width
        aiDrawnOuterCropPaint.strokeWidth = width
        movablePointsPaint.strokeWidth = width + 2
    }
}