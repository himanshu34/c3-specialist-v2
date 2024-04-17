package co.nayan.canvas.widgets

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

class ConfirmAnnotationsDialogFragment(
    private val selectedLabel: String?,
    private val callback: (() -> Unit)? = null
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
        val text = String.format(getString(R.string.confirm_annotations), selectedLabel)
        binding.messageTxt.text =
            setTextWithSpan(text, selectedLabel ?: "", StyleSpan(Typeface.BOLD))
        binding.positiveBtn.text = getString(R.string.yes)
        binding.positiveBtn.setOnClickListener {
            callback?.invoke()
            dismiss()
        }
        binding.negativeBtn.setOnClickListener {
            dismiss()
        }
    }
}