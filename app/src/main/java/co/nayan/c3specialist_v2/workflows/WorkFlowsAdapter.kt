package co.nayan.c3specialist_v2.workflows

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.databinding.WorkFlowItemBinding
import co.nayan.c3v2.core.models.WorkFlow
import java.util.*

class WorkFlowsAdapter(
    private val onWorkFlowClickListener: OnWorkFlowClickListener
) : RecyclerView.Adapter<WorkFlowsAdapter.WorkFlowViewHolder>() {

    private val workFlows = mutableListOf<WorkFlow>()
    private val filteredItems = mutableListOf<WorkFlow>()
    private var searchQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkFlowViewHolder {
        return WorkFlowViewHolder(
            WorkFlowItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = filteredItems.size

    override fun onBindViewHolder(holder: WorkFlowViewHolder, position: Int) {
        holder.binding.workFlowData = filteredItems[position]
    }

    fun addAll(toAdd: List<WorkFlow>) {
        workFlows.clear()
        workFlows.addAll(toAdd)

        filterListItems(searchQuery)
    }

    fun filterListItems(query: String) {
        searchQuery = query
        filteredItems.clear()
        val items = if (query.isNotEmpty()) {
            workFlows.filter {
                (it.name ?: "").lowercase(Locale.getDefault())
                    .contains(query.lowercase(Locale.getDefault()))
            }
        } else workFlows
        filteredItems.addAll(items)

        notifyDataSetChanged()
    }

    inner class WorkFlowViewHolder(
        val binding: WorkFlowItemBinding
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            binding.root.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            onWorkFlowClickListener.onClick(filteredItems[adapterPosition])
        }
    }
}

interface OnWorkFlowClickListener {
    fun onClick(workFlow: WorkFlow)
}