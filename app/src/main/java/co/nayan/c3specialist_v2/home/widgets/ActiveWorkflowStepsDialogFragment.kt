package co.nayan.c3specialist_v2.home.widgets

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutActiveWfstepDialogBinding
import co.nayan.c3v2.core.models.ActiveWfStep
import co.nayan.c3v2.core.utils.gone

class ActiveWorkflowStepsDialogFragment(
    private val wfSteps: List<ActiveWfStep>,
    private val click: ((ActiveWfStep) -> Unit)? = null
) : DialogFragment() {

    private lateinit var activeWfStepAdapter: ActiveWfStepAdapter
    private lateinit var binding: LayoutActiveWfstepDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutActiveWfstepDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isCancelable = true

        binding.progress.isIndeterminate = true
        binding.progress.gone()
        activeWfStepAdapter = ActiveWfStepAdapter(wfSteps) {
            click?.invoke(it)
            dismiss()
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = activeWfStepAdapter
        }

        binding.txtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activeWfStepAdapter.filter(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {

            }
        })
    }
}