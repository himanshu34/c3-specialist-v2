package co.nayan.canvas.widgets

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3views.utils.crop
import co.nayan.canvas.databinding.InterpolationLabelRowBinding
import co.nayan.canvas.utils.extractBitmap

class InterpolationLabelAdapter(
    private val originalBitmap: Bitmap,
    private val annotationDataList: MutableList<AnnotationData>,
    private val callback: ((AnnotationData) -> Unit)? = null
) : RecyclerView.Adapter<InterpolationLabelAdapter.InterpolationLabelViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): InterpolationLabelViewHolder {
        return InterpolationLabelViewHolder(
            InterpolationLabelRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = annotationDataList.size

    override fun onBindViewHolder(holder: InterpolationLabelViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class InterpolationLabelViewHolder(
        val binding: InterpolationLabelRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            val annotationData = annotationDataList[position]
            binding.interpolationColorImage.apply {
                val actualCropping = annotationData.crop(originalBitmap)
                val extractedBitmap = originalBitmap.extractBitmap(actualCropping)
                extractedBitmap?.let {
                    this.setImageBitmap(it)
//                    setColorFilter(Color.parseColor(annotationData.paintColor))
                }
            }

            itemView.setOnClickListener {
                callback?.invoke(annotationData)
            }
        }
    }
}