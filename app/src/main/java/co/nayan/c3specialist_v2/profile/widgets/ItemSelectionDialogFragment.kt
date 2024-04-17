package co.nayan.c3specialist_v2.profile.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutStateSelectorBinding

class ItemSelectionDialogFragment : DialogFragment() {

    private val elements = mutableListOf<String>()
    private var title: String? = null
    lateinit var onItemSelectionListener: OnItemSelectionListener
    private lateinit var binding: LayoutStateSelectorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            elements.clear()
            it.getStringArray(ELEMENTS)?.forEach { element ->
                elements.add(element)
            }
            title = it.getString(TITLE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return LayoutStateSelectorBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStateView()
    }

    private fun setupStateView() {
        binding.titleTxt.text = String.format("Select %s", title ?: "")
        context?.let {
            val elementAdapter =
                ArrayAdapter(it, R.layout.layout_state_item, R.id.stateName, elements)
            binding.elementListView.adapter = elementAdapter
            binding.elementListView.setOnItemClickListener { _, _, position, _ ->
                onItemSelectionListener.onSelect(elements[position])
                dismiss()
            }
        }
    }

    companion object {
        private const val TITLE = "title"
        private const val ELEMENTS = "elements"
        fun newInstance(
            title: String,
            callback: OnItemSelectionListener,
            elements: Array<String>
        ): ItemSelectionDialogFragment {
            val stateSelectionDialogFragment = ItemSelectionDialogFragment()
            stateSelectionDialogFragment.onItemSelectionListener = callback
            val arg = Bundle()
            arg.putStringArray(ELEMENTS, elements)
            arg.putString(TITLE, title)
            stateSelectionDialogFragment.arguments = arg
            return stateSelectionDialogFragment
        }
    }
}

interface OnItemSelectionListener {
    fun onSelect(element: String)
}
