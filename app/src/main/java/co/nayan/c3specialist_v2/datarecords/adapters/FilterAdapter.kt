package co.nayan.c3specialist_v2.datarecords.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutAasmStateBinding

class FilterAdapter(
    aasmStateSelectionListener: FilterSelectionListener
) : RecyclerView.Adapter<FilterViewHolder>() {

    private val filters = mutableListOf<Pair<String, String>>()
    private val selectedPositions: MutableList<Int> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        return FilterViewHolder(
            LayoutAasmStateBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), clickListener
        )
    }

    override fun getItemCount(): Int {
        return filters.size
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val isSelected = selectedPositions.contains(position)
        holder.onBind(filters[position], position, isSelected)
    }

    fun addAll(toAdd: List<Pair<String, String>>, selectedValue: Pair<String, String>?) {
        filters.clear()
        filters.addAll(toAdd)

        val selectedPosition = filters.indexOf(selectedValue)
        if (selectedPosition != RecyclerView.NO_POSITION) {
            selectedPositions.add(selectedPosition)
        }
    }

    private val clickListener = View.OnClickListener {
        val position = it.getTag(R.id.position) as Int
        if (selectedPositions.contains(position).not()) {
            selectedPositions.clear()
            selectedPositions.add(position)
        } else {
            selectedPositions.clear()
        }
        aasmStateSelectionListener.onSelected(getSelectedItem())
        notifyDataSetChanged()
    }

    private fun getSelectedItem(): Pair<String, String>? {
        return if (selectedPositions.isEmpty()) {
            null
        } else {
            filters[selectedPositions.first()]
        }
    }
}

class FilterViewHolder(
    val binding: LayoutAasmStateBinding,
    private val onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun onBind(filter: Pair<String, String>, position: Int, isSelected: Boolean) {
        binding.filterName.text = filter.second
        binding.filterCb.isChecked = isSelected
        itemView.setOnClickListener(onClickListener)
        itemView.setTag(R.id.position, position)
    }
}

interface FilterSelectionListener {
    fun onSelected(filter: Pair<String, String>?)
}