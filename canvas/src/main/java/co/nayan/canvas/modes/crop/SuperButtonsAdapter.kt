package co.nayan.canvas.modes.crop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Template
import co.nayan.canvas.R
import co.nayan.canvas.databinding.SuperButtonItemBinding
import com.bumptech.glide.Glide

class SuperButtonsAdapter(onSuperButtonClickListener: OnSuperButtonClickListener) :
    RecyclerView.Adapter<SuperButtonsViewHolder>() {

    private val superButtons = mutableListOf<Template>()
    private val selectedPositions = mutableListOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuperButtonsViewHolder {
        return SuperButtonsViewHolder(
            SuperButtonItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onClickListener
        )
    }

    override fun getItemCount() = superButtons.size

    override fun onBindViewHolder(holder: SuperButtonsViewHolder, position: Int) {
        val isSelected = selectedPositions.contains(position)
        holder.onBind(superButtons[position], position, isSelected)
    }

    fun add(toAdd: List<Template>) {
        superButtons.clear()
        superButtons.addAll(toAdd)
    }

    fun update(toUpdate: List<String>) {
        selectedPositions.clear()
        toUpdate.forEach { tag ->
            val index = superButtons.indexOfFirst { it.templateName == tag }
            selectedPositions.add(index)
        }
    }

    private val onClickListener = View.OnClickListener {
        val position = it.getTag(R.id.position) as Int
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        onSuperButtonClickListener.onClicked(selectedTags(), superButtons)
    }

    private fun selectedTags(): List<Template> {
        return superButtons.filterIndexed { index, _ ->
            selectedPositions.contains(index)
        }
    }
}

class SuperButtonsViewHolder(
    val binding: SuperButtonItemBinding,
    private val onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun onBind(superButton: Template, position: Int, isSelected: Boolean) {
        binding.superButtonIv.apply {
            Glide.with(context).load(superButton.templateIcon)
                .into(this)
        }
        itemView.setOnClickListener(onClickListener)
        itemView.setTag(R.id.position, position)

        if (isSelected) binding.superButtonIv.alpha = 0.2f
        else binding.superButtonIv.alpha = 1f
    }
}

interface OnSuperButtonClickListener {
    fun onClicked(superButtons: List<Template>, tags: List<Template>)
}