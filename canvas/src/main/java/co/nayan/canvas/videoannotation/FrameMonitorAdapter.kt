package co.nayan.canvas.videoannotation

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.utils.*
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FrameMonitorViewBinding
import co.nayan.canvas.utils.getFormattedTimeStamp
import com.bumptech.glide.Glide

class FrameMonitorAdapter : RecyclerView.Adapter<FrameMonitorViewHolder>() {

    private val videoAnnotationDataList: MutableList<VideoAnnotationData> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameMonitorViewHolder {
        return FrameMonitorViewHolder(
            FrameMonitorViewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return videoAnnotationDataList.size
    }

    override fun onBindViewHolder(holder: FrameMonitorViewHolder, position: Int) {
        holder.onBind(videoAnnotationDataList[position])
    }

    fun add(toAdd: MutableList<VideoAnnotationData>) {
        videoAnnotationDataList.clear()
        videoAnnotationDataList.addAll(toAdd)
    }
}

class FrameMonitorViewHolder(
    val binding: FrameMonitorViewBinding
) : RecyclerView.ViewHolder(binding.root) {

    private val cropAndTagsAdapter = CropAndTagsAdapter()

    @SuppressLint("NotifyDataSetChanged")
    fun onBind(videoAnnotationData: VideoAnnotationData) {
        binding.vACropTagsView.gone()
        binding.vACropTagsView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = cropAndTagsAdapter
        }
        itemView.setTag(R.id.videoAnnotationViewTag, videoAnnotationData)
        binding.previewTimeStamp.text = videoAnnotationData.frameCount.getFormattedTimeStamp()
        if (videoAnnotationData.isJunk) binding.junkMarkerIV.visible()
        else binding.junkMarkerIV.gone()

        hideAll()
        videoAnnotationData.bitmap?.let {
            val videoAnnotations = videoAnnotationData.annotations
            when (videoAnnotations.firstOrNull()?.type) {
                DrawType.BOUNDING_BOX -> {
                    binding.vACropContainer.visible()
                    binding.vACropView.apply {
                        reset()
                        resetScale()
                        visible()
                        editMode(false)
                        setBitmapAttributes(it.width, it.height)
                        this.crops.addAll(videoAnnotations.crops(it))

                        if (isMultiTagMode()) {
                            binding.vACropTagsView.visible()
                            binding.tagsDivider.visible()
                            cropAndTagsAdapter.originalBitmap = it
                            cropAndTagsAdapter.addTags(this.crops)
                            cropAndTagsAdapter.notifyDataSetChanged()
                        } else {
                            binding.vACropTagsView.gone()
                            binding.tagsDivider.gone()
                        }

                        Glide.with(context).load(it).into(this)
                    }.invalidate()
                }

                DrawType.QUADRILATERAL -> {
                    binding.vAQuadrilateralView.apply {
                        reset()
                        resetScale()
                        visible()
                        editMode(false)
                        this.quadrilaterals.addAll(videoAnnotations.quadrilaterals(it))
                        Glide.with(context).load(it).into(this)
                    }.invalidate()
                }

                DrawType.POLYGON -> {
                    binding.vAPolygonView.apply {
                        reset()
                        resetScale()
                        visible()
                        editMode(false)
                        this.points.addAll(videoAnnotations.polygonPoints(it))
                        Glide.with(context).load(it).into(this)
                    }.invalidate()
                }

                DrawType.CONNECTED_LINE -> {
                    binding.vAPaintView.apply {
                        reset()
                        resetScale()
                        visible()
                        editMode(false)
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
                        editMode(false)
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

    private fun hideAll() {
        binding.vACropContainer.gone()
        binding.vACropView.gone()
        binding.vADragSplitView.gone()
        binding.vAPaintView.gone()
        binding.vAQuadrilateralView.gone()
        binding.vAPolygonView.gone()
        binding.vAPhotoView.gone()
    }
}