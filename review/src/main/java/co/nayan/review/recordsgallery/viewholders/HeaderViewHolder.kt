package co.nayan.review.recordsgallery.viewholders

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Record
import co.nayan.review.databinding.ListItemHeaderBinding

abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

class HeaderViewHolder(val binding: ListItemHeaderBinding) : BaseViewHolder(binding.root) {
    fun onBind(header: String?) {
        binding.headerLabel.text = header
    }
}

data class RecordAdapterItem(
    val header: String?,
    val record: Record?
)
