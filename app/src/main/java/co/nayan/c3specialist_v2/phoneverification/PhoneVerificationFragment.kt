package co.nayan.c3specialist_v2.phoneverification

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.databinding.FragmentPhoneVerificationBinding
import co.nayan.c3specialist_v2.onTextChanged
import co.nayan.c3specialist_v2.phoneverification.utils.getCountryCode
import co.nayan.c3specialist_v2.phoneverification.utils.isValidPhoneNumber
import co.nayan.c3specialist_v2.referral.IReferralManager
import co.nayan.c3specialist_v2.referral.ReferralDialogFragment
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorMessageState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.GooglePlayClientConnectedState
import co.nayan.c3v2.core.models.GooglePlayClientDisconnectedState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.spannableString
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3v2.login.hideKeyBoard
import co.nayan.c3v2.login.textToString
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import dagger.hilt.android.AndroidEntryPoint
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PhoneVerificationFragment : BaseFragment(R.layout.fragment_phone_verification) {

    @Inject
    lateinit var errorUtils: ErrorUtils

    private val TAG = PhoneVerificationFragment::class.java.simpleName
    private lateinit var phoneNumberUtil: PhoneNumberUtil
    private val binding by viewBinding(FragmentPhoneVerificationBinding::bind)
    private val viewModel: PhoneVerificationViewModel by activityViewModels()
    private lateinit var signInClient: SignInClient

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = viewModel
        phoneNumberUtil = PhoneNumberUtil.createInstance(requireContext())
        signInClient = Identity.getSignInClient(requireActivity())
        viewModel.googlePlayInstallManager.initListener()
        viewModel.googlePlayInstallManager.subscribe()
            .observe(viewLifecycleOwner, referralStateObserver)

        viewModel.requestHint = {
            val request = GetPhoneNumberHintIntentRequest.builder().build()
            if (::signInClient.isInitialized) {
                signInClient.getPhoneNumberHintIntent(request)
                    .addOnSuccessListener { result: PendingIntent ->
                        try {
                            hintIntentSender.launch(IntentSenderRequest.Builder(result).build())
                        } catch (e: Exception) {
                            Timber.e(TAG, "Launching the PendingIntent failed")
                        }
                    }
                    .addOnFailureListener {
                        Timber.e(TAG, "Phone Number Hint failed")
                    }
            }
        }

        viewModel.state.observe(viewLifecycleOwner, otpObserver)

        binding.isdEt.onTextChanged {
            if (it.length >= 5) binding.phoneNumberEt.requestFocus()
        }

        binding.phoneNumberEt.onTextChanged {
            if (it.isValidPhoneNumber())
                binding.sendOTPButton.enabled()
            else binding.sendOTPButton.disabled()
        }

        binding.phoneNumberEt.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_SEND) {
                hideKeyBoard()
                viewModel.validateUserLogin(
                    binding.isdEt.textToString(),
                    binding.phoneNumberEt.textToString()
                )
                true
            } else false
        }


        val userDetails = viewModel.getUserInfo()
        if (userDetails?.referrer != null) {
            binding.haveAReferralText.gone()
            binding.referralVerifiedText.visible()
        } else {
            binding.haveAReferralText.visible()
            binding.referralVerifiedText.gone()
        }

        binding.haveAReferralText.spannableString(
            "",
            getString(R.string.i_have_referral_code),
            true,
            callback = {
                viewModel.setInitialState()
                childFragmentManager.showDialogFragment(
                    ReferralDialogFragment.newInstance(referralDialogListener, errorUtils),
                    "Referral Dialog"
                )
            })
        binding.haveAReferralText.invalidate()
    }

    @SuppressLint("SetTextI18n")
    private val hintIntentSender =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            it?.data?.let { data ->
                when (it.resultCode) {
                    Activity.RESULT_OK -> {
                        try {
                            if (::signInClient.isInitialized) {
                                val phoneNumber = signInClient.getPhoneNumberFromIntent(data)
                                val countryCode = getCountryCode(phoneNumberUtil, phoneNumber)
                                countryCode?.let { code ->
                                    binding.isdEt.setText(code)
                                    binding.phoneNumberEt.setText(phoneNumber.removePrefix(code))
                                } ?: run {
                                    binding.isdEt.setText("+91")
                                    binding.phoneNumberEt.setText(phoneNumber)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(TAG, "Phone Number Hint failed")
                            binding.isdEt.setText("+91")
                        }
                    }

                    else -> binding.isdEt.setText("+91")
                }
            }
        }

    private val referralStateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> {

            }

            GooglePlayClientConnectedState -> {
                if (viewModel.getUserInfo()?.referrer == null)
                    viewModel.fetchReferralCode()
            }

            GooglePlayClientDisconnectedState -> {

            }
        }
    }

    private val otpObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> {
                binding.isdEt.enabled()
                binding.phoneNumberEt.enabled()
                binding.sendOTPButton.disabled()
                binding.errorMessageTxt.gone()
            }

            ProgressState -> {
                binding.isdEt.disabled()
                binding.phoneNumberEt.disabled()
                binding.sendOTPButton.disabled()
                binding.errorMessageTxt.gone()
            }

            is PhoneVerificationViewModel.PhoneVerificationSuccessState -> {
                replaceFragment(VerificationCodeFragment())
            }

            is PhoneVerificationViewModel.ReferralSuccessState -> {
                changeReferralAppliedChanges()
            }

            is ErrorMessageState -> {
                binding.isdEt.enabled()
                binding.phoneNumberEt.enabled()
                binding.sendOTPButton.disabled()
                binding.errorMessageTxt.text =
                    it.errorMessage ?: getString(co.nayan.c3v2.core.R.string.something_went_wrong)
                binding.errorMessageTxt.visible()
            }

            is ErrorState -> {
                binding.isdEt.enabled()
                binding.phoneNumberEt.enabled()
                binding.sendOTPButton.disabled()
                binding.errorMessageTxt.text = errorUtils.parseExceptionMessage(it.exception)
                binding.errorMessageTxt.visible()
            }
        }
    }

    private val referralDialogListener = object : IReferralManager {
        override fun onApplyReferral(referralCode: String) {
            viewModel.validateReferralCode(referralCode)
        }

        override fun onDialogDismiss() {
            binding.phoneNumberEt.textToString().apply {
                if (this.isValidPhoneNumber())
                    binding.sendOTPButton.enabled()
                else binding.sendOTPButton.disabled()
            }
        }
    }

    private fun changeReferralAppliedChanges() = lifecycleScope.launch {
        if (view != null && isVisible) {
            binding.haveAReferralText.gone()
            binding.referralVerifiedText.visible()
            binding.phoneNumberEt.enabled()
            binding.isdEt.enabled()
            binding.errorMessageTxt.gone()
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        requireActivity().supportFragmentManager.commit {
            replace(R.id.fragmentContainerVerification, fragment)
        }
    }
}