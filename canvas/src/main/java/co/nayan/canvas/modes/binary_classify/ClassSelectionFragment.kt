package co.nayan.canvas.modes.binary_classify

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.utils.parcelableArrayList
import co.nayan.canvas.R
import co.nayan.canvas.databinding.DialogClassSelectionBinding

class ClassSelectionFragment : DialogFragment() {

    private val templates = mutableListOf<Template>()
    private var currentClass: String = ""
    private lateinit var callback: TemplateConfirmationListener
    private var selectedTemplate: String? = null
    private lateinit var binding: DialogClassSelectionBinding

    private val onTemplateSelectListener = object : OnTemplateSelectListener {
        override fun onSelect(position: Int) {
            if (templates.size > position) {
                selectedTemplate = templates[position].templateName
            }
        }
    }
    private val templatesAdapter = TemplatesAdapter(onTemplateSelectListener)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return DialogClassSelectionBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ClassifyDialogTheme)

        arguments?.parcelableArrayList<Template>(TEMPLATES)?.let { toAdd ->
            templates.clear()
            toAdd.forEach { template ->
                if (template.templateName != currentClass) {
                    templates.add(template)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.templatesView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = templatesAdapter
        }

        templatesAdapter.addAll(templates)
        templatesAdapter.notifyDataSetChanged()

        binding.confirmBtn.setOnClickListener {
            selectedTemplate?.let { name ->
                callback.onConfirm(name)
            }
            dismiss()
        }
    }

    companion object {
        private const val TEMPLATES = "templates"

        fun newInstance(
            templates: List<Template>,
            templateConfirmationListener: TemplateConfirmationListener
        ): ClassSelectionFragment {
            val f = ClassSelectionFragment()
            val args = Bundle()
            args.putParcelableArrayList(TEMPLATES, templates as ArrayList<out Parcelable>)
            f.callback = templateConfirmationListener
            f.arguments = args
            return f
        }
    }
}

interface OnTemplateSelectListener {
    fun onSelect(position: Int)
}

interface TemplateConfirmationListener {
    fun onConfirm(templateName: String)
}