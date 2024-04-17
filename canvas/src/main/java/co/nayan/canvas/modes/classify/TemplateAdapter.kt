package co.nayan.canvas.modes.classify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.canvas.R
import co.nayan.canvas.databinding.TemplateViewBinding

class TemplateAdapter(
    val itemClick: ((Template) -> Unit)? = null
) : RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder>() {

    private val templates = mutableListOf<Template>()
    private val filteredTemplates = mutableListOf<Template>()
    var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        return TemplateViewHolder(
            TemplateViewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return filteredTemplates.size
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        val isSelected = (selectedPosition == position)
        holder.binding.templateData = filteredTemplates[position]
        holder.bind(position, isSelected)
    }

    fun addAll(toAdd: List<Template>) {
        templates.clear()
        templates.addAll(toAdd)
        filteredTemplates.clear()
        filteredTemplates.addAll(toAdd)

        notifyDataSetChanged()
    }

    inner class TemplateViewHolder(
        val binding: TemplateViewBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(position: Int, isSelected: Boolean) {
            if (isSelected) {
                binding.templateView.scaleView(1.2f)
                binding.templateView.selected()
            } else {
                binding.templateView.scaleView(1f)
                binding.templateView.unSelected()
            }
            itemView.setTag(R.id.position, position)
        }

        private fun View.scaleView(scaleValue: Float) {
            scaleX = scaleValue
            scaleY = scaleValue
        }

        override fun onClick(view: View?) {
            selectedPosition = view?.getTag(R.id.position) as Int
            itemClick?.invoke(filteredTemplates[selectedPosition])
            notifyDataSetChanged()
        }
    }
}