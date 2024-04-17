package co.nayan.review.recordsgallery

import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Record
import co.nayan.c3views.utils.answer
import co.nayan.review.R
import co.nayan.review.databinding.ListItemHeaderBinding
import co.nayan.review.databinding.ListItemRecordRowBinding
import co.nayan.review.recordsgallery.viewholders.BaseViewHolder
import co.nayan.review.recordsgallery.viewholders.HeaderViewHolder
import co.nayan.review.recordsgallery.viewholders.RecordAdapterItem
import co.nayan.review.recordsgallery.viewholders.RecordViewHolder
import timber.log.Timber

class ReviewRecordsAdapter(
    private val selectionEnabled: Boolean,
    private val applicationMode: String?,
    private val recordClickListener: RecordClickListener,
    private val question: String?,
    private val appFlavor: String?
) : RecyclerView.Adapter<BaseViewHolder>() {

    private val recordItems = mutableListOf<RecordAdapterItem>()
    private val selectedPositions = HashSet<Int>()
    var selectionMode: Boolean = false
    var contrast: ColorMatrixColorFilter? = null
    var isForIncorrectSniffingRecords: Boolean = false
    var showOverlay = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == ITEM_TYPE_HEADER) {
            HeaderViewHolder(
                ListItemHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        } else {
            RecordViewHolder(
                ListItemRecordRowBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                clickListener,
                longClickListener
            )
        }
    }

    override fun getItemCount(): Int {
        return recordItems.size
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val params = holder.itemView.layoutParams as RecyclerView.LayoutParams
        params.bottomMargin = if (position == recordItems.lastIndex) 160 else 0
        holder.itemView.layoutParams = params
        if (getItemViewType(position) == ITEM_TYPE_HEADER)
            (holder as HeaderViewHolder).onBind(recordItems[position].header)
        else {
            val isSelected = selectedPositions.contains(position)
            (holder as RecordViewHolder).onBind(
                record = recordItems[position].record,
                isInSelectionMode = selectionMode,
                isSelected = isSelected,
                applicationMode = applicationMode,
                position = position,
                appFlavor = appFlavor,
                contrast = contrast,
                showOverlay = showOverlay
            )
        }
    }

    fun addAll(toAdd: MutableList<Record>) {
        recordItems.clear()
        selectedPositions.clear()

        val sortedRecords = mutableListOf<Record>()
        if (isForIncorrectSniffingRecords) sortedRecords.addAll(toAdd.sortedBy { it.needsRejection })
        else sortedRecords.addAll(toAdd)
        var needsRejection = sortedRecords.firstOrNull()?.needsRejection?.not() ?: true

        if (!question.isNullOrEmpty()) {
            if (question.contains("%{answer}")) {
                for (record in sortedRecords) {
                    record.answer()?.let { answer ->
                        val currentQuestion = question.replace("%{answer}", answer)
                        recordItems.add(RecordAdapterItem(currentQuestion, null))
                    }
                    recordItems.add(RecordAdapterItem(null, record))
                }
            } else {
                recordItems.add(RecordAdapterItem(question, null))
                for (record in sortedRecords) {
                    if (isForIncorrectSniffingRecords) {
                        if (needsRejection != record.needsRejection) {
                            val header = if (needsRejection) "Incorrect Rejections"
                            else "Incorrect Approvals"
                            needsRejection = needsRejection.not()
                            recordItems.add(RecordAdapterItem(header, null))
                        }
                    }
                    recordItems.add(RecordAdapterItem(null, record))
                }
            }
        } else {
            for (record in sortedRecords)
                recordItems.add(RecordAdapterItem(null, record))
        }

        notifyDataSetChanged()
    }

    private val clickListener = View.OnClickListener {
        Timber.e("On Item Click")
        val position = it.getTag(R.id.position) as Int
        val record = it.getTag(R.id.record) as Record
        if (selectionMode) {
            toggleSelection(position)
            recordClickListener.updateRecordsCount(
                selectedPositions.count(),
                recordItems.count { recordItem -> recordItem.record != null })
            if (selectedPositions.isNullOrEmpty()) recordClickListener.resetRecords()
        } else recordClickListener.onItemClicked(record)
    }

    private val longClickListener = View.OnLongClickListener {
        Timber.e("On Item Long Click")
        val position = it.getTag(R.id.position) as Int
        val record = it.getTag(R.id.record) as Record
        if (selectionEnabled) {
            selectionMode = true
            toggleSelection(position)
            recordClickListener.onLongPressed(position, record)
            recordClickListener.updateRecordsCount(
                selectedPositions.count(),
                recordItems.count { recordItem -> recordItem.record != null })
            notifyDataSetChanged()
        }
        return@OnLongClickListener true
    }

    override fun getItemViewType(position: Int): Int {
        return if (recordItems[position].record != null) ITEM_TYPE_RECORD
        else ITEM_TYPE_HEADER
    }

    fun onBackPressed(): Boolean {
        return if (selectionMode) {
            selectionMode = false
            selectedPositions.clear()
            notifyDataSetChanged()
            true
        } else false
    }

    // ----------------------
    // Selection
    // ----------------------
    private fun toggleSelection(pos: Int) {
        if (selectedPositions.contains(pos)) selectedPositions.remove(pos)
        else selectedPositions.add(pos)
        notifyItemChanged(pos)
    }

    fun selectRange(start: Int, end: Int, selected: Boolean) {
        for (i in start..end) {
            if (selected) selectedPositions.add(i) else selectedPositions.remove(i)
        }
        recordClickListener.updateRecordsCount(
            selectedPositions.count(),
            recordItems.count { recordItem -> recordItem.record != null })
        notifyItemRangeChanged(start, end - start + 1)
    }

    fun getSelection(): HashSet<Int> {
        return selectedPositions
    }

    fun getSelectedItems(): List<Int> {
        return recordItems
            .filterIndexed { index, _ -> selectedPositions.contains(index) }
            .mapNotNull { it.record?.id }
    }

    companion object {
        const val ITEM_TYPE_HEADER = 1
        const val ITEM_TYPE_RECORD = 2
    }
}

interface RecordClickListener {
    fun onItemClicked(record: Record)
    fun onLongPressed(position: Int, record: Record)
    fun updateRecordsCount(selectedCount: Int, totalCount: Int)
    fun starRecord(record: Record, position: Int, status: Boolean)
    fun resetRecords()
}