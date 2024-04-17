package co.nayan.canvas.modes.crop

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.crop.Cropping
import co.nayan.canvas.R
import co.nayan.canvas.databinding.TemplateRowBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class LabelsAdapter(
    private val onLabelSelectionListener: LabelSelectionListener
) : RecyclerView.Adapter<LabelsAdapter.LabelsViewHolder>() {

    var selectedPosition = RecyclerView.NO_POSITION
    private val templates = mutableListOf<Template>()
    private val filterTemplates = mutableListOf<Template>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LabelsViewHolder {
        return LabelsViewHolder(
            TemplateRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = filterTemplates.size

    override fun onBindViewHolder(holder: LabelsViewHolder, position: Int) {
        val isSelected = (selectedPosition == position)
        val template = filterTemplates[position]
        holder.binding.templateNameTxt.text = template.templateName
        val annotationCount = template.annotationCount
        if (annotationCount > 0) {
            holder.binding.templateAnnotationCountTxt.visible()
            holder.binding.templateAnnotationCountTxt.text =
                String.format(
                    holder.binding.templateAnnotationCountTxt.context.getString(R.string.boxes),
                    template.annotationCount
                )
        } else holder.binding.templateAnnotationCountTxt.gone()
        holder.loadIcon(template.templateIcon)

        if (isSelected) holder.binding.templateView.selected()
        else holder.binding.templateView.unSelected()

        if (template.isClicked)
            holder.binding.templateView.setCardBackgroundColor(
                ContextCompat.getColor(
                    holder.binding.templateView.context,
                    R.color.green
                )
            )
        else holder.binding.templateView.setCardBackgroundColor(null)

        holder.itemView.setOnClickListener(onItemClickListener)
        holder.itemView.setTag(R.id.template, template)

        holder.binding.reloadIV.setOnClickListener {
            holder.loadIcon(template.templateIcon)
        }
    }

    fun addAll(toAdd: List<Template>) {
        filterTemplates.clear()
        templates.clear()
        filterTemplates.addAll(toAdd)
        templates.addAll(toAdd)
        notifyDataSetChanged()
    }

    fun filterTemplates(dataPrefix: String) {
        selectedPosition = RecyclerView.NO_POSITION
        filterTemplates.clear()
        if (dataPrefix.isNotEmpty()) {
            val charPrefix = dataPrefix.first()
            val filteredData = templates.filter { template ->
                val words = template.templateName.split(" ")
                val filteredWords = words.filter {
                    it.isNotEmpty() && it.first().equals(charPrefix, ignoreCase = true)
                }
                filteredWords.isNotEmpty()
            }
            filterTemplates.addAll(filteredData)
        }
        notifyDataSetChanged()
    }

    fun updateAdapter(template: Template?) {
        val isNoTemplateSelected = (template == null)
        selectedPosition = if (isNoTemplateSelected)
            RecyclerView.NO_POSITION else filterTemplates.indexOf(template)
        notifyDataSetChanged()
    }

    fun resetViews() {
        selectedPosition = RecyclerView.NO_POSITION
        filterTemplates.forEach { template ->
            template.annotationCount = 0
            template.isClicked = false
            template.isSelected = false
        }
        notifyDataSetChanged()
    }

    fun updateAnnotations(crops: MutableList<Cropping>) {
        filterTemplates.forEach { template ->
            val filteredTemplates =
                crops.filter { it.input.equals(template.templateName, ignoreCase = true) }
            template.annotationCount = filteredTemplates.count()
            template.isClicked = filteredTemplates.isNullOrEmpty().not()
        }

        notifyDataSetChanged()
    }

    fun updateAnnotationsForValidation(annotations: List<AnnotationData>) {
        filterTemplates.forEach { template ->
            val filteredTemplates =
                annotations.filter { it.input.equals(template.templateName, ignoreCase = true) }
            template.annotationCount = filteredTemplates.count()
            template.isClicked = filteredTemplates.isNullOrEmpty().not()
        }

        notifyDataSetChanged()
    }

    private val onItemClickListener = View.OnClickListener {
        val template = it.getTag(R.id.template) as Template
        template.isSelected = true
        onLabelSelectionListener.onSelect(template)
    }

    inner class LabelsViewHolder(
        val binding: TemplateRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun loadIcon(imageUrl: String?) {
            Glide.with(binding.templateIv.context)
                .asBitmap()
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(imageTarget)
        }

        private val imageTarget = object : CustomTarget<Bitmap>() {
            override fun onLoadCleared(placeholder: Drawable?) {
                binding.loaderIv.visible()
                binding.reloadIV.gone()
                binding.templateIv.gone()
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                binding.loaderIv.gone()
                binding.reloadIV.gone()
                binding.templateIv.visible()
                binding.templateIv.setImageBitmap(resource)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                super.onLoadFailed(errorDrawable)
                binding.loaderIv.gone()
                binding.reloadIV.visible()
                binding.templateIv.gone()
            }
        }
    }
}

interface LabelSelectionListener {
    fun onSelect(template: Template)
}