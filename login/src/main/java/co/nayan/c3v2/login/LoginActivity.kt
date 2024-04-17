package co.nayan.c3v2.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.getDeviceTotalRAM
import co.nayan.c3v2.login.databinding.ActivityLoginBinding
import co.nayan.c3v2.login.view.LoginFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val loginViewModel: LoginViewModel by viewModels()
    private val binding: ActivityLoginBinding by viewBinding(ActivityLoginBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupVersionCode()

        intent.getStringExtra(ERROR_MESSAGE)?.let { showMessage(it) }
        supportFragmentManager.commit { replace(R.id.fragmentContainer, LoginFragment()) }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun setupVersionCode() = lifecycleScope.launch {
        val buildVersion = loginViewModel.getBuildVersion() ?: run {
            val version = if (intent.hasExtra(VERSION_NAME) && intent.hasExtra(VERSION_CODE)) {
                String.format(
                    "v %s.%d",
                    intent.getStringExtra(VERSION_NAME),
                    intent.getIntExtra(VERSION_CODE, 0)
                )
            } else "0"
            loginViewModel.saveDeviceConfig(version, getDeviceTotalRAM())
            version
        }
        binding.tvBuildVersion.text = buildVersion
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        loginViewModel.callbackManager.onActivityResult(requestCode, resultCode, data)
        // onActivityResult() is deprecated, but Facebook hasn't added support
        // for the new Result Contracts API yet.
        // https://github.com/facebook/facebook-android-sdk/issues/875
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val ERROR_MESSAGE = "error_message"
        const val KEY_USER = "user"
        const val VERSION_NAME = "version_name"
        const val VERSION_CODE = "version_code"
    }
}