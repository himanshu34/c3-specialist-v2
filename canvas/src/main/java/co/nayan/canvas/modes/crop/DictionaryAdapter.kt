package co.nayan.canvas.modes.crop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import co.nayan.canvas.R
import co.nayan.canvas.config.Dictionary.DictionaryData
import co.nayan.canvas.databinding.DictionaryRowBinding

class DictionaryAdapter(
    private val dictionaryList: List<DictionaryData>,
    private val click: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<DictionaryAdapter.DictionaryViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DictionaryViewHolder {
        return DictionaryViewHolder(
            DictionaryRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = dictionaryList.size

    override fun onBindViewHolder(holder: DictionaryViewHolder, position: Int) {
        holder.binding.textDictionary.apply {
            val backgroundDrawable = if (dictionaryList[position].isSelected)
                ContextCompat.getDrawable(context, R.drawable.bg_dictionary_selected)
            else ContextCompat.getDrawable(context, R.drawable.bg_dictionary)
            background = backgroundDrawable
            text = dictionaryList[position].alphabet
        }

        holder.binding.textDictionary.setOnClickListener {
            dictionaryList.forEachIndexed { index, dictionaryData ->
                if (index == position)
                    dictionaryData.isSelected = !dictionaryData.isSelected
                else dictionaryData.isSelected = false
            }
            click?.invoke(position)
            notifyDataSetChanged()
        }
    }

    inner class DictionaryViewHolder(
        val binding: DictionaryRowBinding
    ) : RecyclerView.ViewHolder(binding.root)
}