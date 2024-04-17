package co.nayan.appsession

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.appsession.databinding.LayoutSessionDialogBinding

class SessionDialogFragment : DialogFragment() {

    private lateinit var binding: LayoutSessionDialogBinding
    private var dialogInteractionListener: DialogInteractionListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.SessionDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutSessionDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        binding.resumeBtn.setOnClickListener {
            dialogInteractionListener?.onResume()
            dismiss()
        }
    }

    companion object {
        fun newInstance(callback: DialogInteractionListener): SessionDialogFragment {
            val fragment = SessionDialogFragment()
            fragment.dialogInteractionListener = callback
            return fragment
        }
    }
}

interface DialogInteractionListener {
    fun onResume()
}