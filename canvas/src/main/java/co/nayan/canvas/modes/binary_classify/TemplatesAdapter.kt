package co.nayan.canvas.modes.binary_classify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.canvas.R
import co.nayan.canvas.databinding.TemplateViewBinding

class TemplatesAdapter(private val onTemplateSelectListener: OnTemplateSelectListener) :
    RecyclerView.Adapter<TemplatesViewHolder>() {

    private val templates = mutableListOf<Template>()
    private var selectedPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplatesViewHolder {
        return TemplatesViewHolder(
            TemplateViewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onItemClickListener
        )
    }

    override fun getItemCount() = templates.size

    override fun onBindViewHolder(holder: TemplatesViewHolder, position: Int) {
        val isSelected = (selectedPosition == position)
        holder.binding.templateData = templates[position]
        holder.bind(position, isSelected)
    }

    fun addAll(toAdd: List<Template>) {
        templates.clear()
        templates.addAll(toAdd)
    }


    private val onItemClickListener = View.OnClickListener {
        selectedPosition = it.getTag(R.id.position) as Int
        onTemplateSelectListener.onSelect(selectedPosition)
        notifyDataSetChanged()
    }
}

class TemplatesViewHolder(
    val binding: TemplateViewBinding,
    private val onItemClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(position: Int, isSelected: Boolean) {
        if (isSelected) binding.templateView.selected()
        else binding.templateView.unSelected()

        itemView.setOnClickListener(onItemClickListener)
        itemView.setTag(R.id.position, position)
    }
}


