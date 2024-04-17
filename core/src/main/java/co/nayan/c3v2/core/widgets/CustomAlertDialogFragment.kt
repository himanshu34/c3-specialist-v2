package co.nayan.c3v2.core.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.databinding.LayoutCustomDialogBinding
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible

class CustomAlertDialogFragment : DialogFragment() {

    private var showNegativeBtn: Boolean = false
    private var showPositiveBtn: Boolean = false
    private var positiveBtnText: String? = null
    private var negativeBtnText: String? = null
    private var shouldFinish: Boolean = false
    private var message: String? = null
    private var titleText: String? = null
    lateinit var customAlertDialogListener: CustomAlertDialogListener
    private lateinit var binding: LayoutCustomDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutCustomDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupData()

        binding.positiveBtn.setOnClickListener {
            dismiss()
            customAlertDialogListener.onPositiveBtnClick(shouldFinish, tag)
        }

        binding.negativeBtn.setOnClickListener {
            dismiss()
            customAlertDialogListener.onNegativeBtnClick(shouldFinish, tag)
        }
    }

    private fun setupData() {
        binding.messageTxt.text = message

        if (showNegativeBtn) {
            binding.negativeBtn.visible()
            binding.negativeBtn.text = negativeBtnText ?: getString(R.string.cancel)
        } else binding.negativeBtn.gone()

        if (showPositiveBtn) {
            binding.positiveBtn.visible()
            binding.positiveBtn.text = positiveBtnText ?: getString(R.string.ok)
        } else binding.positiveBtn.gone()

        binding.titleTxt.text = titleText
    }

    fun setMessage(toSet: String) {
        message = toSet
    }

    fun shouldFinish(toSet: Boolean) {
        shouldFinish = toSet
    }

    fun showNegativeBtn(shouldShow: Boolean) {
        showNegativeBtn = shouldShow
    }

    fun showPositiveBtn(shouldShow: Boolean) {
        showPositiveBtn = shouldShow
    }

    fun setPositiveBtnText(toSet: String?) {
        positiveBtnText = toSet
    }

    fun setNegativeBtnText(toSet: String?) {
        negativeBtnText = toSet
    }

    fun setTitle(toSet: String?) {
        titleText = toSet
    }

    companion object {
        fun newInstance(callback: CustomAlertDialogListener): CustomAlertDialogFragment {
            val fragment = CustomAlertDialogFragment()
            fragment.customAlertDialogListener = callback
            return fragment
        }
    }
}

interface CustomAlertDialogListener {
    fun onPositiveBtnClick(shouldFinish: Boolean, tag: String?)
    fun onNegativeBtnClick(shouldFinish: Boolean, tag: String?)
}