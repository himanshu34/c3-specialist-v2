package co.nayan.canvas.sandbox

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.crop.CropPhotoView
import co.nayan.c3views.dragsplit.DragSplitPhotoView
import co.nayan.c3views.paint.PaintPhotoView
import co.nayan.c3views.polygon.PolygonPhotoView
import co.nayan.c3views.quadrilateral.QuadrilateralPhotoView
import co.nayan.c3views.utils.*
import co.nayan.canvas.R
import co.nayan.canvas.databinding.ListSandboxRecordBinding
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
import timber.log.Timber
import java.util.*

class SandboxRecordsAdapter(
    private val appFlavor: String?,
    private val applicationMode: String?,
    colorContrast: ColorMatrixColorFilter?,
    val itemClick: ((Record) -> Unit)? = null
) : RecyclerView.Adapter<SandboxRecordsAdapter.RecordViewHolder>() {

    private val recordItems = mutableListOf<Record>()
    private val filteredItems = mutableListOf<Record>()
    var showOverlay = true
    var contrast: ColorMatrixColorFilter? = null

    init {
        this.contrast = colorContrast
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SandboxRecordsAdapter.RecordViewHolder {
        return RecordViewHolder(
            ListSandboxRecordBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.onBind(filteredItems[position], position)
    }

    override fun getItemCount(): Int {
        return filteredItems.size
    }

    fun addAll(toAdd: List<Record>) {
        recordItems.clear()
        recordItems.addAll(toAdd)

        filterListItems()
    }

    fun filterListItems(query: String? = null): List<Record> {
        val searchQuery = query?.lowercase(Locale.getDefault())
        filteredItems.clear()
        val items = if (searchQuery.isNullOrEmpty().not()) {
            recordItems.filter {
                it.id.toString().contains(searchQuery!!)
            }
        } else recordItems

        filteredItems.addAll(items)
        notifyDataSetChanged()

        return items
    }

    fun refreshRecord(recordId: Int?, annotations: List<AnnotationObjectsAttribute>?) {
        filteredItems.forEachIndexed { index, record ->
            if (record.id == recordId) {
                record.annotation = annotations
                notifyItemChanged(index)
                return@forEachIndexed
            }
        }
    }

    inner class RecordViewHolder(
        val binding: ListSandboxRecordBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

//        private val parentViewContainer = itemView.parentViewContainer
//        private val confidenceScoreTxt = itemView.confidenceScoreTxt
//        private val answerTxt = itemView.answerTxt
//        private val reloadIv = itemView.reloadIV
//        private val loaderIv = itemView.loaderIV
//        private val photoViewContainer = itemView.photoViewContainer
//        private val junkRecordIv = itemView.junkRecordIv
//        private var videoRecordContainer = itemView.videoViewContainer
//        private var recordIdTxt = itemView.recordIdTxt

        private var record: Record? = null
        private var drawType: String? = null

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View?) {
            itemClick?.invoke(filteredItems[layoutPosition])
        }

        fun onBind(
            record: Record?,
            position: Int
        ) {
            this.record = record

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

            itemView.setTag(R.id.record, record)
            itemView.setTag(R.id.position, position)

            binding.reloadIV.setOnClickListener { setupRecord() }
        }

        private fun setupSandboxView() {
            if (record?.annotation.isNullOrEmpty())
                binding.parentViewContainer.apply {
                    setBackgroundColor(
                        ContextCompat.getColor(
                            this.context,
                            R.color.false_button_enabled
                        )
                    )
                }
            else binding.parentViewContainer.setBackgroundColor(Color.TRANSPARENT)
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
            setupSandboxView()
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setupRecord() {
            binding.reloadIV.gone()
            binding.loaderIV.visible()
            binding.photoViewContainer.invisible()
            binding.recordIdTxt.text =
                String.format(itemView.context.getString(R.string.record_id_text), record?.id)
            drawType = record?.annotation?.drawType()
            record?.displayImage?.let { mediaUrl ->
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
                            Glide.with(itemView.context)
                                .asGif()
                                .load(mediaUrl)
                                .listener(gifRequestListener)
                                .into(photoView)

                            setUpAnswers()
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

        private fun setUpAnswers() {
            if (applicationMode == Mode.EVENT_VALIDATION) {
                binding.answerTxt.gone()
                binding.junkRecordIv.gone()
            } else {
                val answer = record?.answer(true)
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
                binding.photoViewContainer.removeAllViews()
            }
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
                setupSandboxView()
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setupAnswer(bitmap: Bitmap) {
            val annotations = record?.annotations(true)
            if (annotations.isNullOrEmpty() || showOverlay.not()) {
                val photoView = PhotoView(binding.photoViewContainer.context)
                photoView.setOnTouchListener { _, _ -> false }
//                makeTransparent.gone()
                loadImage(bitmap, photoView)

                if (showOverlay && applicationMode != Mode.EVENT_VALIDATION) {
                    val answer = record?.answer(true)
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
//                        makeTransparent.visibility = cropView.getTransparentVisibility()
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
                        val dragSplitView =
                            DragSplitPhotoView(binding.photoViewContainer.context, null)
                        dragSplitView.reset()
                        dragSplitView.touchEnabled(false)
                        dragSplitView.setBitmapAttributes(bitmap.width, bitmap.height)
                        dragSplitView.splitCropping.addAll(annotations.splitCrops(bitmap))
//                        makeTransparent.visibility = dragSplitView.getTransparentVisibility()
                        loadImage(bitmap, dragSplitView)
                    }

                    else -> {
                        val photoView = PhotoView(binding.photoViewContainer.context)
                        photoView.setOnTouchListener { _, _ -> false }
//                        makeTransparent.gone()
                        loadImage(bitmap, photoView)
                    }
                }
            }

            when (applicationMode) {
                Mode.INPUT, Mode.LP_INPUT, Mode.MULTI_INPUT, Mode.CLASSIFY, Mode.DYNAMIC_CLASSIFY -> {
                    val answer = record?.answer(true)
                    if (answer.isNullOrEmpty().not() && answer != Judgment.JUNK) {
                        binding.answerTxt.visible()
                        binding.answerTxt.text = answer
                    } else binding.answerTxt.gone()
                }

                else -> binding.answerTxt.gone()
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
}