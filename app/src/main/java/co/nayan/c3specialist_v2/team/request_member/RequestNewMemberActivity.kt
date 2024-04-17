package co.nayan.c3specialist_v2.team.request_member

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
import co.nayan.c3specialist_v2.databinding.ActivityRequestNewMemberBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RequestNewMemberActivity : BaseActivity() {

    private val requestMemberViewModels: RequestMemberViewModel by viewModels()
    private val binding: ActivityRequestNewMemberBinding by viewBinding(ActivityRequestNewMemberBinding::inflate)

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar, true)
        title = getString(R.string.request_member)

        requestMemberViewModels.state.observe(this, stateObserver)
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                binding.progressOverlay.visible()
            }
            FinishedState -> {
                binding.progressOverlay.gone()
                setResult(Activity.RESULT_OK, Intent())
                finish()
            }
            is ErrorState -> {
                binding.progressOverlay.gone()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
        invalidateOptionsMenu()
    }

    private fun validateInputs(name: String, email: String, phoneNumber: String): Boolean {
        return when {
            name.isEmpty() -> {
                popupErrorMessage(
                    binding.nameInput, getString(R.string.name_cant_be_blank)
                )
                false
            }
            email.isEmpty() -> {
                popupErrorMessage(
                    binding.emailInput, getString(co.nayan.c3v2.login.R.string.invalid_email)
                )
                false
            }
            phoneNumber.isEmpty() || phoneNumber.length != 10 -> {
                popupErrorMessage(
                    binding.phoneNumberInput, getString(R.string.invalid_phone_number)
                )
                false
            }
            else -> true
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.let {
            it.findItem(R.id.requestMember)?.isEnabled = binding.progressOverlay.isVisible.not()
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.request_member_item, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.requestMember) {
            val name = binding.nameInput.editText?.text.toString().trim()
            val email = binding.emailInput.editText?.text.toString().trim()
            val phoneNumber = binding.phoneNumberInput.editText?.text.toString().trim()

            if (validateInputs(name, email, phoneNumber)) {
                requestMemberViewModels.requestTeamMember(
                    name = name, email = email, phone = phoneNumber
                )
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }
}