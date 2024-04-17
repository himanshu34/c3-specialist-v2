package co.nayan.canvas.searchdialog.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Template
import co.nayan.canvas.databinding.ItemSearchResultBinding
import co.nayan.canvas.utils.editDistance
import java.util.*

class SearchDialogAdapter(
    val templates: List<Template>,
    val itemClick: ((Template) -> Unit)? = null
) : RecyclerView.Adapter<SearchDialogAdapter.MyViewHolder>() {

    private val filteredTemplates = mutableListOf<Template>()

    init {
        filteredTemplates.clear()
        filteredTemplates.addAll(templates)

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchDialogAdapter.MyViewHolder {
        return MyViewHolder(
            ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return filteredTemplates.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.binding.textView.text = filteredTemplates[position].templateName
    }

    fun filter(constraint: String) {
        val searchTerm = constraint.lowercase(Locale.getDefault()).trim()
        filteredTemplates.clear()
        if (searchTerm.isNotEmpty()) {
            val editDistanceList = templates.filter { template ->
                val value = template.templateName.lowercase(Locale.getDefault())
                editDistance(value, searchTerm) <= if (searchTerm.length <= 3)
                    searchTerm.length else 4
            }.sortedBy { template ->
                val value = template.templateName.lowercase(Locale.getDefault())
                editDistance(value, searchTerm)
            }

            val finalList = templates.filter { template ->
                val value = template.templateName.lowercase(Locale.getDefault())
                value.contains(searchTerm) && value.startsWith(searchTerm)
            }.union(editDistanceList)
            filteredTemplates.addAll(finalList)
        } else filteredTemplates.addAll(templates)

        notifyDataSetChanged()
    }

    inner class MyViewHolder(
        val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View?) {
            itemClick?.invoke(filteredTemplates[layoutPosition])
        }
    }
}