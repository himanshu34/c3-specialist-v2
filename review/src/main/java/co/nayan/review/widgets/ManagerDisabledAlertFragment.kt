package co.nayan.review.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.review.R
import co.nayan.review.databinding.LayoutManagerDisabledBinding

class ManagerDisabledAlertFragment : DialogFragment() {

    private lateinit var binding: LayoutManagerDisabledBinding
    private var message: String? = null
    private var managerDisabledDialogListener: ManagerDisabledDialogListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutManagerDisabledBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        message?.let {
            binding.messageTxt.text = it
        }

        binding.negativeBtn.setOnClickListener {
            managerDisabledDialogListener?.onNegativeBtnClick()
        }

        binding.positiveBtn.setOnClickListener {
            dismiss()
            managerDisabledDialogListener?.onPositiveBtnClick()
        }
    }

    companion object {
        fun newInstance(
            callback: ManagerDisabledDialogListener?,
            message: String?
        ): ManagerDisabledAlertFragment {
            val fragment = ManagerDisabledAlertFragment()
            fragment.managerDisabledDialogListener = callback
            fragment.message = message
            return fragment
        }
    }
}

interface ManagerDisabledDialogListener {
    fun onPositiveBtnClick()
    fun onNegativeBtnClick()
}