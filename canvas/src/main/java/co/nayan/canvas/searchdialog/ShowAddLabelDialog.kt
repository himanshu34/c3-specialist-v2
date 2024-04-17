package co.nayan.canvas.searchdialog

import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.AddLabelDialogLayoutBinding
import co.nayan.canvas.views.setTextWithSpan

class ShowAddLabelDialog(
    private val addLabelText: String,
    private val callback: ((String) -> Unit)? = null
) : DialogFragment() {

    private lateinit var binding: AddLabelDialogLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, co.nayan.appsession.R.style.SessionDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return AddLabelDialogLayoutBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        val text = String.format(getString(R.string.add_new_label_text), addLabelText)
        binding.messageTxt.text = setTextWithSpan(text, addLabelText, StyleSpan(Typeface.BOLD))
        binding.positiveBtn.setOnClickListener {
            callback?.invoke(addLabelText)
            dismiss()
        }
        binding.negativeBtn.setOnClickListener {
            dismiss()
        }
    }
}