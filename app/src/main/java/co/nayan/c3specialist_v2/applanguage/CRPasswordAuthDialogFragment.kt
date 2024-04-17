package co.nayan.c3specialist_v2.applanguage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.LayoutCrPasswordAuthBinding
import co.nayan.c3v2.core.showToast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nayan.nayancamv2.util.toBase64
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class CRPasswordAuthDialogFragment(
    private val userEmail: String,
    private val currentDate: String
) : BottomSheetDialogFragment() {

    var onTokenUpdate: OnTokenUpdate? = null
    private lateinit var binding: LayoutCrPasswordAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return LayoutCrPasswordAuthBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.startCR.setOnClickListener {
            if (binding.crToken.text.isNullOrEmpty()) {
                requireContext().showToast(getString(R.string.enter_cr_token))
                onTokenUpdate?.onFail()
            } else {
                val tokenInput = binding.crToken.text.toString()
                val token = (userEmail + currentDate).toBase64()

                Timber.e("tokenInput: $tokenInput")
                Timber.e("token: $token")
                if (token.trim() == tokenInput.trim()) {
                    onTokenUpdate?.onSuccess()
                } else {
                    requireContext().showToast(getString(R.string.invalid_cr))
                    onTokenUpdate?.onFail()
                }
            }

            dismiss()
        }
    }

    interface OnTokenUpdate {
        fun onFail()
        fun onSuccess()
    }
}
