package co.nayan.c3specialist_v2.faq

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.FaqDataCategories
import co.nayan.c3specialist_v2.databinding.FaqGalleryItemBinding
import co.nayan.c3v2.core.models.c3_module.DisplayDataItem
import co.nayan.c3v2.core.models.c3_module.FaqData
import com.bumptech.glide.Glide

class FaqGalleryAdapter(
    private val onFaqItemClickListener: OnFaqItemClickListener
) : RecyclerView.Adapter<TrainingGalleryViewHolder>() {

    private val displayDataItems = mutableListOf<DisplayDataItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrainingGalleryViewHolder {
        return TrainingGalleryViewHolder(
            FaqGalleryItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onItemClickListener
        )
    }

    override fun getItemCount() = displayDataItems.size

    override fun onBindViewHolder(holder: TrainingGalleryViewHolder, position: Int) {
        holder.bind(displayDataItems[position])
    }

    fun update(toAdd: List<DisplayDataItem>) {
        displayDataItems.clear()
        displayDataItems.addAll(toAdd)
    }

    private val onItemClickListener = View.OnClickListener {
        val data = it.getTag(R.id.display_data_item) as DisplayDataItem
        data.faqData?.let { trainingData ->
            onFaqItemClickListener.onClicked(trainingData)
        }
    }
}

class TrainingGalleryViewHolder(
    val binding: FaqGalleryItemBinding,
    private val onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(displayDataItem: DisplayDataItem) {
        if (displayDataItem.isPresent) {
            displayDataItem.faqData?.image?.let {
                binding.imageView.apply {
                    Glide.with(context).load(it).into(this)
                }
            }
            itemView.setOnClickListener(onClickListener)
            itemView.setTag(R.id.display_data_item, displayDataItem)
        }
        displayDataItem.category.let {
            val colorId = when (it) {
                FaqDataCategories.CORRECT -> {
                    R.color.does
                }
                FaqDataCategories.INCORRECT -> {
                    R.color.donts
                }
                else -> {
                    R.color.junk_light
                }
            }
            binding.imageContainer.setBackgroundColor(
                ContextCompat.getColor(
                    itemView.context,
                    colorId
                )
            )
            binding.imageView.setBackgroundColor(ContextCompat.getColor(itemView.context, colorId))
        }
    }
}

interface OnFaqItemClickListener {
    fun onClicked(data: FaqData)
}
