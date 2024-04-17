package co.nayan.c3specialist_v2.datarecords.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.databinding.ListItemProgressBinding
import co.nayan.c3specialist_v2.incorrectrecords.ProgressViewHolder
import co.nayan.c3v2.core.models.Record
import co.nayan.review.R
import co.nayan.review.databinding.ListItemRecordRowBinding
import co.nayan.review.recordsgallery.RecordClickListener
import co.nayan.review.recordsgallery.viewholders.BaseViewHolder
import co.nayan.review.recordsgallery.viewholders.RecordViewHolder

class DataRecordsAdapter(
    private val recordClickListener: RecordClickListener
) : RecyclerView.Adapter<BaseViewHolder>() {

    var applicationMode: String? = null
    var isPaginationEnabled: Boolean = true
    private val records = mutableListOf<Record>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == ITEM_TYPE_RECORD) {
            RecordViewHolder(
                ListItemRecordRowBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                clickListener,
                longClickListener
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
            (holder as RecordViewHolder).onBind(
                record = records[position],
                isInSelectionMode = false,
                isSelected = false,
                applicationMode = applicationMode,
                position = position
            )
        } else {
            if (isPaginationEnabled) (holder as ProgressViewHolder).showProgressBar()
            else (holder as ProgressViewHolder).hideProgressBar()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == (records.size)) ITEM_TYPE_LOADER
        else ITEM_TYPE_RECORD
    }

    fun addAll(toAdd: List<Record>) {
        records.clear()
        records.addAll(toAdd)
    }

    fun addNewRecords(toAdd: List<Record>) {
        val startIndex = records.size
        val endIndex = startIndex + toAdd.size

        records.addAll(toAdd)
        notifyItemRangeChanged(startIndex, endIndex)
    }

    private val clickListener = View.OnClickListener {
        val record = it.getTag(R.id.record) as Record
        recordClickListener.onItemClicked(record)
    }

    private val longClickListener = View.OnLongClickListener {
        return@OnLongClickListener false
    }

    companion object {
        const val ITEM_TYPE_RECORD = 1
        const val ITEM_TYPE_LOADER = 2
    }
}