package co.nayan.c3specialist_v2.profile.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.c3specialist_v2.databinding.LayoutUserAuthenticationBinding
import co.nayan.c3v2.core.utils.visible

class AuthenticateUserDialogFragment : DialogFragment() {

    lateinit var authenticateUserCallback: AuthenticateUserCallback
    private lateinit var binding: LayoutUserAuthenticationBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return LayoutUserAuthenticationBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cancelTxt.setOnClickListener { dismiss() }
        binding.okTxt.setOnClickListener {
            validatePassword(binding.passwordInput.editText?.text.toString())
        }
    }

    private fun validatePassword(password: String) {
        if (password.isEmpty()) {
            binding.emptyPasswordTxt.visible()
        } else {
            authenticateUserCallback.onSubmit(password)
            dismiss()
        }
    }

    companion object {
        fun newInstance(callback: AuthenticateUserCallback): AuthenticateUserDialogFragment {
            val authenticateUserDialogFragment = AuthenticateUserDialogFragment()
            authenticateUserDialogFragment.authenticateUserCallback = callback
            return authenticateUserDialogFragment
        }
    }
}

interface AuthenticateUserCallback {
    fun onSubmit(password: String)
}