package co.nayan.c3specialist_v2.record_visualization

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.ResultType
import co.nayan.c3specialist_v2.databinding.RecordHistoryItemBinding
import co.nayan.c3specialist_v2.databinding.UserAnswerItemBinding
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.AnnotationJudgment
import co.nayan.c3v2.core.models.RecordAnnotationHistory
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
import co.nayan.canvas.utils.isVideo
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
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import timber.log.Timber

class RecordHistoryViewHolder(
    private val binding: RecordHistoryItemBinding,
    private val clickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    private var displayUrl: String? = null
    private var recordAnnotation: RecordAnnotationHistory? = null
    private var drawType: String? = null
    private var applicationMode: String? = null
    private var contrast: ColorMatrixColorFilter? = null
    private var recordBitmap: Bitmap? = null
    private var maskedBitmap: Bitmap? = null

    @SuppressLint("ClickableViewAccessibility")
    private val makeTransparentListener = View.OnTouchListener { _, event ->
        val photoView = binding.imageContainer.children.firstOrNull()
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
        displayUrl: String?,
        recordAnnotation: RecordAnnotationHistory?,
        applicationMode: String?,
        mediaType: String?
    ) {
        setupBackground(recordAnnotation?.result)
        this.displayUrl = displayUrl
        this.recordAnnotation = recordAnnotation
        this.applicationMode = applicationMode
        binding.makeTransparent.setOnTouchListener(makeTransparentListener)
        binding.junkRecordIv.gone()
        binding.answerTxt.gone()

        if (mediaType == MediaType.VIDEO) {
            binding.videoViewContainer.visible()
            binding.imageContainer.gone()
            binding.playerView.gone()
        } else {
            binding.imageContainer.removeAllViews()
            binding.playerView.gone()
            binding.videoViewContainer.gone()
            binding.imageContainer.visible()
            setupRecord()
        }

        setupAnnotationData()
        setupJudgmentData()
        setupReviewData()

        itemView.setOnClickListener(clickListener)
        binding.nonStarredIv.setOnClickListener(clickListener)
        binding.starredIv.setOnClickListener(clickListener)

        itemView.setTag(R.id.record, recordAnnotation)
        binding.starredIv.setTag(R.id.record, recordAnnotation)
        binding.nonStarredIv.setTag(R.id.record, recordAnnotation)

        binding.reloadIV.setOnClickListener { setupRecord() }
    }

    private fun setupBackground(result: String?) {
        val colorId = when (result) {
            ResultType.CORRECT -> R.color.correct
            ResultType.INCONCLUSIVE -> R.color.inconclusive
            ResultType.INCORRECT -> R.color.incorrect
            ResultType.NO_CONSENSUS -> R.color.no_consensus
            else -> R.color.white
        }
        val color = ContextCompat.getColor(binding.recordContainer.context, colorId)
        binding.recordContainer.setCardBackgroundColor(color)
    }

    private fun setupAnnotationData() {
        val annotationType = recordAnnotation?.type
        val annotationBy = recordAnnotation?.createdBy
        if (annotationType == null && annotationBy == null) {
            binding.annotationForTxt.text = String.format("Annotation pending...")
        } else {
            binding.annotationForTxt.text = annotationType?.split("::")?.last()
            binding.annotatedByTxt.text = annotationBy
        }
    }

    private fun setupJudgmentData() {
        val annotationJudgments = recordAnnotation?.annotationJudgments
        if (annotationJudgments.isNullOrEmpty()) {
            binding.judgementContainer.gone()
        } else {
            binding.judgementContainer.visible()
            val specialistJudgments = annotationJudgments.filter {
                it.type?.split("::")?.last() == "SpecialistJudgment"
            }
            if (specialistJudgments.isNullOrEmpty()) {
                binding.specialistJudgmentsContainer.gone()
            } else {
                binding.specialistJudgmentsContainer.visible()
                setupJudgments(specialistJudgments, binding.specialistJudgmentItemContainer)
            }
            val managerJudgments = annotationJudgments.filter {
                it.type?.split("::")?.last() == "ManagerJudgment"
            }
            if (managerJudgments.isNullOrEmpty()) {
                binding.managerJudgmentsContainer.gone()
            } else {
                binding.managerJudgmentsContainer.visible()
                setupJudgments(managerJudgments, binding.managerJudgmentItemContainer)
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun setupJudgments(
        judgments: List<AnnotationJudgment>, itemContainer: LinearLayout
    ) {
        itemContainer.removeAllViews()
        itemContainer.let { layout ->
            judgments.forEach { judgment ->
                val userBinding = UserAnswerItemBinding.inflate(
                    LayoutInflater.from(layout.context),
                    itemContainer,
                    false
                )
                userBinding.createdByTxt.text = judgment.createdBy
                userBinding.userAnswerTxt.text = judgment.judgment.toString()
                layout.addView(userBinding.root)
            }
        }
    }

    private fun setupReviewData() {
        val annotationReviews = recordAnnotation?.annotationReviews
        if (annotationReviews.isNullOrEmpty()) {
            binding.reviewContainer.gone()
        } else {
            binding.reviewContainer.visible()
            val managerReviews = annotationReviews.filter {
                it.type?.split("::")?.last() == "ManagerReview"
            }
            if (managerReviews.isNullOrEmpty()) {
                binding.managerReviewContainer.gone()
            } else {
                binding.managerReviewContainer.visible()
                setupReviews(managerReviews, binding.managerReviewItemContainer)
            }
            val adminReviews = annotationReviews.filter {
                it.type?.split("::")?.last() == "AdminReview"
            }
            if (adminReviews.isNullOrEmpty()) {
                binding.adminReviewsContainer.gone()
            } else {
                binding.adminReviewsContainer.visible()
                setupReviews(adminReviews, binding.adminReviewItemContainer)
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun setupReviews(
        reviews: List<AnnotationJudgment>,
        itemContainer: LinearLayout
    ) {
        itemContainer.removeAllViews()
        itemContainer.let { layout ->
            reviews.forEach { judgment ->
                val userBinding = UserAnswerItemBinding.inflate(
                    LayoutInflater.from(layout.context),
                    itemContainer,
                    false
                )
                userBinding.createdByTxt.text = judgment.createdBy
                userBinding.userAnswerTxt.text = judgment.judgment.toString()
                layout.addView(userBinding.root)
            }
        }
    }

    private fun setupRecord() {
        binding.makeTransparent.gone()
        maskedBitmap = null
        recordBitmap = null
        binding.reloadIV.gone()
        binding.loaderIV.visible()
        binding.imageContainer.invisible()
        drawType = recordAnnotation?.drawType()
        displayUrl?.let { mediaUrl ->
            try {
                when {
                    mediaUrl.isVideo() -> loadVideo(mediaUrl)
                    mediaUrl.contains("gif") -> {
                        val photoView = PhotoView(itemView.context)
                        photoView.colorFilter = contrast
                        binding.imageContainer.addView(photoView)
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
                Firebase.crashlytics.recordException(e)
                onBitmapLoadFailed()
                Timber.e("${e.printStackTrace()}")
            }
        }
    }

    private fun loadVideo(mediaUrl: String) {
        binding.reloadIV.gone()
        binding.loaderIV.gone()
        binding.imageContainer.gone()
        binding.playerView.visible()
        try {
            binding.playerView.apply {
                setBackgroundColor(Color.TRANSPARENT)
                setVideoURI(Uri.parse(mediaUrl))
                setOnPreparedListener {
                    val mediaProportion: Float = it.videoHeight.toFloat() / it.videoWidth.toFloat()
                    layoutParams = this.layoutParams.apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = (this.width.toFloat() * mediaProportion).toInt()
                    }
                    it.start()
                    it.isLooping = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setUpAnswer()
    }

    private fun loadGif(mediaUrl: String, photoView: PhotoView) {
        Glide.with(photoView.context)
            .asGif()
            .load(mediaUrl)
            .listener(gifRequestListener)
            .into(photoView)

        setUpAnswer()
    }

    private fun setUpAnswer() {
        if (applicationMode == Mode.EVENT_VALIDATION) {
            binding.answerTxt.gone()
            binding.junkRecordIv.gone()
        } else {
            val answer = recordAnnotation?.answer()
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
            if (drawType != DrawType.MASK) {
                onBitmapLoadSuccess()
            }
            setupAnswer(resource)
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            super.onLoadFailed(errorDrawable)
            onBitmapLoadFailed()
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            binding.imageContainer.removeAllViews()
        }
    }

    private fun setupAnswer(bitmap: Bitmap) {
        val annotations = recordAnnotation?.annotations()
        if (annotations.isNullOrEmpty()) {
            val photoView = PhotoView(binding.imageContainer.context)
            binding.makeTransparent.gone()
            loadImage(bitmap, photoView)

            if (applicationMode != Mode.EVENT_VALIDATION) {
                val answer = recordAnnotation?.answer()
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
                    val cropView = CropPhotoView(binding.imageContainer.context, null)
                    cropView.reset()
                    cropView.editMode(false)
                    cropView.setBitmapAttributes(bitmap.width, bitmap.height)
                    cropView.crops.addAll(annotations.crops(bitmap))
                    binding.makeTransparent.visibility = cropView.getTransparentVisibility()
                    loadImage(bitmap, cropView)
                }

                DrawType.QUADRILATERAL -> {
                    val quadrilateralView =
                        QuadrilateralPhotoView(binding.imageContainer.context, null)
                    quadrilateralView.reset()
                    quadrilateralView.editMode(false)
                    quadrilateralView.quadrilaterals.addAll(annotations.quadrilaterals(bitmap))
                    loadImage(bitmap, quadrilateralView)
                }

                DrawType.POLYGON -> {
                    val polygonView = PolygonPhotoView(binding.imageContainer.context, null)
                    polygonView.reset()
                    polygonView.touchEnabled(false)
                    polygonView.points.addAll(annotations.polygonPoints(bitmap))
                    loadImage(bitmap, polygonView)
                }

                DrawType.CONNECTED_LINE -> {
                    val paintView = PaintPhotoView(binding.imageContainer.context, null)
                    paintView.reset()
                    paintView.editMode(false)
                    paintView.setBitmapAttributes(bitmap.width, bitmap.height)
                    paintView.paintDataList.addAll(annotations.paintDataList(bitmap = bitmap))
                    loadImage(bitmap, paintView)
                }

                DrawType.SPLIT_BOX -> {
                    val dragSplitView = DragSplitPhotoView(binding.imageContainer.context, null)
                    dragSplitView.reset()
                    dragSplitView.editMode(false)
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
                        val photoView = PhotoView(binding.imageContainer.context)
                        recordBitmap?.let { loadImage(it, photoView) }
                    } else downloadMaskBitmaps(imageUrls)
                }

                else -> {
                    val photoView = PhotoView(binding.imageContainer.context)
                    binding.makeTransparent.gone()
                    loadImage(bitmap, photoView)
                }
            }
        }

        when (applicationMode) {
            Mode.INPUT, Mode.LP_INPUT, Mode.MULTI_INPUT, Mode.CLASSIFY, Mode.DYNAMIC_CLASSIFY -> {
                val answer = recordAnnotation?.answer()
                if (answer.isNullOrEmpty().not() && answer != Judgment.JUNK) {
                    binding.answerTxt.visible()
                    binding.answerTxt.text = answer
                } else binding.answerTxt.gone()
            }

            else -> binding.answerTxt.gone()
        }
    }

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
                                    val photoView = PhotoView(binding.imageContainer.context)
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
                Firebase.crashlytics.recordException(e)
                e.printStackTrace()
                onMaskBitmapLoadFailed()
            }
        }
    }

    private fun onBitmapLoadFailed() {
        itemView.context?.let {
            binding.loaderIV.gone()
            binding.reloadIV.visible()
            binding.imageContainer.invisible()
        }
    }

    private fun onMaskBitmapLoadFailed() {
        itemView.context?.let {
            binding.loaderIV.gone()
            binding.reloadIV.gone()
            binding.imageContainer.visible()
        }

        recordBitmap?.let {
            val photoView = PhotoView(binding.imageContainer.context)
            recordBitmap?.let { loadImage(it, photoView) }
        }
    }

    private fun onBitmapLoadSuccess() {
        itemView.context?.let {
            binding.loaderIV.gone()
            binding.reloadIV.gone()
            binding.imageContainer.visible()
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
        binding.imageContainer.addView(view)
    }
}