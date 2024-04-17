package co.nayan.review.recorddetail

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.MediaController
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.crop.CropPhotoView
import co.nayan.c3views.dragsplit.DragSplitPhotoView
import co.nayan.c3views.paint.PaintPhotoView
import co.nayan.c3views.polygon.PolygonPhotoView
import co.nayan.c3views.quadrilateral.QuadrilateralPhotoView
import co.nayan.c3views.utils.*
import co.nayan.review.R
import co.nayan.review.databinding.FragmentRecordDetailBinding
import co.nayan.review.recordsgallery.RecordItem
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

class RecordDetailAdapter(
    private val recordListener: RecordListener,
    private val orientation: Int?,
    private val applicationMode: String?,
    private val appFlavor: String?,
    private val showStar: Boolean = false,
    private val contrastValue: Int = 50,
    private val infoTouchListener: ((Record?) -> Unit)? = null
) : RecyclerView.Adapter<RecordDetailAdapter.MyViewHolder>() {

    private val recordItems = arrayListOf<RecordItem>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecordDetailAdapter.MyViewHolder {
        return MyViewHolder(
            FragmentRecordDetailBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecordDetailAdapter.MyViewHolder, position: Int) {
        holder.onBind(
            recordItems[position],
            appFlavor,
            applicationMode,
            showStar,
            contrastValue
        )
    }

    override fun getItemCount(): Int {
        return recordItems.size
    }

    fun addAll(toAdd: List<RecordItem>) {
        recordItems.addAll(toAdd)
        notifyDataSetChanged()
    }

    fun updateRecords(recordItem: RecordItem?) {
        val submittedRecordIndex = recordItems.indexOf(recordItem)
        if (submittedRecordIndex != RecyclerView.NO_POSITION) {
            recordItems.removeAt(submittedRecordIndex)
            notifyItemRemoved(submittedRecordIndex)
        }
    }

    fun markRecordAsApproved(recordItem: RecordItem) {
        val selectedRecordIndex = recordItems.indexOf(recordItem)
        if (selectedRecordIndex != RecyclerView.NO_POSITION) {
            recordItems.removeAt(selectedRecordIndex)
            notifyItemRemoved(selectedRecordIndex)
        }
    }

    fun markRecordAsRejected(recordItem: RecordItem) {
        val selectedRecordIndex = recordItems.indexOf(recordItem)
        if (selectedRecordIndex != RecyclerView.NO_POSITION) {
            recordItems.removeAt(selectedRecordIndex)
            notifyItemRemoved(selectedRecordIndex)
        }
    }

    inner class MyViewHolder(
        val binding: FragmentRecordDetailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

//        private val recordIdTxt = itemView.recordIdTxt
//        private val answerTxt = itemView.answerTxt
//        private val hideOverlayIv = itemView.hideOverlayIv
//        private val reloadIV = itemView.reloadIV
//        private val loaderIV = itemView.loaderIV
//        private val starredIv = itemView.starredIv
//        private var needsRejectionView = itemView.needsRejectionView
//        private var needsNoRejectionView = itemView.needsNoRejectionView
//        private val videoView = itemView.videoView
//        private val junkRecordIv = itemView.junkRecordIv
//        private val answerContainer = itemView.answerContainer
//        private val parentViewController = itemView.parentViewController
//        private val photoViewContainer = itemView.photoViewContainer
//        private val resetBtn = itemView.resetBtn
//        private val approveBtn = itemView.approveBtn
//        private val statusImg = itemView.statusImg

        private var applicationMode: String? = null
        private var recordItem: RecordItem? = null
        private var appFlavor: String? = null
        private var drawType: String? = null
        private var showStar: Boolean = false
        private var contrastValue: Int = 50
        private var recordBitmap: Bitmap? = null
        private var maskedBitmap: Bitmap? = null

        fun onBind(
            recordItem: RecordItem,
            appFlavor: String? = null,
            applicationMode: String?,
            showStar: Boolean,
            contrastValue: Int
        ) {
            this.recordItem = recordItem
            this.appFlavor = appFlavor
            this.applicationMode = applicationMode
            this.showStar = showStar
            this.contrastValue = contrastValue

            val recordId = if (recordItem.record.isSniffingRecord == true)
                recordItem.record.randomSniffingId ?: recordItem.record.id
            else recordItem.record.id
            binding.recordIdTxt.text = String.format("Record ID: $recordId")
            binding.recordIdTxt.setOnTouchListener(recordInfoTouchListener)

            setupStarredStatus(showStar)
            setupSniffingView(recordItem.record.isSniffingRecord)
            setupSubmissionStatus()
            val url = if (recordItem.record.mediaType == MediaType.VIDEO)
                recordItem.record.mediaUrl
            else recordItem.record.displayImage ?: recordItem.record.mediaUrl
            url?.let { mediaUrl ->
                if (mediaUrl.isVideo()) {
                    binding.hideOverlayIv.gone()
                    onVideoReady(mediaUrl)
                } else {
                    binding.hideOverlayIv.visible()
                    binding.reloadIV.gone()
                    binding.loaderIV.visible()
                    setupRecord(recordItem.record)
                }
            }

            binding.hideOverlayIv.setOnTouchListener(hideOverlayListener)
            binding.reloadIV.setOnClickListener { setupRecord(recordItem.record) }
            binding.resetBtn.setOnClickListener {
                recordItem.isApproved = false
                recordItem.isRejected = true
                recordListener.onResetClicked(recordItem)
            }
            binding.approveBtn.setOnClickListener {
                recordItem.isApproved = true
                recordItem.isRejected = false
                recordListener.onApproveClicked(recordItem)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private val recordInfoTouchListener = View.OnTouchListener { _, event ->
            val drawableRight = 2
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (event.rawX >= binding.recordIdTxt.right - binding.recordIdTxt.compoundDrawables[drawableRight].bounds.width()) {
                    infoTouchListener?.invoke(recordItem?.record)
                }
            }
            true
        }

        @SuppressLint("ClickableViewAccessibility")
        private val hideOverlayListener = View.OnTouchListener { _, event ->
            val photoView = binding.photoViewContainer.children.firstOrNull()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (binding.junkRecordIv.visibility == View.VISIBLE) binding.junkRecordIv.gone()
                    photoView?.let {
                        when (photoView) {
                            is CropPhotoView -> {
                                photoView.crops.clear()
                                photoView.invalidate()
                            }

                            is QuadrilateralPhotoView -> {
                                photoView.quadrilaterals.clear()
                                photoView.invalidate()
                            }

                            is PolygonPhotoView -> {
                                photoView.points.clear()
                                photoView.invalidate()
                            }

                            is PaintPhotoView -> {
                                photoView.paintDataList.clear()
                                photoView.invalidate()
                            }

                            is DragSplitPhotoView -> {
                                photoView.splitCropping.clear()
                                photoView.invalidate()
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
                        val annotations = recordItem?.record.annotations()
                        recordBitmap?.let { bitmap ->
                            when (photoView) {
                                is CropPhotoView -> {
                                    photoView.crops.addAll(annotations.crops(bitmap))
                                    photoView.invalidate()
                                }

                                is QuadrilateralPhotoView -> {
                                    photoView.quadrilaterals.addAll(
                                        annotations.quadrilaterals(
                                            bitmap
                                        )
                                    )
                                    photoView.invalidate()
                                }

                                is PolygonPhotoView -> {
                                    photoView.points.addAll(annotations.polygonPoints(bitmap))
                                    photoView.invalidate()
                                }

                                is PaintPhotoView -> {
                                    photoView.paintDataList.addAll(annotations.paintDataList(bitmap = bitmap))
                                    photoView.invalidate()
                                }

                                is DragSplitPhotoView -> {
                                    photoView.splitCropping.addAll(annotations.splitCrops(bitmap))
                                    photoView.invalidate()
                                }

                                else -> {
                                    maskedBitmap?.let { maskedBitmap ->
                                        (photoView as PhotoView).setImageBitmap(maskedBitmap)
                                    }
                                }
                            }
                        }
                    }

                    setAnswer()
                }
            }
            true
        }

        private fun setupStarredStatus(showStar: Boolean) {
            if (showStar && recordItem?.record?.starred == true)
                binding.starredIv.visible() else binding.starredIv.gone()
        }

        private fun setupSniffingView(isSniffingRecord: Boolean?) {
            if ((appFlavor == "qa" || appFlavor == "dev") && isSniffingRecord == true) {
                if (recordItem?.record?.needsRejection == true) {
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

        @SuppressLint("UseCompatLoadingForDrawables")
        private fun setupSubmissionStatus() {
            if (recordItem?.isApproved == true && recordItem?.isRejected == false) {
                binding.approveBtn.gone()
                binding.resetBtn.gone()
                binding.statusImg.visible()
                binding.statusImg.setImageResource(R.drawable.ic_approved_stamp)
            } else if (recordItem?.isApproved == false && recordItem?.isRejected == true) {
                binding.approveBtn.gone()
                binding.resetBtn.gone()
                binding.statusImg.visible()
                binding.statusImg.setImageResource(R.drawable.ic_rejected_stamp)
            } else if (recordItem?.isApproved == false && recordItem?.isRejected == false) {
                binding.approveBtn.visible()
                binding.resetBtn.visible()
                binding.statusImg.gone()
            }
        }

        private fun loadGif(mediaUrl: String, photoView: PhotoView) {
            Glide.with(photoView.context)
                .asGif()
                .load(mediaUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .listener(gifRequestListener)
                .into(photoView)
        }

        private fun setupRecord(record: Record) {
            binding.photoViewContainer.removeAllViews()
            maskedBitmap = null
            recordBitmap = null
            binding.reloadIV.gone()
            binding.loaderIV.visible()
            binding.photoViewContainer.invisible()
            drawType = record.drawType()
            record.displayImage?.let { mediaUrl ->
                try {
                    when {
                        mediaUrl.contains("gif") -> {
                            val photoView = PhotoView(itemView.context)
                            photoView.colorFilter = ImageUtils.getColorMatrix(contrastValue)
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

                    setAnswer()
                } catch (e: Exception) {
                    onBitmapLoadFailed()
                    Timber.e("${e.printStackTrace()}")
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
                onBitmapReady()
                return false
            }
        }

        private val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                if (drawType != DrawType.MASK) {
                    onBitmapReady()
                }

                drawAnnotations(resource)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                super.onLoadFailed(errorDrawable)
                onBitmapLoadFailed()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                binding.photoViewContainer.removeAllViews()
            }
        }

        private fun drawAnnotations(bitmap: Bitmap) {
            maskedBitmap = null
            val annotations = recordItem?.record.annotations()
            if (annotations.isNullOrEmpty()) {
                val photoView = PhotoView(itemView.context, null)
                recordBitmap = bitmap
                loadImage(bitmap, photoView)
                setAnswer()
            } else {
                binding.junkRecordIv.gone()
                recordBitmap = bitmap
                when (drawType) {
                    DrawType.BOUNDING_BOX -> {
                        val cropView = CropPhotoView(binding.photoViewContainer.context, null)
                        cropView.reset()
                        cropView.editMode(isEnabled = false)
                        cropView.setBitmapAttributes(bitmap.width, bitmap.height)
                        cropView.crops.addAll(annotations.crops(bitmap))
                        loadImage(bitmap, cropView)
                    }

                    DrawType.QUADRILATERAL -> {
                        val quadrilateralView =
                            QuadrilateralPhotoView(binding.photoViewContainer.context, null)
                        quadrilateralView.reset()
                        quadrilateralView.editMode(isEnabled = false)
                        quadrilateralView.quadrilaterals.addAll(annotations.quadrilaterals(bitmap))
                        loadImage(bitmap, quadrilateralView)
                    }

                    DrawType.POLYGON -> {
                        val polygonView = PolygonPhotoView(binding.photoViewContainer.context, null)
                        polygonView.reset()
                        polygonView.editMode(isEnabled = false)
                        polygonView.points.addAll(annotations.polygonPoints(bitmap))
                        loadImage(bitmap, polygonView)
                    }

                    DrawType.CONNECTED_LINE -> {
                        val paintView = PaintPhotoView(binding.photoViewContainer.context, null)
                        paintView.reset()
                        paintView.editMode(isEnabled = false)
                        paintView.setBitmapAttributes(bitmap.width, bitmap.height)
                        paintView.paintDataList.addAll(annotations.paintDataList(bitmap = bitmap))
                        loadImage(bitmap, paintView)
                    }

                    DrawType.SPLIT_BOX -> {
                        val dragSplitView =
                            DragSplitPhotoView(binding.photoViewContainer.context, null)
                        dragSplitView.reset()
                        dragSplitView.editMode(isEnabled = false)
                        dragSplitView.setBitmapAttributes(bitmap.width, bitmap.height)
                        dragSplitView.splitCropping.addAll(annotations.splitCrops(bitmap))
                        loadImage(bitmap, dragSplitView)
                    }

                    DrawType.MASK -> {
                        val imageUrls = annotations.map { it.maskUrl }
                        if (imageUrls.isNullOrEmpty()) {
                            onBitmapReady()
                            val photoView = PhotoView(binding.photoViewContainer.context)
                            photoView.setOnTouchListener { _, _ -> false }
                            recordBitmap?.let { loadImage(it, photoView) }
                        } else downloadMaskBitmaps(imageUrls)
                    }

                    else -> {
                        val photoView = PhotoView(binding.photoViewContainer.context)
                        photoView.setOnTouchListener { _, _ -> false }
                        loadImage(bitmap, photoView)
                    }
                }
            }
        }

        private fun setAnswer() {
            val answer = recordItem?.record?.answer()
            binding.junkRecordIv.gone()
            when (applicationMode) {
                Mode.INPUT, Mode.LP_INPUT, Mode.MULTI_INPUT, Mode.CLASSIFY, Mode.DYNAMIC_CLASSIFY, Mode.BINARY_CLASSIFY -> {
                    if (answer.isNullOrEmpty()) binding.answerContainer.gone()
                    else if (answer == Judgment.JUNK) {
                        binding.junkRecordIv.visible()
                        binding.answerContainer.gone()
                    } else {
                        binding.answerContainer.visible()
                        binding.answerTxt.text = answer
                    }
                }

                Mode.EVENT_VALIDATION -> {
                    binding.answerContainer.gone()
                    binding.junkRecordIv.gone()
                }

                else -> {
                    if (answer == Judgment.JUNK) {
                        binding.answerContainer.visible()
                        binding.answerTxt.text = answer
                    } else binding.answerContainer.gone()
                }
            }
        }

        private fun onBitmapLoadFailed() {
            itemView.context?.let {
                binding.reloadIV.visible()
                binding.loaderIV.gone()
                binding.videoView.gone()
                binding.photoViewContainer.invisible()
            }
        }

        private fun onMaskBitmapLoadFailed() {
            itemView.context?.let {
                binding.reloadIV.gone()
                binding.loaderIV.gone()
                binding.videoView.gone()
                binding.photoViewContainer.visible()

                val photoView = PhotoView(binding.photoViewContainer.context)
                recordBitmap?.let { loadImage(it, photoView) }
            }
        }

        private fun onBitmapReady() {
            itemView.context?.let {
                binding.reloadIV.gone()
                binding.loaderIV.gone()
                binding.videoView.gone()
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
                colorFilter = ImageUtils.getColorMatrix(contrastValue)
            }
            binding.photoViewContainer.addView(view)
        }

        private fun onVideoReady(mediaUrl: String) {
            binding.photoViewContainer.gone()
            binding.parentViewController.gone()
            binding.videoView.visible()
            binding.reloadIV.gone()
            binding.loaderIV.gone()
            // Play Video in VideoView
            val mediaController = MediaController(itemView.context)
            binding.videoView.apply {
                setBackgroundColor(Color.TRANSPARENT)
                setVideoURI(Uri.parse(mediaUrl))
                setOnPreparedListener {
                    binding.videoView.layoutParams = binding.videoView.layoutParams.apply {
                        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            val mediaProportion: Float =
                                it.videoHeight.toFloat() / it.videoWidth.toFloat()
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                            height = (binding.videoView.width.toFloat() * mediaProportion).toInt()
                        } else {
                            val mediaProportion: Float =
                                it.videoWidth.toFloat() / it.videoHeight.toFloat()
                            height = ViewGroup.LayoutParams.MATCH_PARENT
                            width = (binding.videoView.height.toFloat() * mediaProportion).toInt()
                        }
                    }
                    it.start()
                    it.isLooping = true
                    it.setOnVideoSizeChangedListener { _, _, _ ->
                        binding.videoView.setMediaController(mediaController)
                        mediaController.setAnchorView(binding.videoView)
                    }
                }
            }

            setAnswer()
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
                                    onBitmapReady()
                                    recordBitmap?.let {
                                        val photoView =
                                            PhotoView(binding.photoViewContainer.context)
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
    }
}