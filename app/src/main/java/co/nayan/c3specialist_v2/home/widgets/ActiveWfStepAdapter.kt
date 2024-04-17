package co.nayan.c3specialist_v2.home.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.ActiveWfStepRowBinding
import co.nayan.c3v2.core.models.ActiveWfStep
import java.util.*

class ActiveWfStepAdapter(
    val wfSteps: List<ActiveWfStep>,
    val itemClick: ((ActiveWfStep) -> Unit)? = null
) : RecyclerView.Adapter<ActiveWfStepAdapter.MyViewHolder>() {

    private val filteredWfSteps = mutableListOf<ActiveWfStep>()

    init {
        filteredWfSteps.clear()
        filteredWfSteps.addAll(wfSteps)

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ActiveWfStepAdapter.MyViewHolder {
        return MyViewHolder(
            ActiveWfStepRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return filteredWfSteps.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val activeWfStep = filteredWfSteps[position]
        holder.binding.apply {
            workStepNameTxt.text = activeWfStep.name
            workFlowNameTxt.text = activeWfStep.workflowName
            activeCountTxt.apply {
                text = String.format(context.getString(R.string.active_records), activeWfStep.count)
            }
        }
    }

    fun filter(constraint: String) {
        val searchTerm = constraint.lowercase(Locale.getDefault()).trim()
        filteredWfSteps.clear()
        if (searchTerm.isNotEmpty()) {
            val filteredList = wfSteps.filter { wfStep ->
                val value = wfStep.name.lowercase(Locale.getDefault())
                value.contains(searchTerm)
            }
            filteredWfSteps.addAll(filteredList)
        } else filteredWfSteps.addAll(wfSteps)

        notifyDataSetChanged()
    }

    inner class MyViewHolder(
        val binding: ActiveWfStepRowBinding
    ) : RecyclerView.ViewHolder(binding.root),
        View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View?) {
            itemClick?.invoke(filteredWfSteps[layoutPosition])
        }
    }
}