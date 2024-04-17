package co.nayan.c3views.dragsplit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3views.CanvasPhotoViewAttacher
import co.nayan.c3views.Constants.Dimension.MIN_DIAGONAL_DISTANCE
import co.nayan.c3views.Constants.Dimension.MIN_DIMENSION
import co.nayan.c3views.DrawListener
import co.nayan.c3views.R
import co.nayan.c3views.ZoomView
import co.nayan.c3views.crop.CropInteractionInterface
import co.nayan.c3views.crop.CropPoint
import com.github.chrisbanes.photoview.PhotoView
import timber.log.Timber

class DragSplitPhotoView(context: Context, attributeSet: AttributeSet?) :
    PhotoView(context, attributeSet) {

    private val dragSplitPaints = DragSplitPaints()

    private var cropInteractionInterface: CropInteractionInterface? = null
    val splitCropping = mutableListOf<SplitCropping>()
    private var touchDown = CropPoint(0F, 0F)
    private var cropSelected = false
    private var selectedCropIndex: Int = -1
    private var selectedCropping: SplitCropping? = null

    private var isDrawingCrop = false
    private var currentTouchDown = CropPoint(0F, 0F)

    private var zoomView: ZoomView? = null
    private val zoomPoint: PointF = PointF(0f, 0f)
    private var isZooming = false

    private var currentCrop =
        SplitCropping(touchDown, currentTouchDown, mutableListOf(1f), mutableListOf(""))

    private var bitmapWidth: Float? = null
    private var bitmapHeight: Float? = null

    var showHint: Boolean = false
    private val hintData = mutableListOf<SplitCropping>()

    var isInLabelingMode: Boolean = false
    var showLabel: Boolean = true

    private val drawListener = object : DrawListener {
        override fun touchDownPoint(rawX: Float, rawY: Float, x: Float, y: Float) {
            if (isInLabelingMode) {
                splitCropping.forEachIndexed { index, cropping ->
                    val selectedSegmentIndex =
                        DragSplitUtils.getSelectedSegment(Pair(x, y), cropping)
                    if (selectedSegmentIndex != -1) {
                        splitCropping.forEach {
                            it.isSelected = false
                            it.selectedSegmentIndex = -1
                        }
                        selectedCropIndex = index
                        selectedCropping = cropping
                        cropping.isSelected = true
                        cropping.selectedSegmentIndex = selectedSegmentIndex
                        cropSelected = true
                        cropInteractionInterface?.selected(
                            status = true,
                            isClassificationMode = true
                        )
                        invalidate()
                        return
                    }
                }
            } else {
                zoomPoint.x = rawX
                zoomPoint.y = rawY
                isZooming = true
                cropInteractionInterface?.showZoom(true, x, y)

                if (cropSelected) touchDown.clear()
                else {
                    splitCropping.forEachIndexed { index, cropping ->
                        if (DragSplitUtils.isDragging(Pair(x, y), cropping)) {
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
            if (isInLabelingMode) return
            else {
                val releasePoint = CropPoint(rawX, rawY)
                if (validDimensions(releasePoint, displayRect) && !cropSelected) {
                    val rect = SplitCropping(
                        touchDown.copy(), releasePoint, mutableListOf(1f), mutableListOf("")
                    )
                    splitCropping.add(rect)
                    touchDown.clear()
                    cropInteractionInterface?.onCropDrawn()
                }

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
            if (isInLabelingMode) return
            else {
                zoomPoint.x = rawX
                zoomPoint.y = rawY
                isZooming = true

                if (validDimensions(CropPoint(rawX, rawY), displayRect)) {
                    cropInteractionInterface?.showZoom(true, x, y)
                    if (cropSelected) selectedCropping?.resizeRect(rawX, rawY)
                    else {
                        currentTouchDown.rawX = rawX
                        currentTouchDown.rawY = rawY
                        isDrawingCrop = true
                        currentCrop = SplitCropping(
                            touchDown, currentTouchDown, mutableListOf(1f), mutableListOf("")
                        )
                    }
                    invalidate()
                }
            }
        }

        override fun unselect() {
            unSelect()
        }

        override fun hideZoomView() {
            cropInteractionInterface?.showZoom(false, 0f, 0f)
        }
    }

    init {
        val innerOverlayColor = ContextCompat.getColor(context, R.color.drag_split_inner_overlay)
        val outerOverlayColor = ContextCompat.getColor(context, R.color.drag_split_outer_overlay)
        val hintColor = ContextCompat.getColor(context, R.color.hint_overlay)
        val selectedSegmentColor = ContextCompat.getColor(context, R.color.selected_segment_overlay)

        dragSplitPaints.apply {
            outerRectPaint.apply {
                color = outerOverlayColor
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            innerRectPaint.apply {
                color = innerOverlayColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            movablePointPaint.apply {
                color = outerOverlayColor
                style = Paint.Style.FILL
            }
            hintPaint.apply {
                color = hintColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
            selectedSegmentPaint.apply {
                color = selectedSegmentColor
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
        }

        scaleType = ScaleType.FIT_CENTER
    }

    private val cropViewAttacher = CanvasPhotoViewAttacher(this, drawListener)

    private fun validDimensions(releasePoint: CropPoint, displayRect: RectF): Boolean {
        val diagonalDistance = CropPoint.diagonalDistance(touchDown, releasePoint, displayRect)
        val distance = CropPoint.distance(touchDown, releasePoint, displayRect)
        Timber.e("#### Diagonal Distance $diagonalDistance ####")
        Timber.e("#### Crop Height ${distance.first} ####")
        Timber.e("#### Crop Width ${distance.second} ####")
        return diagonalDistance > MIN_DIAGONAL_DISTANCE
                && distance.first >= MIN_DIMENSION
                && distance.second >= MIN_DIMENSION
                && touchDown.y < releasePoint.y
                && touchDown.x < releasePoint.x
                && validPoints(releasePoint)
    }

    private fun validPoints(releasePoint: CropPoint): Boolean {
        return releasePoint.x > cropViewAttacher.displayRect.left &&
                releasePoint.x < cropViewAttacher.displayRect.right &&
                releasePoint.y > cropViewAttacher.displayRect.top &&
                releasePoint.y < cropViewAttacher.displayRect.bottom
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        zoomView?.let {
            it.setZooming(isZooming, zoomPoint, cropViewAttacher.scale)
            it.invalidate()
        }

        dragSplitPaints.setStrokeWidth(strokeWidth())

        splitCropping.forEach { cropping ->
            cropViewAttacher.displayRect?.let { displayRect ->
                cropping.draw(
                    canvas,
                    displayRect,
                    isInLabelingMode,
                    dragSplitPaints,
                    showLabel
                )
            }
        }

        if (isDrawingCrop && displayRect != null) {
            touchDown.transform(displayRect)
            currentTouchDown.transform(displayRect)

            if (currentTouchDown.y > touchDown.y &&
                validPoints(currentTouchDown) &&
                touchDown.x < currentTouchDown.x
            ) {
                cropViewAttacher.displayRect?.let { displayRect ->
                    currentCrop.draw(
                        canvas,
                        displayRect,
                        isInLabelingMode,
                        dragSplitPaints,
                        showLabel
                    )
                }
            }
        }

        if (showHint) {
            hintData.forEach { cropping ->
                cropViewAttacher.displayRect?.let { displayRect ->
                    cropping.draw(
                        canvas,
                        displayRect,
                        isInLabelingMode,
                        dragSplitPaints,
                        showLabel
                    )
                }
            }
        }
    }

    fun reset() {
        splitCropping.clear()
        unSelect()
    }

    fun delete() {
        if (selectedCropIndex != -1 && selectedCropIndex < splitCropping.size) {
            splitCropping[selectedCropIndex].isSelected = false
            splitCropping.removeAt(selectedCropIndex)
            selectedCropping = null
            cropSelected = false
            invalidate()
            cropInteractionInterface?.selected(false)
        }
    }

    fun unSelect() {
        if (cropSelected) {
            selectedCropping?.let {
                it.isSelected = false
                it.selectedSegmentIndex = -1
            }
            cropSelected = false
            selectedCropIndex = -1
            selectedCropping = null
            cropInteractionInterface?.selected(false)
            invalidate()
        }
    }

    fun undo() {
        if (splitCropping.size > 0) {
            splitCropping.remove(splitCropping.last())
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
        bitmapWidth: Float, bitmapHeight: Float
    ): List<AnnotationObjectsAttribute> {
        val annotationObjectsAttributes = mutableListOf<AnnotationObjectsAttribute>()
        val sortedCroppingList =
            splitCropping.sortedWith(compareBy({ it.touchDown.rawY }, { it.releasePoint.rawY }))
        sortedCroppingList.forEach {
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

    fun addHintData(toAdd: List<SplitCropping>) {
        hintData.clear()
        hintData.addAll(toAdd)
    }

    fun addSegment() {
        val size = selectedCropping?.segmentRatioList?.size ?: 15
        if (size < 15) {
            selectedCropping?.resetSegmentRatio()
            selectedCropping?.segmentRatioList?.add(1f)
            selectedCropping?.inputList?.add("")
            cropInteractionInterface?.updateCrops()
            invalidate()
        }
    }

    fun removeSegment() {
        val size = selectedCropping?.segmentRatioList?.size ?: 0
        if (size > 1) {
            selectedCropping?.segmentRatioList?.removeAt(size - 1)
            selectedCropping?.inputList?.removeAt(size - 1)
            selectedCropping?.resetSegmentRatio()
            cropInteractionInterface?.updateCrops()
            invalidate()
        }
    }

    fun updateInputValue(value: String) {
        splitCropping.forEachIndexed { index, cropping ->
            if (index == selectedCropIndex) {
                if (cropping.segmentRatioList.size > 1 && cropping.selectedSegmentIndex > 0)
                    cropping.inputList[cropping.selectedSegmentIndex - 1] = value
                else cropping.inputList[0] = value
            }
        }
    }

    fun selectNext() {
        splitCropping.sortBy { it.touchDown.rawX }

        if (selectedCropping?.inputList?.none { it.isEmpty() } == true) {
            selectedCropIndex = -1
            selectedCropping?.isSelected = false
            selectedCropping?.selectedSegmentIndex = -1
            selectedCropping = null
        }

        if (selectedCropping == null) {
            splitCropping.forEachIndexed { index, cropping ->
                if (cropping.inputList.any { it.isEmpty() }) {
                    selectedCropIndex = index
                    selectedCropping = cropping
                    cropping.isSelected = true
                    val segIndex = cropping.inputList.indexOfFirst { it.isEmpty() }

                    if (cropping.inputList.size == 1) selectedCropping?.selectedSegmentIndex = 0
                    else selectedCropping?.selectedSegmentIndex = segIndex + 1
                    cropSelected = true
                    invalidate()
                    return
                }
            }
        } else {
            selectedCropping?.let { cropping ->
                if (cropping.inputList.none { it.isEmpty() }) {
                    cropping.isSelected = false
                    cropping.selectedSegmentIndex = -1
                } else {
                    val index = cropping.inputList.indexOfFirst { it.isEmpty() }
                    cropping.selectedSegmentIndex = index + 1
                    return
                }
            }
        }
    }

    fun getTransparentVisibility() =
        if (splitCropping.any { cropping -> cropping.inputList.any { it.isNotEmpty() } })
            View.VISIBLE else View.GONE

    private fun strokeWidth(): Float {
        cropViewAttacher.displayRect?.let {
            return when {
                width < 256 && height < 256 -> 4f
                width < 512 && height < 512 -> 6f
                else -> 8f
            }
        }
        return 8f
    }

    fun addSplitCropIfNotExist(toAdd: List<SplitCropping>?) {
        toAdd?.forEach { splitCrop ->
            val isExist =
                splitCropping.any {
                    it.touchDown.x == splitCrop.touchDown.x && it.touchDown.y == splitCrop.touchDown.y &&
                            it.releasePoint.x == splitCrop.releasePoint.x && it.releasePoint.y == splitCrop.releasePoint.y
                }
            if (isExist.not()) splitCropping.add(splitCrop)
        }
    }
}

data class DragSplitPaints(
    val outerRectPaint: Paint = Paint(),
    val innerRectPaint: Paint = Paint(),
    val movablePointPaint: Paint = Paint(),
    val hintPaint: Paint = Paint(),
    val selectedSegmentPaint: Paint = Paint()
) {
    fun setStrokeWidth(width: Float) {
        outerRectPaint.strokeWidth = width / 2
        innerRectPaint.strokeWidth = width
        hintPaint.strokeWidth = width
        selectedSegmentPaint.strokeWidth = width
    }
}