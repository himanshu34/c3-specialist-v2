package co.nayan.canvas.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.LayoutJunkRecordDialogBinding

class JunkRecordDialogFragment : DialogFragment() {

    private var negativeBtnText: String? = null
    private var message: String? = null
    lateinit var junkDialogListener: JunkDialogListener
    private lateinit var binding: LayoutJunkRecordDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutJunkRecordDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        message?.let {
            binding.messageTxt.text = it
        }

        negativeBtnText?.let {
            binding.junkBtn.text = it
        }

        binding.junkBtn.setOnClickListener {
            dismiss()
            junkDialogListener.junkRecord()
        }

        binding.noBtn.setOnClickListener {
            dismiss()
        }
    }

    fun setMessage(toSet: String?) {
        message = toSet
    }

    fun setNegativeButtonText(toSet: String?) {
        toSet?.let {
            negativeBtnText = toSet
        }
    }

    companion object {
        fun newInstance(callback: JunkDialogListener): JunkRecordDialogFragment {
            val fragment = JunkRecordDialogFragment()
            fragment.junkDialogListener = callback
            return fragment
        }
    }
}

interface JunkDialogListener {
    fun junkRecord()
}
