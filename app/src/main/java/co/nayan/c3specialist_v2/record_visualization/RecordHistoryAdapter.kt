package co.nayan.c3specialist_v2.record_visualization

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.RecordHistoryItemBinding
import co.nayan.c3v2.core.models.RecordAnnotationHistory
import timber.log.Timber

class RecordHistoryAdapter(
    private val recordClickListener: RecordHistoryClickListener,
) : RecyclerView.Adapter<RecordHistoryViewHolder>() {

    private val recordAnnotations = mutableListOf<RecordAnnotationHistory>()
    var displayUrl: String? = null
    var applicationMode: String? = null
    var mediaType: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordHistoryViewHolder {
        return RecordHistoryViewHolder(
            RecordHistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            clickListener
        )
    }

    override fun getItemCount(): Int {
        return recordAnnotations.size
    }

    override fun onBindViewHolder(holder: RecordHistoryViewHolder, position: Int) {
        holder.onBind(displayUrl, recordAnnotations[position], applicationMode, mediaType)
    }

    fun addAll(toAdd: List<RecordAnnotationHistory>) {
        recordAnnotations.clear()
        recordAnnotations.addAll(toAdd)
    }

    private val clickListener = View.OnClickListener {
        Timber.e("On Item Click")
        val record = it.getTag(R.id.record) as RecordAnnotationHistory
        recordClickListener.onItemClicked(record)
    }
}

interface RecordHistoryClickListener {
    fun onItemClicked(recordAnnotationHistory: RecordAnnotationHistory)
}