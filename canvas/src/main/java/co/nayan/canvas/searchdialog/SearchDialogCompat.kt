package co.nayan.canvas.searchdialog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.R
import co.nayan.canvas.databinding.SearchDialogCompatBinding
import co.nayan.canvas.searchdialog.adapters.SearchDialogAdapter
import co.nayan.canvas.utils.searchTagsList
import co.nayan.canvas.viewmodels.BaseCanvasViewModel

class SearchDialogCompat(
    private val canvasViewModel: BaseCanvasViewModel,
    private val templates: List<Template>,
    private val callback: ((Template?) -> Unit)? = null
) : DialogFragment() {

    private lateinit var binding: SearchDialogCompatBinding
    private lateinit var suggestionsAdapter: SearchDialogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, co.nayan.appsession.R.style.SessionDialogTheme)
        dialog?.window?.let {
            it.callback = UserInteractionAwareCallback(it.callback, activity)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return SearchDialogCompatBinding.inflate(inflater, container, false).apply {
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
        suggestionsAdapter = SearchDialogAdapter(templates) {
            callback?.invoke(it)
            dismiss()
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = suggestionsAdapter
        }
        binding.txtSearch.requestFocus()
        binding.txtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                suggestionsAdapter.filter(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {

            }
        })

        if (canvasViewModel.isSandbox())
            binding.ivAddTemplate.gone()
        else binding.ivAddTemplate.visible()
        binding.ivAddTemplate.setOnClickListener(addTagClickListener)
    }

    private val addTagClickListener = View.OnClickListener {
        binding.txtSearch.text.toString().trim().let { value ->
            if (value.isEmpty()) {
                requireContext().showToast(getString(R.string.enter_label))
            } else if (value.length < 2) {
                requireContext().showToast(getString(R.string.enter_valid_label))
            } else {
                val isAlreadyPresent = value.searchTagsList(templates)
                if (isAlreadyPresent) {
                    val message = String.format(getString(R.string.label_already_exists), value)
                    requireContext().showToast(message)
                } else {
                    childFragmentManager.showDialogFragment(
                        ShowAddLabelDialog(value) {
                            canvasViewModel.addNewLabel(it.trim())
                            dismiss()
                        })
                }
            }
        }
    }
}