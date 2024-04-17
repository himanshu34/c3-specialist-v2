package co.nayan.canvas.videoannotation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.utils.*
import co.nayan.canvas.R
import co.nayan.canvas.databinding.ChildVideoAnnotationViewBinding
import co.nayan.canvas.databinding.VideoAnnotationViewBinding
import co.nayan.canvas.utils.getFormattedTimeStamp
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_SANDBOX
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_VIDEO_VALIDATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_SANDBOX
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_VIDEO_VALIDATION
import com.bumptech.glide.Glide

class VideoAnnotationViewAdapter(
    onVideoAnnotationClickListener: OnVideoAnnotationClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var videoMode: Int = -1
    private val videoAnnotationDataList: MutableList<VideoAnnotationData> = mutableListOf()

    private val onClickListener = View.OnClickListener {
        val selectedFrame = it.getTag(R.id.videoAnnotationViewTag) as VideoAnnotationData
        videoAnnotationDataList.forEach { videoFrame ->
            videoFrame.selected = videoFrame == selectedFrame
        }
        notifyDataSetChanged()
        onVideoAnnotationClickListener.onClick(selectedFrame)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            CHILD_VIDEO_ANNOTATION -> {
                ChildVideoAnnotationViewHolder(
                    ChildVideoAnnotationViewBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ), onClickListener
                )
            }
            else -> {
                VideoAnnotationViewHolder(
                    VideoAnnotationViewBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ), onClickListener
                )
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (videoMode) {
            CHILD_STEP_SANDBOX,
            CHILD_STEP_VIDEO_ANNOTATION,
            CHILD_STEP_VIDEO_VALIDATION -> CHILD_VIDEO_ANNOTATION
            PARENT_STEP_SANDBOX,
            PARENT_STEP_VIDEO_VALIDATION,
            PARENT_STEP_VIDEO_ANNOTATION -> PARENT_VIDEO_ANNOTATION
            else -> PARENT_VIDEO_ANNOTATION
        }
    }

    override fun getItemCount(): Int {
        return videoAnnotationDataList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChildVideoAnnotationViewHolder -> {
                holder.onBind(videoAnnotationDataList[position], position)
            }
            is VideoAnnotationViewHolder -> {
                holder.onBind(videoAnnotationDataList[position])
            }
        }
    }

    fun add(toAdd: List<VideoAnnotationData>) {
        videoAnnotationDataList.clear()
        videoAnnotationDataList.addAll(toAdd)
    }

    fun clear() {
        videoAnnotationDataList.clear()
        notifyDataSetChanged()
    }

    fun setVideoAnnotationViewAdapterMode(videoMode: Int) {
        this.videoMode = videoMode
    }

    companion object {
        const val PARENT_VIDEO_ANNOTATION = 1
        const val CHILD_VIDEO_ANNOTATION = 2
    }
}

interface OnVideoAnnotationClickListener {
    fun onClick(videoAnnotationData: VideoAnnotationData)
}

class ChildVideoAnnotationViewHolder(
    val binding: ChildVideoAnnotationViewBinding,
    onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    private val parentAnnotationColor =
        ContextCompat.getColor(itemView.context, R.color.translucentOverlay)
    private val childAnnotationColor =
        ContextCompat.getColor(itemView.context, R.color.translucent_yellow)
    private val defaultFrame = ContextCompat.getDrawable(itemView.context, R.drawable.default_frame)
    private val stepIdTxt = itemView.context.getString(R.string.step_id_text)

    init {
        itemView.setOnClickListener(onClickListener)
    }

    fun onBind(
        videoAnnotationData: VideoAnnotationData,
        position: Int
    ) {
        itemView.setTag(R.id.videoAnnotationViewTag, videoAnnotationData)
        binding.previewTimeStamp.text = videoAnnotationData.frameCount.getFormattedTimeStamp()
        val stepIndicator = String.format(stepIdTxt, position + 1)
        binding.stepIdTxt.text = stepIndicator
        hideAll()

        if (videoAnnotationData.isParent) binding.vAHolderBg.setBackgroundColor(
            parentAnnotationColor
        )
        else binding.vAHolderBg.setBackgroundColor(childAnnotationColor)

        if (videoAnnotationData.isJunk) binding.junkMarkerIV.visible()
        else binding.junkMarkerIV.gone()

        if (videoAnnotationData.bitmap == null) {
            binding.vAPhotoView.visible()
            binding.vAPhotoView.setImageDrawable(defaultFrame)
            binding.vAPhotoView.setOnTouchListener { _, _ -> false }
        } else {
            videoAnnotationData.bitmap?.let {
                val videoAnnotations = videoAnnotationData.annotations
                when (videoAnnotations.firstOrNull()?.type) {
                    DrawType.BOUNDING_BOX -> {
                        binding.vACropView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            setBitmapAttributes(it.width, it.height)
                            this.crops.addAll(videoAnnotations.crops(it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    DrawType.QUADRILATERAL -> {
                        binding.vAQuadrilateralView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            this.quadrilaterals.addAll(videoAnnotations.quadrilaterals(it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    DrawType.POLYGON -> {
                        binding.vAPolygonView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            this.points.addAll(videoAnnotations.polygonPoints(it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    DrawType.CONNECTED_LINE -> {
                        binding.vAPaintView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            setBitmapAttributes(it.width, it.height)
                            this.paintDataList.addAll(videoAnnotations.paintDataList(bitmap = it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    DrawType.SPLIT_BOX -> {
                        binding.vADragSplitView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            setBitmapAttributes(it.width, it.height)
                            this.splitCropping.addAll(videoAnnotations.splitCrops(it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    else -> {
                        binding.vAPhotoView.apply {
                            visible()
                            setImageBitmap(it)
                        }.invalidate()
                    }
                }
            }
        }
    }

    private fun hideAll() {
        binding.vACropView.gone()
        binding.vADragSplitView.gone()
        binding.vAPaintView.gone()
        binding.vAQuadrilateralView.gone()
        binding.vAPolygonView.gone()
        binding.vAPhotoView.gone()
    }
}

class VideoAnnotationViewHolder(
    val binding: VideoAnnotationViewBinding,
    onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    private val selectedFrameColor = ContextCompat.getColor(itemView.context, R.color.colorAccent)
    private val translucentColor =
        ContextCompat.getColor(itemView.context, R.color.translucentOverlay)
    private val defaultFrame = ContextCompat.getDrawable(itemView.context, R.drawable.default_frame)

    init {
        itemView.setOnClickListener(onClickListener)
    }

    fun onBind(
        videoAnnotationData: VideoAnnotationData
    ) {
        itemView.setTag(R.id.videoAnnotationViewTag, videoAnnotationData)
        binding.previewTimeStamp.text = videoAnnotationData.frameCount.getFormattedTimeStamp()
        hideAll()

        if (videoAnnotationData.selected) binding.vAHolderBg.setBackgroundColor(selectedFrameColor)
        else binding.vAHolderBg.setBackgroundColor(translucentColor)

        if (videoAnnotationData.bitmap == null) {
            binding.vAPhotoView.visible()
            binding.vAPhotoView.setImageDrawable(defaultFrame)
            binding.vAPhotoView.setOnTouchListener { _, _ -> false }
        } else {
            videoAnnotationData.bitmap?.let {
                val videoAnnotations = videoAnnotationData.annotations
                when (videoAnnotations.firstOrNull()?.type) {
                    DrawType.BOUNDING_BOX -> {
                        binding.vACropView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            setBitmapAttributes(it.width, it.height)
                            this.crops.addAll(videoAnnotations.crops(it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    DrawType.QUADRILATERAL -> {
                        binding.vAQuadrilateralView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            this.quadrilaterals.addAll(videoAnnotations.quadrilaterals(it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    DrawType.POLYGON -> {
                        binding.vAPolygonView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            this.points.addAll(videoAnnotations.polygonPoints(it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    DrawType.CONNECTED_LINE -> {
                        binding.vAPaintView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            setBitmapAttributes(it.width, it.height)
                            this.paintDataList.addAll(videoAnnotations.paintDataList(bitmap = it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    DrawType.SPLIT_BOX -> {
                        binding.vADragSplitView.apply {
                            reset()
                            resetScale()
                            visible()
                            touchEnabled(false)
                            setBitmapAttributes(it.width, it.height)
                            this.splitCropping.addAll(videoAnnotations.splitCrops(it))
                            Glide.with(context).load(it).into(this)
                        }.invalidate()
                    }

                    else -> {
                        binding.vAPhotoView.apply {
                            visible()
                            setImageBitmap(it)
                        }.invalidate()
                    }
                }
            }
        }
    }

    private fun hideAll() {
        binding.vACropView.gone()
        binding.vADragSplitView.gone()
        binding.vAPaintView.gone()
        binding.vAQuadrilateralView.gone()
        binding.vAPolygonView.gone()
        binding.vAPhotoView.gone()
    }
}


