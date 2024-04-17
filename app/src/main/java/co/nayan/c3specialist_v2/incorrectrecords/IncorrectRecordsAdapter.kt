package co.nayan.c3specialist_v2.incorrectrecords

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.ListItemIncorrectRecordBinding
import co.nayan.c3specialist_v2.databinding.ListItemProgressBinding
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.review.recordsgallery.viewholders.BaseViewHolder
import co.nayan.review.utils.isVideo
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class IncorrectRecordsAdapter(
    private val onIncorrectRecordClickListener: OnIncorrectRecordClickListener
) : RecyclerView.Adapter<BaseViewHolder>() {

    var isPaginationEnabled: Boolean = true
    private val records = mutableListOf<Record>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == ITEM_TYPE_RECORD) {
            IncorrectRecordViewHolder(
                ListItemIncorrectRecordBinding.inflate(
                    LayoutInflater.from(
                        parent.context
                    ), parent, false
                ), clickListener
            )
        } else {
            ProgressViewHolder(
                ListItemProgressBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun getItemCount(): Int {
        return if (records.size > 0) records.size + 1
        else records.size
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (getItemViewType(position) == ITEM_TYPE_RECORD) {
            (holder as IncorrectRecordViewHolder).onBind(record = records[position])
        } else {
            if (isPaginationEnabled) {
                (holder as ProgressViewHolder).showProgressBar()
            } else {
                (holder as ProgressViewHolder).hideProgressBar()
            }
        }
    }

    fun addNewRecords(toAdd: List<Record>) {
        records.addAll(toAdd)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == records.size) ITEM_TYPE_LOADER
        else ITEM_TYPE_RECORD
    }

    private val clickListener = View.OnClickListener {
        val record = it.getTag(R.id.record) as Record?
        onIncorrectRecordClickListener.onClick(record)
    }

    companion object {
        const val ITEM_TYPE_RECORD = 1
        const val ITEM_TYPE_LOADER = 2
    }
}

class IncorrectRecordViewHolder(
    val binding: ListItemIncorrectRecordBinding,
    private val clickListener: View.OnClickListener
) : BaseViewHolder(binding.root) {

    @SuppressLint("ClickableViewAccessibility")
    fun onBind(record: Record?) {
        record?.let {
            val url = if (record.mediaType == MediaType.VIDEO)
                record.mediaUrl else record.displayImage
            url?.let { mediaUrl ->
                if (mediaUrl.isVideo()) {
                    binding.videoViewContainer.visible()
                    binding.photoView.gone()
                    binding.recordIdTxt.text = String.format(
                        itemView.context.getString(co.nayan.review.R.string.record_id_text),
                        it.id
                    )
                } else {
                    binding.videoViewContainer.gone()
                    binding.photoView.visible()
                    binding.photoView.setOnTouchListener { _, _ -> false }
                    val imageUrl = it.displayImage
                    if (imageUrl?.contains("gif") == true) {
                        Glide.with(itemView.context)
                            .asGif()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .load(imageUrl)
                            .into(binding.photoView)
                    } else {
                        Glide.with(itemView.context)
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .load(imageUrl)
                            .into(binding.photoView)
                    }
                }
            }

            itemView.setTag(R.id.record, it)
            itemView.setOnClickListener(clickListener)
        }
    }
}

class ProgressViewHolder(
    val binding: ListItemProgressBinding
) : BaseViewHolder(binding.root) {

    fun showProgressBar() {
        binding.progressBar.visible()
    }

    fun hideProgressBar() {
        binding.progressBar.gone()
    }
}

interface OnIncorrectRecordClickListener {
    fun onClick(record: Record?)
}