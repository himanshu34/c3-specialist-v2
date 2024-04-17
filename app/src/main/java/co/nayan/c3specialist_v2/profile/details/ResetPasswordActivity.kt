package co.nayan.c3specialist_v2.profile.details

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityResetPasswordBinding
import co.nayan.c3specialist_v2.profile.ProfileViewModel
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ResetPasswordActivity : BaseActivity() {

    private val profileViewModel: ProfileViewModel by viewModels()
    private val binding: ActivityResetPasswordBinding by viewBinding(ActivityResetPasswordBinding::inflate)

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar, true)
        title = getString(R.string.reset_password)

        profileViewModel.state.observe(this, stateObserver)
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                binding.progressOverlay.visible()
            }
            is ProfileViewModel.UpdatePasswordState -> {
                binding.progressOverlay.gone()

                if (it.success) {
                    val intent = Intent().apply {
                        putExtra(Extras.UPDATED_MESSAGE, it.message)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                } else {
                    popupErrorMessage(
                        binding.currentPasswordInput,
                        it.message ?: getString(R.string.incorrect_currnet_password)
                    )
                }
            }
            is ErrorState -> {
                binding.progressOverlay.gone()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
        invalidateOptionsMenu()
    }

    private fun validateUserInputs(
        newPassword: String,
        confirmPassword: String,
        currentPassword: String
    ): Boolean {
        return when {
            currentPassword.isEmpty() -> {
                popupErrorMessage(
                    binding.currentPasswordInput,
                    getString(R.string.current_password_cant_be_blank)
                )
                false
            }
            newPassword.isEmpty() -> {
                popupErrorMessage(binding.newPasswordInput, getString(R.string.new_password_cant_be_blank))
                false
            }
            newPassword != confirmPassword -> {
                popupErrorMessage(binding.newPasswordInput, getString(R.string.password_not_same))
                false
            }
            else -> true
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.let {
            it.findItem(R.id.save)?.apply {
                title = getString(co.nayan.review.R.string.reset)
                isEnabled = !binding.progressOverlay.isVisible
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.save) {
            val currentPassword = binding.currentPasswordInput.editText?.text.toString().trim()
            val newPassword = binding.newPasswordInput.editText?.text.toString().trim()
            val confirmPassword = binding.confirmPasswordInput.editText?.text.toString().trim()

            if (validateUserInputs(newPassword, confirmPassword, currentPassword)) {
                profileViewModel.resetPassword(currentPassword, newPassword)
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.update_info_menu_item, menu)
        return super.onCreateOptionsMenu(menu)
    }
}