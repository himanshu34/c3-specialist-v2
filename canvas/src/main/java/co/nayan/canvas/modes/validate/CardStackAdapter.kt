package co.nayan.canvas.modes.validate

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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode.CLASSIFY
import co.nayan.c3v2.core.config.Mode.EVENT_VALIDATION
import co.nayan.c3v2.core.config.Mode.MCML
import co.nayan.c3v2.core.config.Mode.MCMT
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.crop.CropPhotoView
import co.nayan.c3views.dragsplit.DragSplitPhotoView
import co.nayan.c3views.paint.PaintPhotoView
import co.nayan.c3views.polygon.PolygonPhotoView
import co.nayan.c3views.quadrilateral.QuadrilateralPhotoView
import co.nayan.c3views.utils.*
import co.nayan.canvas.R
import co.nayan.canvas.databinding.RecordCardViewBinding
import co.nayan.canvas.utils.GifRequestListener
import co.nayan.canvas.utils.isVideo
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.github.chrisbanes.photoview.PhotoView
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import timber.log.Timber

class CardStackAdapter(
    private val applicationMode: String?,
    private val cardStackListener: CardRecordListener,
    private val infoTouchListener: ((Record?) -> Unit)? = null
) : RecyclerView.Adapter<CardStackAdapter.CardStackHolder>() {

    private val records = mutableListOf<Record>()
    private var toggledTemplate: Template? = null
    lateinit var colorFilter: ColorMatrixColorFilter
    var appFlavor: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardStackHolder {
        return CardStackHolder(
            RecordCardViewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), cardStackListener
        )
    }

    override fun getItemCount(): Int {
        return records.size
    }

    override fun onBindViewHolder(holder: CardStackHolder, position: Int) {
        holder.onBind(records[position], colorFilter, appFlavor)
    }

    fun addAll(toAdd: List<Record>): Int {
        val newRecords = toAdd.filter { records.contains(it).not() }
        records.addAll(newRecords)
        return newRecords.size
    }

    fun getItem(position: Int): Record? {
        return records[position] ?: null
    }

    fun toggleView(template: Template?) {
        toggledTemplate = template
    }

    inner class CardStackHolder(
        val binding: RecordCardViewBinding,
        private val cardStackListener: CardRecordListener
    ) : RecyclerView.ViewHolder(binding.root) {

        private var record: Record? = null
        private var drawType: String? = null
        private var colorFilter: ColorMatrixColorFilter? = null
        private var appFlavor: String? = null

        private var recordBitmap: Bitmap? = null
        private var maskedBitmap: Bitmap? = null

        fun onBind(
            record: Record,
            colorFilter: ColorMatrixColorFilter?,
            appFlavor: String?
        ) {
            this.colorFilter = colorFilter
            this.record = record
            this.appFlavor = appFlavor
            cardStackListener.setupQuestion(record.answer())
            binding.junkRecordIv.gone()
            val recordId = if (record.isSniffingRecord == true)
                record.randomSniffingId else record.id
            binding.recordIdTxt.text =
                String.format(itemView.context.getString(R.string.record_id_text), recordId)
            setupRecord()
            setupSniffingView()
            binding.reloadIV.setOnClickListener { loadImage() }
            binding.recordIdTxt.setOnTouchListener(recordInfoTouchListener)
            binding.overlayTransparentIv.setOnTouchListener(overlayTransparentTouchListener)
        }

        @SuppressLint("ClickableViewAccessibility")
        private val recordInfoTouchListener = View.OnTouchListener { _, event ->
            val drawableRight = 2
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (event.rawX >= binding.recordIdTxt.right - binding.recordIdTxt.compoundDrawables[drawableRight].bounds.width()) {
                    infoTouchListener?.invoke(record)
                    true
                }
            }
            false
        }

        @SuppressLint("ClickableViewAccessibility")
        private val overlayTransparentTouchListener = View.OnTouchListener { _, event ->
            toggleView(event.action, binding.photoViewContainer.children.firstOrNull())
            true
        }

        private fun toggleView(eventAction: Int, photoView: View?) {
            when (eventAction) {
                MotionEvent.ACTION_DOWN -> {
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
                                if (record?.answer() == Judgment.JUNK && binding.junkRecordIv.visibility == View.VISIBLE)
                                    binding.junkRecordIv.gone()
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    photoView?.let {
                        val annotations = record?.annotations() ?: emptyList()
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
                                    if (record?.answer() == Judgment.JUNK && binding.junkRecordIv.visibility == View.GONE)
                                        binding.junkRecordIv.visible()
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun setupRecord() {
            binding.photoViewContainer.removeAllViews()
            binding.videoView.gone()
            binding.reloadIV.gone()
            binding.loaderIV.visible()
            binding.photoViewContainer.invisible()
            binding.videoView.invisible()
            binding.answerTxt.gone()
            val url = if (record?.mediaType == MediaType.VIDEO)
                record?.mediaUrl
            else record?.displayImage ?: record?.mediaUrl
            url?.let { mediaUrl ->
                if (mediaUrl.isVideo()) loadVideo(mediaUrl)
                else {
                    cardStackListener.toggleContrast(true)
                    drawType = record?.drawType()
                    loadImage()
                }
            }

            if (applicationMode != EVENT_VALIDATION)
                setUpAnswer(record?.answer())
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

        private fun loadImage() {
            record?.displayImage?.let { imageUrl ->
                if (imageUrl.contains(".gif")) {
                    loadGif(imageUrl)
                } else {
                    binding.overlayTransparentIv.visible()
                    Glide.with(itemView.context)
                        .asBitmap()
                        .load(imageUrl)
                        .into(target)
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun loadGif(imageUrl: String) {
            binding.overlayTransparentIv.invisible()
            val photoView = PhotoView(itemView.context)
            colorFilter?.let { photoView.colorFilter = it }
            photoView.setOnTouchListener { _, _ -> false }
            photoView.layoutParams = getLayoutParams()
            binding.photoViewContainer.addView(photoView)

            Glide.with(itemView.context)
                .asGif()
                .listener(gifRequestListener)
                .load(imageUrl)
                .into(photoView)
        }

        private fun loadVideo(mediaUrl: String) {
            binding.reloadIV.gone()
            binding.loaderIV.gone()
            binding.overlayTransparentIv.invisible()
            binding.videoView.visible()
            cardStackListener.toggleContrast(false)
            // Play Video in VideoView
            val mediaController = MediaController(itemView.context)
            binding.videoView.apply {
                setBackgroundColor(Color.TRANSPARENT)
                setVideoURI(Uri.parse(mediaUrl))
                setOnPreparedListener {
                    val mediaProportion: Float = it.videoHeight.toFloat() / it.videoWidth.toFloat()
                    binding.videoView.layoutParams = binding.videoView.layoutParams.apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = (binding.videoView.width.toFloat() * mediaProportion).toInt()
                        Timber.e("VideoView -> [$width, $height]")
                    }
                    it.start()
                    it.isLooping = true
                    it.setOnVideoSizeChangedListener { _, _, _ ->
                        this.setMediaController(mediaController)
                        mediaController.setAnchorView(this)
                    }
                }
            }
        }

        private fun setUpAnswer(answer: String?) {
            if (answer.isNullOrEmpty()) {
                binding.junkRecordIv.gone()
                binding.answerTxt.gone()
            } else {
                if (answer == Judgment.JUNK) {
                    binding.junkRecordIv.visible()
                    binding.answerTxt.gone()
                } else {
                    binding.junkRecordIv.gone()
                    binding.answerTxt.text = answer
                    when (applicationMode) {
                        CLASSIFY -> binding.answerTxt.visible()
                        else -> binding.answerTxt.gone()
                    }
                }
            }
        }

        private val gifRequestListener = object : GifRequestListener() {
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
                if (drawType != DrawType.MASK) onBitmapLoadSuccess()
                drawAnnotations(resource)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                super.onLoadFailed(errorDrawable)
                onBitmapLoadFailed()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                Timber.e("In Load Cleared ")
            }
        }

        private fun drawAnnotations(bitmap: Bitmap) {
            maskedBitmap = null
            recordBitmap = null
            val annotations = record?.annotations()
            if (annotations.isNullOrEmpty()) {
                val photoView = PhotoView(itemView.context, null)
                val mBitmap = if (record?.answer() == Judgment.JUNK) {
                    binding.junkRecordIv.visible()
                    bitmap
                } else {
                    binding.junkRecordIv.gone()
                    if (applicationMode != EVENT_VALIDATION)
                        bitmap.scaledBitmap().mergedBitmap(record?.answer())
                    else bitmap
                }
                recordBitmap = mBitmap
                loadBitmap(mBitmap, photoView)
            } else {
                val filteredAnnotations = if (toggledTemplate == null) annotations
                else {
                    when (record?.applicationMode) {
                        MCML -> annotations.filter { it.input == toggledTemplate?.templateName }
                        MCMT -> annotations.filter { it.tags?.contains(toggledTemplate?.templateName) == true }
                        else -> annotations
                    }
                }

                binding.junkRecordIv.gone()
                recordBitmap = bitmap
                when (drawType) {
                    DrawType.BOUNDING_BOX -> {
                        val cropView = CropPhotoView(itemView.context, null)
                        cropView.editMode(false)
                        cropView.setBitmapAttributes(bitmap.width, bitmap.height)
                        cropView.crops.addAll(filteredAnnotations.crops(bitmap))
                        loadBitmap(bitmap, cropView)
                    }

                    DrawType.QUADRILATERAL -> {
                        val quadrilateralView = QuadrilateralPhotoView(itemView.context, null)
                        quadrilateralView.editMode(false)
                        quadrilateralView.quadrilaterals.addAll(
                            filteredAnnotations.quadrilaterals(
                                bitmap
                            )
                        )
                        loadBitmap(bitmap, quadrilateralView)
                    }

                    DrawType.POLYGON -> {
                        val polygonView = PolygonPhotoView(itemView.context, null)
                        polygonView.editMode(false)
                        polygonView.points.addAll(filteredAnnotations.polygonPoints(bitmap))
                        loadBitmap(bitmap, polygonView)
                    }

                    DrawType.CONNECTED_LINE -> {
                        val paintView = PaintPhotoView(itemView.context, null)
                        paintView.editMode(false)
                        paintView.setBitmapAttributes(bitmap.width, bitmap.height)
                        paintView.paintDataList.addAll(filteredAnnotations.paintDataList(bitmap = bitmap))
                        loadBitmap(bitmap, paintView)
                    }

                    DrawType.SPLIT_BOX -> {
                        val dragSplitView = DragSplitPhotoView(itemView.context, null)
                        dragSplitView.editMode(false)
                        dragSplitView.setBitmapAttributes(bitmap.width, bitmap.height)
                        dragSplitView.splitCropping.addAll(filteredAnnotations.splitCrops(bitmap))
                        loadBitmap(bitmap, dragSplitView)
                    }

                    DrawType.MASK -> {
                        val imageUrls = filteredAnnotations.map { it.maskUrl }
                        if (imageUrls.isNullOrEmpty()) {
                            onBitmapLoadSuccess()
                            val photoView = PhotoView(binding.photoViewContainer.context)
                            recordBitmap?.let { loadBitmap(it, photoView) }
                        } else downloadMaskBitmaps(imageUrls)
                    }

                    else -> {
                        val photoView = PhotoView(itemView.context, null)
                        loadBitmap(bitmap, photoView)
                    }
                }
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
                                    //makeTransparent.visible()
                                    recordBitmap?.let {
                                        val photoView =
                                            PhotoView(binding.photoViewContainer.context)
                                        maskedBitmap = it.overlayBitmap(bitmaps)
                                        maskedBitmap?.let { maskedBitmap ->
                                            loadBitmap(maskedBitmap, photoView)
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

        private fun onMaskBitmapLoadFailed() {
            itemView.context?.let {
                binding.loaderIV.gone()
                binding.reloadIV.gone()
                binding.photoViewContainer.visible()
            }

            val photoView = PhotoView(binding.photoViewContainer.context)
            recordBitmap?.let { loadBitmap(it, photoView) }
        }

        private fun onBitmapLoadFailed() {
            itemView.context?.let {
                binding.loaderIV.gone()
                binding.reloadIV.visible()
                binding.photoViewContainer.invisible()
            }
        }

        private fun onBitmapLoadSuccess() {
            itemView.context?.let {
                binding.loaderIV.gone()
                binding.reloadIV.gone()
                binding.photoViewContainer.visible()
            }
        }

        private fun loadBitmap(bitmap: Bitmap, view: PhotoView) {
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            view.layoutParams = layoutParams
            colorFilter?.let { view.colorFilter = it }
            view.setImageBitmap(bitmap)
            binding.photoViewContainer.addView(view)
        }

        private fun getLayoutParams() = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
}

interface CardRecordListener {
    fun setupQuestion(answer: String?)
    fun toggleContrast(status: Boolean? = true)
}