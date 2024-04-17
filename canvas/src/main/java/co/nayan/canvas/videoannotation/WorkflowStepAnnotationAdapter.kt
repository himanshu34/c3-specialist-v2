package co.nayan.canvas.videoannotation

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.canvas.databinding.PreviewWorkflowStepBinding

class WorkflowStepAnnotationAdapter(
    private val onVideoAnnotationClickListener: OnVideoAnnotationClickListener
) : RecyclerView.Adapter<WorkflowStepAnnotationViewHolder>() {

    private var videoMode: Int = -1
    private val annotationStepList = mutableListOf<MutableList<VideoAnnotationData>>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): WorkflowStepAnnotationViewHolder {
        return WorkflowStepAnnotationViewHolder(
            PreviewWorkflowStepBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onVideoAnnotationClickListener, videoMode
        )
    }

    override fun getItemCount(): Int {
        return annotationStepList.size
    }

    override fun onBindViewHolder(holder: WorkflowStepAnnotationViewHolder, position: Int) {
        val updateBgColor = position % 2 == 0
        holder.bind(annotationStepList[position], updateBgColor)
    }

    fun add(previewFrames: MutableList<MutableList<VideoAnnotationData>>) {
        annotationStepList.clear()
        annotationStepList.addAll(previewFrames)
    }

    fun setVideoAnnotationViewAdapterMode(videoMode: Int) {
        this.videoMode = videoMode
    }

}

class WorkflowStepAnnotationViewHolder(
    val binding: PreviewWorkflowStepBinding,
    onVideoAnnotationClickListener: OnVideoAnnotationClickListener,
    videoMode: Int
) : RecyclerView.ViewHolder(binding.root) {

    private val videoAnnotationViewAdapter =
        VideoAnnotationViewAdapter(onVideoAnnotationClickListener)

    init {
        val linearLayoutManager = LinearLayoutManager(itemView.context)
        linearLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
        binding.previewAnnotationStepList.layoutManager = linearLayoutManager
        binding.previewAnnotationStepList.adapter = videoAnnotationViewAdapter
        videoAnnotationViewAdapter.setVideoAnnotationViewAdapterMode(videoMode)
    }

    fun bind(
        stepAnnotationList: MutableList<VideoAnnotationData>,
        updateBgColor: Boolean
    ) {
//        view.previewAnnotationStepList.setBackgroundColor(translucentWhite)
        videoAnnotationViewAdapter.add(stepAnnotationList)
        videoAnnotationViewAdapter.notifyDataSetChanged()
    }
}