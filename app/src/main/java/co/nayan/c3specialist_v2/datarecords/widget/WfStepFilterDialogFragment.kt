package co.nayan.c3specialist_v2.datarecords.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutWfstepSelectorBinding
import co.nayan.c3v2.core.models.WorkFlow
import co.nayan.c3v2.core.utils.parcelableArrayList
import java.util.Locale

class WfStepFilterDialogFragment : DialogFragment() {

    private val elements = mutableListOf<WorkFlow>()
    private var onItemSelectionListener: OnWfStepSelectionListener? = null
    private lateinit var binding: LayoutWfstepSelectorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.parcelableArrayList<WorkFlow>(ELEMENTS)?.let { toAdd ->
            elements.addAll(toAdd)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return LayoutWfstepSelectorBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStateView()
    }

    private fun setupStateView() {
        context?.let {
            val wfSteps = listOf(Pair(-1, "None")) + elements.getWfSteps()
                .sortedBy { step -> step.second.lowercase(Locale.getDefault()) }
            val elementAdapter =
                ArrayAdapter(
                    it,
                    R.layout.layout_wf_step_item,
                    R.id.wfStepName,
                    wfSteps.map { step -> step.second })
            binding.elementListView.adapter = elementAdapter
            binding.elementListView.setOnItemClickListener { _, _, position, _ ->
                onItemSelectionListener?.onSelect(wfSteps[position])
                dismiss()
            }
        }
    }

    private fun List<WorkFlow>.getWfSteps(): List<Pair<Int?, String>> {
        val wfSteps = mutableListOf<Pair<Int?, String>>()
        forEach {
            it.wfSteps?.forEach { step ->
                wfSteps.add(Pair(step.id, "${step.name}(${it.name})"))
            }
        }
        return wfSteps
    }

    companion object {
        private const val ELEMENTS = "elements"
        fun newInstance(
            callback: OnWfStepSelectionListener,
            elements: MutableList<WorkFlow>
        ): WfStepFilterDialogFragment {
            val wfStepFilterDialogFragment = WfStepFilterDialogFragment()
            wfStepFilterDialogFragment.onItemSelectionListener = callback
            val arg = Bundle()
            arg.putParcelableArrayList(ELEMENTS, elements as ArrayList)
            wfStepFilterDialogFragment.arguments = arg
            return wfStepFilterDialogFragment
        }
    }
}

interface OnWfStepSelectionListener {
    fun onSelect(wfStep: Pair<Int?, String>)
}
