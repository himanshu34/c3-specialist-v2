package co.nayan.c3v2.login.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorMessageState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3v2.login.LoginViewModel
import co.nayan.c3v2.login.R
import co.nayan.c3v2.login.databinding.ForgotPasswordFragmentBinding
import co.nayan.c3v2.login.hideKeyBoard
import co.nayan.c3v2.login.textToString
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ForgotPasswordDialogFragment : DialogFragment() {

    private lateinit var binding: ForgotPasswordFragmentBinding
    private val loginViewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var errorUtils: ErrorUtils

    companion object {
        fun newInstance(): ForgotPasswordDialogFragment {
            return ForgotPasswordDialogFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ForgotPasswordFragmentBinding.inflate(inflater, container, false).apply {
            binding = this
            binding.viewModel = loginViewModel
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.forgotPasswordLayout.visible()
        binding.passwordSentLayout.gone()
        loginViewModel.state.observe(viewLifecycleOwner, stateObserver)

        binding.emailInput.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_SEND) {
                hideKeyBoard()
                loginViewModel.forgotPassword(binding.emailInput.textToString())
                true
            } else false
        }

        binding.closeBtn.setOnClickListener {
            dialog?.dismiss()
        }

        binding.buttonDone.setOnClickListener {
            dialog?.dismiss()
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                binding.progressBar.visible()
                binding.buttonSendPassword.gone()
            }

            LoginViewModel.ForgotPasswordSuccessState -> {
                binding.buttonSendPassword.visible()
                binding.progressBar.gone()
                binding.forgotPasswordLayout.gone()
                binding.passwordSentLayout.visible()
            }

            is ErrorMessageState -> {
                binding.buttonSendPassword.visible()
                if (binding.progressBar.isVisible) binding.progressBar.gone()
                binding.emailLayout.error = it.errorMessage
            }

            is ErrorState -> {
                binding.buttonSendPassword.visible()
                if (binding.progressBar.isVisible) binding.progressBar.gone()
                binding.emailLayout.error = errorUtils.parseExceptionMessage(it.exception)
            }
        }
    }
}