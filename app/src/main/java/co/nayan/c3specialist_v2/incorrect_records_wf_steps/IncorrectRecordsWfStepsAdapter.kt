package co.nayan.c3specialist_v2.incorrect_records_wf_steps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.IncorrectRecordsWfStepItemBinding
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectWfStep

class IncorrectRecordsWfStepsAdapter(
    private val onWfStepClickListener: OnIncorrectRecordWfStepClickListener
) : RecyclerView.Adapter<IncorrectRecordWfStepViewHolder>() {

    private val wfSteps = mutableListOf<IncorrectWfStep>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): IncorrectRecordWfStepViewHolder {
        return IncorrectRecordWfStepViewHolder(
            IncorrectRecordsWfStepItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ), onClickListener
        )
    }

    override fun getItemCount() = wfSteps.size

    override fun onBindViewHolder(holder: IncorrectRecordWfStepViewHolder, position: Int) {
        holder.bind(wfSteps[position])
    }

    private val onClickListener = View.OnClickListener {
        val wfStep = it.getTag(R.id.wf_step) as IncorrectWfStep?
        onWfStepClickListener.onClicked(wfStep)
    }

    fun addAll(toAdd: List<IncorrectWfStep>) {
        wfSteps.clear()
        wfSteps.addAll(toAdd)
    }
}

class IncorrectRecordWfStepViewHolder(
    val binding: IncorrectRecordsWfStepItemBinding,
    private val onClickListener: View.OnClickListener
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(step: IncorrectWfStep) {
        binding.wfStepName.text = step.wfStep?.name ?: ""
        binding.applicationModeTxt.text = step.wfStep?.applicationModeName ?: ""
        binding.incorrectRecordsCountTxt.text = String.format(
            "Incorrect Count: %d", step.incorrectCount ?: 0
        )

        itemView.setTag(R.id.wf_step, step)
        itemView.setOnClickListener(onClickListener)
    }
}

interface OnIncorrectRecordWfStepClickListener {
    fun onClicked(wfStep: IncorrectWfStep?)
}