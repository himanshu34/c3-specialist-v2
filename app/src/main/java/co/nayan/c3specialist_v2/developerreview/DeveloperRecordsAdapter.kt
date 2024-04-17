package co.nayan.c3specialist_v2.developerreview

import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Record
import co.nayan.review.R
import co.nayan.review.databinding.ListItemRecordRowBinding
import co.nayan.review.recordsgallery.RecordClickListener
import co.nayan.review.recordsgallery.viewholders.BaseViewHolder
import co.nayan.review.recordsgallery.viewholders.RecordViewHolder

class DeveloperRecordsAdapter(
    private val recordClickListener: RecordClickListener
) : RecyclerView.Adapter<BaseViewHolder>() {

    var applicationMode: String? = null
    private val records = mutableListOf<Record>()
    var lastItemMargin = 0
    var selectionMode: Boolean = false
    private val selectedPositions = HashSet<Int>()
    var showOverlay = true
    var contrast: ColorMatrixColorFilter? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        return RecordViewHolder(
            ListItemRecordRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), clickListener, longClickListener
        )
    }

    override fun getItemCount(): Int {
        return records.size
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val params = holder.itemView.layoutParams as RecyclerView.LayoutParams
        params.bottomMargin = if (position == records.lastIndex) lastItemMargin
        else 0
        holder.itemView.layoutParams = params
        val isSelected = selectedPositions.contains(position)
        (holder as RecordViewHolder).onBind(
            record = records[position],
            isInSelectionMode = selectionMode,
            isSelected = isSelected,
            applicationMode = applicationMode,
            position = position,
            showStar = true,
            showOverlay = showOverlay,
            contrast = contrast
        )
    }

    fun addAll(toAdd: List<Record>) {
        selectedPositions.clear()
        records.clear()
        records.addAll(toAdd)

        notifyDataSetChanged()
    }

    private val clickListener = View.OnClickListener {
        val position = it.getTag(R.id.position) as Int
        val record = it.getTag(R.id.record) as Record
        when (it.id) {
            R.id.starredIv -> {
                recordClickListener.starRecord(record, position, false)
            }
            R.id.nonStarredIv -> {
                recordClickListener.starRecord(record, position, true)
            }
            else -> {
                if (selectionMode) {
                    toggleSelection(position)
                    recordClickListener.updateRecordsCount(
                        selectedPositions.count(),
                        records.count()
                    )
                } else recordClickListener.onItemClicked(record)
            }
        }
    }

    private val longClickListener = View.OnLongClickListener {
        val record = it.getTag(R.id.record) as Record
        selectionMode = true
        val position = it.getTag(R.id.position) as Int
        toggleSelection(position)
        recordClickListener.onLongPressed(position, record)
        recordClickListener.updateRecordsCount(selectedPositions.count(), records.count())
        notifyDataSetChanged()
        return@OnLongClickListener false
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
        recordClickListener.updateRecordsCount(selectedPositions.count(), records.count())
        notifyItemRangeChanged(start, end - start + 1)
    }

    fun getSelection(): HashSet<Int> {
        return selectedPositions.toHashSet()
    }

    fun getSelectedItems(): List<Int> {
        return records
            .filterIndexed { index, _ -> selectedPositions.contains(index) }
            .map { it.id }
    }

    fun updateRecordStatus(position: Int, status: Boolean) {
        records[position].starred = status
        notifyItemChanged(position)
    }
}