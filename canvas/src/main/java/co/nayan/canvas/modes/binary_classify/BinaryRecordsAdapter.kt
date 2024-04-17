package co.nayan.canvas.modes.binary_classify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.models.Record
import co.nayan.canvas.R
import co.nayan.canvas.databinding.BncRecordLayoutBinding

class BinaryRecordsAdapter(
    private val recordClickListener: DragAndSelectItemClickListener
) : RecyclerView.Adapter<BinaryRecordViewHolder>() {

    private val records: MutableList<Record> = mutableListOf()
    val selectedPositions: HashSet<Int> = hashSetOf()
    var selectionMode: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinaryRecordViewHolder {
        return BinaryRecordViewHolder(
            BncRecordLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), clickListener, longClickListener
        )
    }

    override fun getItemCount(): Int {
        return records.size
    }

    override fun onBindViewHolder(holderBinary: BinaryRecordViewHolder, position: Int) {
        val isSelected = selectedPositions.contains(position)
        holderBinary.onBind(records[position], selectionMode, isSelected, position)
    }

    fun addAll(toAdd: List<Record>) {
        records.clear()
        selectedPositions.clear()
        records.addAll(toAdd)
    }

    fun getSelectedItems(): List<Record> {
        return records.filterIndexed { index, _ -> selectedPositions.contains(index) }.map { it }
    }

    private val clickListener = View.OnClickListener {
        val position = it.getTag(R.id.position) as Int
        if (selectionMode) {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
            } else {
                selectedPositions.add(position)
            }
            notifyItemChanged(position)
        } else {
            recordClickListener.onItemClick(records[position].displayImage)
        }
    }

    private val longClickListener = View.OnLongClickListener {
        selectionMode = true
        val position = it.getTag(R.id.position) as Int
        selectedPositions.add(position)
        recordClickListener.onItemLongClick(position)
        notifyDataSetChanged()
        return@OnLongClickListener false
    }

    fun onBackPressed(): Boolean {
        return if (selectionMode) {
            selectionMode = false
            selectedPositions.clear()
            notifyDataSetChanged()
            true
        } else {
            false
        }
    }

    /**
     * Selection
     */
    fun selectRange(start: Int, end: Int, selected: Boolean) {
        for (i in start..end) {
            if (selected) {
                selectedPositions.add(i)
            } else {
                selectedPositions.remove(i)
            }
        }
        notifyItemRangeChanged(start, end - start + 1)
    }
}

interface DragAndSelectItemClickListener {
    fun onItemLongClick(position: Int): Boolean
    fun onItemClick(recordUrl: String?)
}
