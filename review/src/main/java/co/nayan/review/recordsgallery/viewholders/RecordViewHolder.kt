package co.nayan.review.recordsgallery.viewholders

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.overlayBitmap
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.crop.CropPhotoView
import co.nayan.c3views.dragsplit.DragSplitPhotoView
import co.nayan.c3views.paint.PaintPhotoView
import co.nayan.c3views.polygon.PolygonPhotoView
import co.nayan.c3views.quadrilateral.QuadrilateralPhotoView
import co.nayan.c3views.utils.*
import co.nayan.review.R
import co.nayan.review.databinding.ListItemRecordRowBinding
import co.nayan.review.utils.isVideo
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.github.chrisbanes.photoview.PhotoView
import timber.log.Timber

class RecordViewHolder(
    val binding: ListItemRecordRowBinding,
    private val clickListener: View.OnClickListener,
    private val longClickListener: View.OnLongClickListener
) : BaseViewHolder(binding.root) {

    private var record: Record? = null
    private var drawType: String? = null
    private var applicationMode: String? = null
    private var appFlavor: String? = null
    private var contrast: ColorMatrixColorFilter? = null
    private var showOverlay: Boolean = true
    private var recordBitmap: Bitmap? = null
    private var maskedBitmap: Bitmap? = null

    @SuppressLint("ClickableViewAccessibility")
    private val makeTransparentListener = View.OnTouchListener { _, event ->
        val photoView = binding.photoViewContainer.children.firstOrNull()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                photoView?.let {
                    when (photoView) {
                        is CropPhotoView -> {
                            photoView.showLabel = false
                            it.invalidate()
                        }
                        is DragSplitPhotoView -> {
                            photoView.showLabel = false
                            it.invalidate()
                        }
                        else -> {
                            recordBitmap?.let { bitmap ->
                                (photoView as PhotoView).setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                photoView?.let {
                    when (photoView) {
                        is CropPhotoView -> {
                            photoView.showLabel = true
                            it.invalidate()
                        }
                        is DragSplitPhotoView -> {
                            photoView.showLabel = true
                            it.invalidate()
                        }
                        else -> {
                            maskedBitmap?.let { bitmap ->
                                (photoView as PhotoView).setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            }
        }
        true
    }

    fun onBind(
        record: Record?,
        isInSelectionMode: Boolean,
        isSelected: Boolean,
        applicationMode: String?,
        position: Int,
        showStar: Boolean = false,
        appFlavor: String? = null,
        contrast: ColorMatrixColorFilter? = null,
        showOverlay: Boolean = true
    ) {
        this.record = record
        this.applicationMode = applicationMode
        this.appFlavor = appFlavor
        this.contrast = contrast
        this.showOverlay = showOverlay

        binding.makeTransparent.setOnTouchListener(makeTransparentListener)
        binding.junkRecordIv.gone()
        binding.answerTxt.gone()
        binding.confidenceScoreTxt.gone()

        if (record?.mediaType == MediaType.VIDEO) {
            binding.videoViewContainer.visible()
            binding.photoViewContainer.gone()
            setupVideoView()
        } else {
            binding.photoViewContainer.removeAllViews()
            binding.videoViewContainer.gone()
            binding.photoViewContainer.visible()
            setupRecord()
        }

        setupStarredStatus(showStar)
        setupSelection(isSelected, isInSelectionMode)
        setupSniffingView()

        itemView.setOnClickListener(clickListener)
        itemView.setOnLongClickListener(longClickListener)
        binding.nonStarredIv.setOnClickListener(clickListener)
        binding.starredIv.setOnClickListener(clickListener)

        itemView.setTag(R.id.record, record)
        itemView.setTag(R.id.position, position)
        binding.starredIv.setTag(R.id.record, record)
        binding.nonStarredIv.setTag(R.id.record, record)
        binding.starredIv.setTag(R.id.position, position)
        binding.nonStarredIv.setTag(R.id.position, position)
        binding.recordCheckBox.setTag(R.id.record, position)

        binding.reloadIV.setOnClickListener { setupRecord() }
    }

    private fun setupVideoView() {
        binding.recordIdTxt.text =
            if ((appFlavor != "qa" || record?.mediaType != MediaType.VIDEO)
                && record?.isSniffingRecord == true && record?.randomSniffingId != null
            ) String.format(
                itemView.context.getString(R.string.record_id_text),
                record?.randomSniffingId
            )
            else String.format(itemView.context.getString(R.string.record_id_text), record?.id)
    }

    private fun setupSniffingView() {
        if ((appFlavor == "qa" || appFlavor == "dev") && record?.isSniffingRecord == true) {
            if (record?.needsRejection == true) {
                binding.needsRejectionView.visible()
                binding.needsNoRejectionView.gone()
            } else {
                binding.needsRejectionView.gone()
                binding.needsNoRejectionView.visible()
            }
        } else {
            binding.needsNoRejectionView.gone()
            binding.needsRejectionView.gone()
        }
    }

    private fun setupStarredStatus(showStar: Boolean) {
        if (showStar) {
            if (record?.starred == true) {
                binding.starredIv.visible()
                binding.nonStarredIv.gone()
            } else {
                binding.nonStarredIv.visible()
                binding.starredIv.gone()
            }
        } else {
            binding.starredIv.gone()
            binding.nonStarredIv.gone()
        }
    }

    private fun setupSelection(isSelected: Boolean, isInSelectionMode: Boolean) {
        if (isSelected) binding.selectionView.visible()
        else binding.selectionView.gone()

        if (isInSelectionMode) {
            binding.recordCheckBox.isChecked = isSelected
            binding.recordCheckBox.visible()
        } else binding.recordCheckBox.gone()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecord() {
        binding.makeTransparent.gone()
        maskedBitmap = null
        recordBitmap = null
        binding.reloadIV.gone()
        binding.loaderIV.visible()
        binding.photoViewContainer.invisible()
        drawType = record?.drawType()
        val url = record?.displayImage ?: record?.mediaUrl
        url?.let { mediaUrl ->
            try {
                when {
                    mediaUrl.isVideo() -> {
                        binding.videoViewContainer.visible()
                        binding.photoViewContainer.gone()
                        setupVideoView()
                    }
                    mediaUrl.contains("gif") -> {
                        val photoView = PhotoView(itemView.context)
                        photoView.colorFilter = contrast
                        photoView.setOnTouchListener { _, _ -> false }
                        binding.photoViewContainer.addView(photoView)
                        loadGif(mediaUrl, photoView)
                    }
                    else -> {
                        Glide.with(itemView.context)
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .load(mediaUrl)
                            .into(target)
                    }
                }
            } catch (e: Exception) {
                onBitmapLoadFailed()
                Timber.e("${e.printStackTrace()}")
            }
        }
    }

    private fun loadGif(mediaUrl: String, photoView: PhotoView) {
        Glide.with(itemView.context)
            .asGif()
            .load(mediaUrl)
            .listener(gifRequestListener)
            .into(photoView)

        setUpAnswers()
    }

    private fun setUpAnswers() {
        if (applicationMode == Mode.EVENT_VALIDATION) {
            binding.answerTxt.gone()
            binding.junkRecordIv.gone()
        } else {
            val answer = record?.answer()
            if (answer.isNullOrEmpty()) {
                binding.junkRecordIv.gone()
                binding.answerTxt.gone()
            } else {
                if (answer == Judgment.JUNK) {
                    binding.junkRecordIv.visible()
                    binding.answerTxt.gone()
                } else {
                    binding.junkRecordIv.gone()
                    binding.answerTxt.visible()
                    binding.answerTxt.text = answer
                }
            }
        }
    }

    private val gifRequestListener = object : RequestListener<GifDrawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<GifDrawable>,
            isFirstResource: Boolean
        ): Boolean {
            onBitmapLoadFailed()
            return false
        }

        override fun onResourceReady(
            resource: GifDrawable,
            model: Any,
            target: Target<GifDrawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            onBitmapLoadSuccess()
            return false
        }
    }

    private val target = object : CustomTarget<Bitmap>() {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            onBitmapLoadSuccess()
            setupAnswer(resource)
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            super.onLoadFailed(errorDrawable)
            onBitmapLoadFailed()
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            binding.photoViewContainer.removeAllViews()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAnswer(bitmap: Bitmap) {
        val annotations = record?.annotations()
        if (annotations.isNullOrEmpty() || showOverlay.not()) {
            val photoView = PhotoView(binding.photoViewContainer.context)
            photoView.setOnTouchListener { _, _ -> false }
            binding.makeTransparent.gone()
            loadImage(bitmap, photoView)

            if (showOverlay && applicationMode != Mode.EVENT_VALIDATION) {
                val answer = record?.answer()
                if (answer == Judgment.JUNK) {
                    binding.junkRecordIv.visible()
                    binding.answerTxt.gone()
                } else {
                    binding.junkRecordIv.gone()
                    binding.answerTxt.visible()
                }
            } else {
                binding.junkRecordIv.gone()
                binding.answerTxt.gone()
            }
        } else {
            binding.junkRecordIv.gone()
            binding.answerTxt.gone()
            when (drawType) {
                DrawType.BOUNDING_BOX -> {
                    val cropView = CropPhotoView(binding.photoViewContainer.context, null)
                    cropView.reset()
                    cropView.touchEnabled(false)
                    cropView.setBitmapAttributes(bitmap.width, bitmap.height)
                    cropView.crops.addAll(annotations.crops(bitmap))
                    binding.makeTransparent.visibility = cropView.getTransparentVisibility()
                    loadImage(bitmap, cropView)
                }
                DrawType.QUADRILATERAL -> {
                    val quadrilateralView =
                        QuadrilateralPhotoView(binding.photoViewContainer.context, null)
                    quadrilateralView.reset()
                    quadrilateralView.touchEnabled(false)
                    quadrilateralView.quadrilaterals.addAll(annotations.quadrilaterals(bitmap))
                    loadImage(bitmap, quadrilateralView)
                }
                DrawType.POLYGON -> {
                    val polygonView = PolygonPhotoView(binding.photoViewContainer.context, null)
                    polygonView.reset()
                    polygonView.touchEnabled(false)
                    polygonView.points.addAll(annotations.polygonPoints(bitmap))
                    loadImage(bitmap, polygonView)
                }
                DrawType.CONNECTED_LINE -> {
                    val paintView = PaintPhotoView(binding.photoViewContainer.context, null)
                    paintView.reset()
                    paintView.touchEnabled(false)
                    paintView.setBitmapAttributes(bitmap.width, bitmap.height)
                    paintView.paintDataList.addAll(annotations.paintDataList(bitmap = bitmap))
                    loadImage(bitmap, paintView)
                }
                DrawType.SPLIT_BOX -> {
                    val dragSplitView = DragSplitPhotoView(binding.photoViewContainer.context, null)
                    dragSplitView.reset()
                    dragSplitView.touchEnabled(false)
                    dragSplitView.setBitmapAttributes(bitmap.width, bitmap.height)
                    dragSplitView.splitCropping.addAll(annotations.splitCrops(bitmap))
                    binding.makeTransparent.visibility = dragSplitView.getTransparentVisibility()
                    loadImage(bitmap, dragSplitView)
                }
                DrawType.MASK -> {
                    recordBitmap = bitmap
                    val imageUrls = annotations.map { it.maskUrl }
                    if (imageUrls.isNullOrEmpty()) {
                        onBitmapLoadSuccess()
                        val photoView = PhotoView(binding.photoViewContainer.context)
                        photoView.setOnTouchListener { _, _ -> false }
                        recordBitmap?.let { loadImage(it, photoView) }
                    } else downloadMaskBitmaps(imageUrls)
                }
                else -> {
                    val photoView = PhotoView(binding.photoViewContainer.context)
                    photoView.setOnTouchListener { _, _ -> false }
                    binding.makeTransparent.gone()
                    loadImage(bitmap, photoView)
                }
            }
        }

        when (applicationMode) {
            Mode.INPUT, Mode.LP_INPUT, Mode.MULTI_INPUT, Mode.CLASSIFY, Mode.DYNAMIC_CLASSIFY -> {
                val answer = record?.answer()
                if (answer.isNullOrEmpty().not() && answer != Judgment.JUNK) {
                    binding.answerTxt.visible()
                    binding.answerTxt.text = answer
                } else binding.answerTxt.gone()
            }
            else -> binding.answerTxt.gone()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun downloadMaskBitmaps(imageUrls: List<String?>) {
        val bitmaps = mutableListOf<Bitmap>()
        imageUrls.forEachIndexed { index, imageUrl ->
            try {
                Glide.with(itemView.context)
                    .asBitmap()
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(
                            resource: Bitmap, transition: Transition<in Bitmap>?
                        ) {
                            bitmaps.add(resource)
                            if (index == imageUrls.size - 1) {
                                onBitmapLoadSuccess()
                                binding.makeTransparent.visible()
                                recordBitmap?.let {
                                    val photoView = PhotoView(binding.photoViewContainer.context)
                                    photoView.setOnTouchListener { _, _ -> false }

                                    maskedBitmap = it.overlayBitmap(bitmaps)
                                    maskedBitmap?.let { maskedBitmap ->
                                        loadImage(maskedBitmap, photoView)
                                    }
                                }
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {}

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            super.onLoadFailed(errorDrawable)
                            onMaskBitmapLoadFailed()
                            return
                        }
                    })
            } catch (e: Exception) {
                e.printStackTrace()
                onMaskBitmapLoadFailed()
            }
        }
    }

    private fun onBitmapLoadFailed() {
        itemView.context?.let {
            binding.loaderIV.gone()
            binding.reloadIV.visible()
            binding.photoViewContainer.invisible()
        }
    }

    private fun onMaskBitmapLoadFailed() {
        itemView.context?.let {
            binding.loaderIV.gone()
            binding.reloadIV.gone()
            binding.photoViewContainer.visible()

            val photoView = PhotoView(binding.photoViewContainer.context)
            recordBitmap?.let { loadImage(it, photoView) }
        }
    }

    private fun onBitmapLoadSuccess() {
        itemView.context?.let {
            binding.loaderIV.gone()
            binding.reloadIV.gone()
            binding.photoViewContainer.visible()
        }
    }

    private fun loadImage(bitmap: Bitmap, view: PhotoView) {
        view.apply {
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setLayoutParams(layoutParams)
            setImageBitmap(bitmap)
            colorFilter = contrast
        }
        binding.photoViewContainer.addView(view)
    }
}