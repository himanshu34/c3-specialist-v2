package co.nayan.c3specialist_v2.phoneverification

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.FragmentVerificationCodeBinding
import co.nayan.c3specialist_v2.introscreen.IntroScreenActivity
import co.nayan.c3specialist_v2.onTextChanged
import co.nayan.c3specialist_v2.phoneverification.otp.SmsBroadcastReceiver
import co.nayan.c3specialist_v2.phoneverification.otp.SmsBroadcastReceiverListener
import co.nayan.c3specialist_v2.phoneverification.utils.isValidOTP
import co.nayan.c3specialist_v2.phoneverification.utils.parseOTP
import co.nayan.c3specialist_v2.setExportedAttribute
import co.nayan.c3specialist_v2.viewBinding
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
import co.nayan.c3v2.login.hideKeyBoard
import co.nayan.c3v2.login.textToString
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class VerificationCodeFragment : BaseFragment(R.layout.fragment_verification_code) {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val binding by viewBinding(FragmentVerificationCodeBinding::bind)
    private val viewModel: PhoneVerificationViewModel by activityViewModels()
    private lateinit var smsBroadcastReceiver: SmsBroadcastReceiver

    override fun onStart() {
        super.onStart()
        smsBroadcastReceiver = SmsBroadcastReceiver().also {
            it.smsBroadcastReceiverListener = object : SmsBroadcastReceiverListener {
                override fun onSuccess(intent: Intent?) {
                    smsConsentRequestHandler.launch(intent)
                }

                override fun onFailure(errorMessage: String?) {
                    errorMessage?.let { e -> showMessage(e) }
                }
            }
        }

        try {
            requireContext().apply {
                val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    registerReceiver(smsBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                else registerReceiver(smsBroadcastReceiver, intentFilter)
                setExportedAttribute(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        requireActivity().unregisterReceiver(smsBroadcastReceiver)
    }

    private val smsConsentRequestHandler =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Obtain the phone number from the result
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                // Get SMS message content
                val message = it.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
                // Extract one-time code from the message and complete verification
                // `message` contains the entire text of the SMS message, so you will need
                // to parse the string.
                Timber.e(message)
                message.parseOTP()?.let { otp ->
                    Timber.e(otp)
                    binding.otpEt.setText(otp)
                    binding.verifyButton.enabled()
                    postDelayed(500) {
                        hideKeyBoard()
                        viewModel.validateOtp(otp)
                    }
                }
                // send one time code to the server
            } else {
                // Consent denied. User can type OTC manually.
                showMessage(getString(R.string.sms_retriever_timeout))
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = viewModel
        setupSmsRetrieverClient()

        binding.otpEt.onTextChanged {
            if (it.isValidOTP()) binding.verifyButton.enabled()
            else binding.verifyButton.disabled()
        }

        binding.otpEt.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_SEND) {
                hideKeyBoard()
                viewModel.validateOtp(binding.otpEt.textToString())
                true
            } else false
        }

        viewModel.state.observe(viewLifecycleOwner, stateObserver)
        viewModel.countDownTimer.observe(viewLifecycleOwner, countDownObserver)
    }

    private val countDownObserver: Observer<Pair<String, Boolean>> =
        Observer { pair: Pair<String, Boolean> ->
            initOTPLayout()
            binding.tvTimer.text = pair.first
            if (pair.second) {
                // count down is over
                resetOTPLayout()
            }
        }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> {
                binding.otpEt.enabled()
                binding.otpLayout.error = null
                binding.verifyButton.disabled()
            }

            ProgressState -> {
                binding.otpEt.disabled()
                binding.otpLayout.error = null
                binding.verifyButton.disabled()
            }

            is PhoneVerificationViewModel.OTPVerificationState -> {
                if (it.success) moveToNextScreen()
                else {
                    binding.otpEt.enabled()
                    binding.verifyButton.enabled()
                    showMessage("Invalid OTP, please try again!")
                }
            }

            is ErrorMessageState -> {
                binding.otpEt.enabled()
                binding.otpLayout.error =
                    it.errorMessage ?: getString(co.nayan.c3v2.core.R.string.something_went_wrong)
                binding.verifyButton.disabled()
            }

            is ErrorState -> {
                binding.otpEt.enabled()
                binding.otpLayout.error = errorUtils.parseExceptionMessage(it.exception)
                binding.verifyButton.disabled()
            }
        }
    }

    private fun moveToNextScreen() {
        when {
            viewModel.isOnBoardingDone().not() -> moveToIntroScreen()
            else -> moveToDashboard()
        }
    }

    private fun moveToIntroScreen() {
        requireActivity().apply {
            startActivity(Intent(this, IntroScreenActivity::class.java))
            finish()
        }
    }

    private fun moveToDashboard() {
        requireActivity().apply {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }

    private fun initOTPLayout() {
        binding.resendOTPButton.disabled()
        binding.tvTimer.visible()
    }

    private fun resetOTPLayout() {
        binding.resendOTPButton.enabled()
        binding.tvTimer.gone()
    }

    private fun setupSmsRetrieverClient() {
        // Start listening for SMS User Consent broadcasts from senderPhoneNumber
        // The Task<Void> will be successful if SmsRetriever was able to start
        // SMS User Consent, and will error if there was an error starting.
        val smsRetrieverClient = SmsRetriever.getClient(requireActivity())
        val task: Task<Void> = smsRetrieverClient.startSmsUserConsent(null)
        task.addOnSuccessListener {
            Timber.tag("SMS Retriever").d("SMS Retriever Success.")
        }
        task.addOnFailureListener { exception ->
            Timber.tag("SMS Retriever").d("SMS Retriever Failed.\n${exception.message}")
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}