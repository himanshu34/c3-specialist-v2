package co.nayan.canvas.sandbox.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import co.nayan.canvas.R
import co.nayan.canvas.config.TrainingStatus
import co.nayan.canvas.databinding.LayoutSandboxStatusBinding

class SandboxStatusDialogFragment : DialogFragment() {

    private lateinit var binding: LayoutSandboxStatusBinding
    private var sandboxDialogClickListener: SandboxStatusDialogClickListener? = null
    private var status: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutSandboxStatusBinding.inflate(inflater, container, false).apply {
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
        setupData()
        binding.confirmBtn.setOnClickListener {
            sandboxDialogClickListener?.onClick(binding.judgementMessageTxt.text.toString() != getString(R.string.correct_answer))
            dismiss()
        }
    }

    private fun setupData() {
        when (status) {
            TrainingStatus.IN_PROGRESS -> {
                binding.titleTxt.text = getString(R.string.congratulations)
                binding.judgementMessageTxt.text = getString(R.string.correct_answer)
                binding.judgementIconIv.setImageDrawable(
                    ContextCompat.getDrawable(binding.judgementIconIv.context, R.drawable.bg_congratulation)
                )
            }
            TrainingStatus.FAILED -> {
                binding.titleTxt.text = getString(R.string.alert)
                binding.judgementMessageTxt.text = getString(R.string.sandbox_failure)
                binding.judgementIconIv.setImageDrawable(
                    ContextCompat.getDrawable(binding.judgementIconIv.context, R.drawable.ic_wrong)
                )
            }
            TrainingStatus.SUCCESS -> {
                binding.titleTxt.text = getString(R.string.congratulations)
                binding.judgementMessageTxt.text = getString(R.string.sandbox_success)
                binding.judgementIconIv.setImageDrawable(
                    ContextCompat.getDrawable(binding.judgementIconIv.context, R.drawable.bg_congratulation)
                )
            }
        }
    }

    fun setStatus(toSet: String) {
        status = toSet
    }

    companion object {
        fun newInstance(callback: SandboxStatusDialogClickListener): SandboxStatusDialogFragment {
            val fragment = SandboxStatusDialogFragment()
            fragment.sandboxDialogClickListener = callback
            return fragment
        }
    }
}

interface SandboxStatusDialogClickListener {
    fun onClick(shouldFinish: Boolean = false)
}