package co.nayan.canvas.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.R
import co.nayan.canvas.databinding.LayoutCanvasStatusBinding

class CanvasStatusDialogFragment : DialogFragment() {

    private lateinit var binding: LayoutCanvasStatusBinding
    private var shouldShowNegativeBtn: Boolean = false
    private var shouldFinish: Boolean = false
    private var message: String? = null
    private var title: String? = null
    private var isAccountLocked = false
    private lateinit var canvasStatusDialogClickListener: CanvasStatusDialogClickListener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutCanvasStatusBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
        arguments?.let {
            shouldShowNegativeBtn = it.getBoolean(SHOULD_SHOW_NEGATIVE_BTN)
            message = it.getString(MESSAGE)
            title = it.getString(TITLE)
            shouldFinish = it.getBoolean(SHOULD_FINISH)
            isAccountLocked = it.getBoolean(IS_ACCOUNT_LOCKED)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        setupData()
        binding.positiveBtn.setOnClickListener {
            if (callBackInitialized())
                canvasStatusDialogClickListener.onPositiveBtnClick(shouldFinish)
            dismiss()
        }

        binding.negativeBtn.setOnClickListener {
            if (callBackInitialized())
                canvasStatusDialogClickListener.onNegativeBtnClick(shouldFinish)
            dismiss()
        }
    }

    private fun setupData() {
        message?.let { binding.messageTxt.text = it }
        val positiveBtnText = if (isAccountLocked) {
            binding.negativeBtn.visible()
            binding.negativeBtn.text = getString(R.string.incorrect_records)
            getString(R.string.ok)
        } else {
            if (shouldShowNegativeBtn) {
                binding.negativeBtn.visible()
                getString(R.string.try_again)
            } else {
                binding.negativeBtn.gone()
                getString(R.string.ok)
            }
        }

        positiveBtnText.let { binding.positiveBtn.text = it }
        title?.let { binding.titleTxt.text = it }
    }

    fun setDialogClickListener(callback: CanvasStatusDialogClickListener) {
        this.canvasStatusDialogClickListener = callback
    }

    private fun callBackInitialized() =
        this@CanvasStatusDialogFragment::canvasStatusDialogClickListener.isInitialized

    companion object {
        const val SHOULD_SHOW_NEGATIVE_BTN = "shouldShowNegativeBtn"
        const val MESSAGE = "message"
        const val TITLE = "title"
        const val SHOULD_FINISH = "shouldFinish"
        const val IS_ACCOUNT_LOCKED = "isAccountLocked"

        fun newInstance(
            shouldShowNegativeBtn: Boolean,
            message: String?,
            title: String?,
            shouldFinish: Boolean,
            isAccountLocked: Boolean
        ): CanvasStatusDialogFragment {
            return CanvasStatusDialogFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(SHOULD_SHOW_NEGATIVE_BTN, shouldShowNegativeBtn)
                    putString(MESSAGE, message)
                    putString(TITLE, title)
                    putBoolean(SHOULD_FINISH, shouldFinish)
                    putBoolean(IS_ACCOUNT_LOCKED, isAccountLocked)
                }
            }
        }
    }
}

interface CanvasStatusDialogClickListener {
    fun onPositiveBtnClick(shouldFinish: Boolean)
    fun onNegativeBtnClick(shouldFinish: Boolean)
}