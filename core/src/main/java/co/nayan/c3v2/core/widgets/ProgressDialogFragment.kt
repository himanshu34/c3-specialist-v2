package co.nayan.c3v2.core.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.databinding.LayoutProgressBinding

class ProgressDialogFragment : DialogFragment() {

    private var message: String? = null
    private lateinit var binding: LayoutProgressBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutProgressBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ProgressDialogTheme)
    }

    override fun onResume() {
        super.onResume()
        message?.let {
            binding.messageTxt.text = it
        }
    }

    fun setMessage(toSet: String) {
        message = toSet
    }
}