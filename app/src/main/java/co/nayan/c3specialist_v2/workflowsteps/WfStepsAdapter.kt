package co.nayan.c3specialist_v2.workflowsteps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.WfStepItemBinding
import co.nayan.c3v2.core.models.WfStep
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible

class WfStepsAdapter(private val onWfStepClickListener: OnWfStepClickListener) :
    RecyclerView.Adapter<WfStepViewHolder>() {

    private val wfSteps = mutableListOf<WfStep>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WfStepViewHolder {
        return WfStepViewHolder(
            WfStepItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onClickListener
        )
    }

    override fun getItemCount() = wfSteps.size

    override fun onBindViewHolder(holder: WfStepViewHolder, position: Int) {
        holder.bind(wfSteps[position])
    }

    private val onClickListener = View.OnClickListener {
        val wfStep = it.getTag(R.id.wf_step) as WfStep?
        when (it.id) {
            R.id.sandboxChip -> onWfStepClickListener.openSandbox(wfStep)
            R.id.reviewRecordsChip -> onWfStepClickListener.openReviewRecords(wfStep)
        }
    }

    fun addAll(toAdd: List<WfStep>) {
        wfSteps.clear()
        wfSteps.addAll(toAdd)
    }
}

class WfStepViewHolder(
    val binding: WfStepItemBinding,
    private val onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(wfStep: WfStep) {
        binding.wfStepName.text = wfStep.name
        binding.applicationModeTxt.text = wfStep.applicationModeName
        binding.thresholdVoteTxt.text = String.format(
            itemView.context.getString(R.string.threshold_vote), wfStep.thresholdVotes ?: 0
        )
        binding.requiredVoteTxt.text = String.format(
            itemView.context.getString(R.string.required_vote), wfStep.requiredVotes ?: 0
        )

        if (wfStep.sandboxId != null) {
            binding.sandboxChip.visible()
            binding.sandboxChip.setTag(R.id.wf_step, wfStep)
            binding.sandboxChip.setOnClickListener(onClickListener)
        } else binding.sandboxChip.gone()

        binding.reviewRecordsChip.setTag(R.id.wf_step, wfStep)
        binding.reviewRecordsChip.setOnClickListener(onClickListener)
    }
}

interface OnWfStepClickListener {
    fun openSandbox(wfStep: WfStep?)
    fun openReviewRecords(wfStep: WfStep?)
}