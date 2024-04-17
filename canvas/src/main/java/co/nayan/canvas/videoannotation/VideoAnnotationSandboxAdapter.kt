package co.nayan.canvas.videoannotation

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.SandboxVideoAnnotationData
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.utils.*
import co.nayan.canvas.R
import co.nayan.canvas.databinding.SandboxVideoAnnotationBinding

class VideoAnnotationSandboxAdapter : RecyclerView.Adapter<VideoAnnotationSandBoxView>() {

    private val sandboxResult = mutableListOf<SandboxVideoAnnotationData>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoAnnotationSandBoxView {
        return VideoAnnotationSandBoxView(
            SandboxVideoAnnotationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return sandboxResult.size
    }

    override fun onBindViewHolder(holder: VideoAnnotationSandBoxView, position: Int) {
        holder.bind(sandboxResult[position])
    }

    fun add(sandboxResult: MutableList<SandboxVideoAnnotationData>) {
        this.sandboxResult.clear()
        this.sandboxResult.addAll(sandboxResult)

        notifyDataSetChanged()
    }
}

class VideoAnnotationSandBoxView(val binding: SandboxVideoAnnotationBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val correctCropAndTagsAdapter = CropAndTagsAdapter(isLandscape = true)
    private val userCropAndTagsAdapter = CropAndTagsAdapter(isLandscape = true)
    private val correctAnnotation = ContextCompat.getColor(itemView.context, R.color.light_green)
    private val wrongAnnotation = ContextCompat.getColor(itemView.context, R.color.light_red)
    private val wrongTag = ContextCompat.getDrawable(itemView.context, R.drawable.wrong_tag)
    private val correctTag = ContextCompat.getDrawable(itemView.context, R.drawable.correct_tag)

    fun bind(sandboxVideoAnnotationData: SandboxVideoAnnotationData) {
        setupAdapters()
        setupCorrectAnnotation(sandboxVideoAnnotationData.correctVideoAnnotation)
        if (sandboxVideoAnnotationData.userVideoAnnotation == null) {
            binding.missingAnnotationIV.visible()
            binding.userAnnotationContainer.gone()
        } else binding.missingAnnotationIV.gone()

        sandboxVideoAnnotationData.userVideoAnnotation?.let {
            setupUserAnnotation(it, sandboxVideoAnnotationData.judgement)
        }
    }

    private fun setupAdapters() {
        binding.correctCropContainer.gone()
        binding.correctCropTagsView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = correctCropAndTagsAdapter
        }

        binding.userCropContainer.gone()
        binding.userCropTagsView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userCropAndTagsAdapter
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupCorrectAnnotation(correctVideoAnnotation: VideoAnnotationData) {
        if (correctVideoAnnotation.isJunk) binding.junkMarkerIV.visible()
        else binding.junkMarkerIV.gone()

        correctVideoAnnotation.bitmap?.let {
            when (correctVideoAnnotation.annotations.firstOrNull()?.type) {
                DrawType.BOUNDING_BOX -> {
                    binding.correctCropView.reset()
                    binding.correctCropView.resetScale()
                    binding.correctCropContainer.visible()
                    binding.correctCropView.editMode(false)
                    binding.correctCropView.setBitmapAttributes(
                        it.width,
                        it.height
                    )
                    binding.correctCropView.crops.addAll(
                        correctVideoAnnotation.annotations.crops(it)
                    )

                    if (binding.correctCropView.isMultiTagMode()) {
                        binding.correctCropTagsView.visible()
                        correctCropAndTagsAdapter.originalBitmap = it
                        correctCropAndTagsAdapter.addTags(binding.correctCropView.crops)
                        correctCropAndTagsAdapter.notifyDataSetChanged()
                    } else binding.correctCropTagsView.gone()
                    binding.correctCropView.setImageBitmap(it)
                }

                DrawType.QUADRILATERAL -> {
                    binding.correctQuadrilateralView.reset()
                    binding.correctQuadrilateralView.resetScale()
                    binding.correctQuadrilateralView.visible()
                    binding.correctQuadrilateralView.editMode(false)
                    binding.correctQuadrilateralView.quadrilaterals.addAll(
                        correctVideoAnnotation.annotations.quadrilaterals(it)
                    )
                    binding.correctQuadrilateralView.setImageBitmap(it)
                }

                DrawType.POLYGON -> {
                    binding.correctPolygonView.reset()
                    binding.correctPolygonView.resetScale()
                    binding.correctPolygonView.visible()
                    binding.correctPolygonView.editMode(false)
                    binding.correctPolygonView.points.addAll(
                        correctVideoAnnotation.annotations.polygonPoints(it)
                    )
                    binding.correctPolygonView.setImageBitmap(it)
                }

                DrawType.CONNECTED_LINE -> {
                    binding.correctPaintView.reset()
                    binding.correctPaintView.resetScale()
                    binding.correctPaintView.visible()
                    binding.correctPaintView.editMode(false)
                    binding.correctPaintView.setBitmapAttributes(
                        it.width,
                        it.height
                    )
                    binding.correctPaintView.paintDataList.addAll(
                        correctVideoAnnotation.annotations
                            .paintDataList(bitmap = it)
                    )
                    binding.correctPaintView.setImageBitmap(it)
                }

                DrawType.SPLIT_BOX -> {
                    binding.correctDragSplitView.reset()
                    binding.correctDragSplitView.resetScale()
                    binding.correctDragSplitView.visible()
                    binding.correctDragSplitView.editMode(false)
                    binding.correctDragSplitView.setBitmapAttributes(
                        it.width, it.height
                    )
                    binding.correctDragSplitView.splitCropping
                        .addAll(correctVideoAnnotation.annotations.splitCrops(it))
                    binding.correctDragSplitView.setImageBitmap(it)
                }

                else -> {
                    binding.correctPhotoView.visible()
                    binding.correctPhotoView.setImageBitmap(it)
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupUserAnnotation(
        userAnnotation: VideoAnnotationData,
        judgement: Boolean
    ) {
        binding.userAnnotationContainer.visible()
        if (judgement) {
            binding.userAnnotationContainer.setBackgroundColor(correctAnnotation)
            binding.userJudgementMarkerIV.setImageDrawable(correctTag)
        } else {
            binding.userAnnotationContainer.setBackgroundColor(wrongAnnotation)
            binding.userJudgementMarkerIV.setImageDrawable(wrongTag)
        }
        userAnnotation.bitmap?.let {
            when (userAnnotation.annotations.firstOrNull()?.type) {
                DrawType.BOUNDING_BOX -> {
                    binding.userCropView.reset()
                    binding.userCropView.resetScale()
                    binding.userCropContainer.visible()
                    binding.userCropView.editMode(false)
                    binding.userCropView.setBitmapAttributes(
                        it.width,
                        it.height
                    )
                    binding.userCropView.crops.addAll(
                        userAnnotation.annotations
                            .crops(it)
                    )

                    if (binding.userCropView.isMultiTagMode()) {
                        binding.userCropTagsView.visible()
                        userCropAndTagsAdapter.originalBitmap = it
                        userCropAndTagsAdapter.addTags(binding.userCropView.crops)
                        userCropAndTagsAdapter.notifyDataSetChanged()
                    } else {
                        binding.userCropTagsView.gone()
                    }
                    binding.userCropView.setImageBitmap(it)
                }

                DrawType.QUADRILATERAL -> {
                    binding.userQuadrilateralView.reset()
                    binding.userQuadrilateralView.resetScale()
                    binding.userQuadrilateralView.visible()
                    binding.userQuadrilateralView.editMode(false)
                    binding.userQuadrilateralView.quadrilaterals.addAll(
                        userAnnotation.annotations.quadrilaterals(it)
                    )
                    binding.userQuadrilateralView.setImageBitmap(it)
                }

                DrawType.POLYGON -> {
                    binding.userPolygonView.reset()
                    binding.userPolygonView.resetScale()
                    binding.userPolygonView.visible()
                    binding.userPolygonView.editMode(false)
                    binding.userPolygonView.points.addAll(
                        userAnnotation.annotations.polygonPoints(it)
                    )
                    binding.userPolygonView.setImageBitmap(it)
                }

                DrawType.CONNECTED_LINE -> {
                    binding.userPaintView.reset()
                    binding.userPaintView.resetScale()
                    binding.userPaintView.visible()
                    binding.userPaintView.editMode(false)
                    binding.userPaintView.setBitmapAttributes(
                        it.width,
                        it.height
                    )
                    binding.userPaintView.paintDataList.addAll(
                        userAnnotation.annotations
                            .paintDataList(bitmap = it)
                    )
                    binding.userPaintView.setImageBitmap(it)
                }

                DrawType.SPLIT_BOX -> {
                    binding.userDragSplitView.reset()
                    binding.userDragSplitView.resetScale()
                    binding.userDragSplitView.visible()
                    binding.userDragSplitView.editMode(false)
                    binding.userDragSplitView.setBitmapAttributes(
                        it.width, it.height
                    )
                    binding.userDragSplitView.splitCropping
                        .addAll(userAnnotation.annotations.splitCrops(it))
                    binding.userDragSplitView.setImageBitmap(it)
                }

                else -> {
                    binding.userPhotoView.visible()
                    binding.userPhotoView.setImageBitmap(it)
                }
            }
        }
    }
}