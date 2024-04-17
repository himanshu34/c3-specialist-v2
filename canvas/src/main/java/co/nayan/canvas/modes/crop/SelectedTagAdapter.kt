package co.nayan.canvas.modes.crop

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.canvas.databinding.LayoutTagSelectedBinding

class SelectedTagAdapter : RecyclerView.Adapter<SelectedTagViewHolder>() {

    private val tags = mutableListOf<String>()

    fun addTags(toAdd: List<String>) {
        tags.clear()
        tags.addAll(toAdd)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedTagViewHolder {
        return SelectedTagViewHolder(
            LayoutTagSelectedBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: SelectedTagViewHolder, position: Int) {
        holder.bind(tags[position])
    }

    override fun getItemCount(): Int {
        return tags.size
    }
}

class SelectedTagViewHolder(
    val binding: LayoutTagSelectedBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(tag: String) {
        binding.tvTitle.text = tag
    }
}