package co.nayan.c3specialist_v2.referral

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.databinding.LayoutReferralDialogBinding
import co.nayan.c3specialist_v2.onTextChanged
import co.nayan.c3specialist_v2.phoneverification.PhoneVerificationViewModel
import co.nayan.c3specialist_v2.phoneverification.utils.isValidReferralCode
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorMessageState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3v2.login.textToString

class ReferralDialogFragment : DialogFragment() {

    private lateinit var binding: LayoutReferralDialogBinding
    private lateinit var referralDialogListener: IReferralManager
    private val sharedViewModel: PhoneVerificationViewModel by activityViewModels()
    private lateinit var errorUtils: ErrorUtils

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutReferralDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedViewModel.state.observe(viewLifecycleOwner, stateObserver)
        setupData()
    }

    private fun setupData() {
        binding.referralLayout.visible()
        binding.referralVerifiedLayout.gone()

        binding.buttonSubmit.setOnClickListener {
            referralDialogListener.onApplyReferral(binding.refEdit.textToString())
        }

        binding.refEdit.onTextChanged {
            if (it.isValidReferralCode())
                binding.buttonSubmit.enabled()
            else binding.buttonSubmit.disabled()
        }

        binding.closeRef.setOnClickListener {
            referralDialogListener.onDialogDismiss()
            dialog?.dismiss()
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> {
                binding.refEdit.enabled()
                binding.buttonSubmit.disabled()
            }

            ProgressState -> {
                binding.refEdit.disabled()
                binding.buttonSubmit.disabled()
            }

            is PhoneVerificationViewModel.ReferralSuccessState -> {
                binding.referralLayout.gone()
                binding.referralVerifiedLayout.visible()
                postDelayed(1000) {
                    referralDialogListener.onDialogDismiss()
                    dialog?.dismiss()
                }
            }

            is ErrorMessageState -> {
                binding.refEdit.enabled()
                binding.buttonSubmit.enabled()
                binding.refEdit.error = it.errorMessage ?: getString(R.string.something_went_wrong)
                binding.refEdit.requestFocus()
            }

            is ErrorState -> {
                binding.refEdit.enabled()
                binding.buttonSubmit.enabled()
                binding.refEdit.error = getString(R.string.something_went_wrong)
                binding.refEdit.requestFocus()
            }
        }
    }

    companion object {
        fun newInstance(callback: IReferralManager, errorUtils: ErrorUtils): ReferralDialogFragment {
            val fragment = ReferralDialogFragment()
            fragment.referralDialogListener = callback
            fragment.errorUtils = errorUtils
            return fragment
        }
    }
}