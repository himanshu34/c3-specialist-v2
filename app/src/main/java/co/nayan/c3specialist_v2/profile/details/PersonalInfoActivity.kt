package co.nayan.c3specialist_v2.profile.details

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.ProfileConstants
import co.nayan.c3specialist_v2.databinding.ActivityPersonalInfoBinding
import co.nayan.c3specialist_v2.profile.ProfileViewModel
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
class PersonalInfoActivity : BaseActivity() {

    private val profileViewModel: ProfileViewModel by viewModels()
    private val binding: ActivityPersonalInfoBinding by viewBinding(ActivityPersonalInfoBinding::inflate)

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupActionBar(binding.actionBar.appToolbar, true)
        title = getString(R.string.personal_info)
        profileViewModel.state.observe(this, stateObserver)
        setupDetails()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                binding.progressOverlay.visible()
            }

            FinishedState -> {
                binding.progressOverlay.gone()
            }

            is ProfileViewModel.FetchUserLocationSuccessState -> {
                binding.progressOverlay.gone()

                val userLocation = it.userLocation
                binding.cityInput.editText?.setText(userLocation.city)
                binding.stateInput.editText?.setText(userLocation.state)
                binding.countryInput.editText?.setText(userLocation.country)
                binding.addressInput.editText?.setText(userLocation.address)

                val userName = binding.nameInput.editText?.text.toString().trim()
                profileViewModel.updateBasePersonalInfo(
                    userName,
                    userLocation.address,
                    userLocation.state,
                    userLocation.city,
                    userLocation.country
                )
            }

            is ProfileViewModel.UpdateInfoSuccessState -> {
                binding.progressOverlay.gone()
                if (it.response.user != null) {
                    val intent = Intent().apply {
                        val message = String.format(
                            getString(R.string.update_message), ProfileConstants.PERSONAL_INFO
                        )
                        putExtra(Extras.UPDATED_MESSAGE, message)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                } else showMessage(it.response.message ?: getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }

            is ErrorState -> {
                binding.progressOverlay.gone()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
        invalidateOptionsMenu()
    }

    private fun setupDetails() {
        val userInfo = profileViewModel.getUserInfo()
        binding.nameInput.editText?.setText(userInfo?.name)

        binding.cityInput.editText?.setText(userInfo?.city ?: "")
        binding.stateInput.editText?.setText(userInfo?.state ?: "")
        binding.countryInput.editText?.setText(userInfo?.country ?: "")
        binding.addressInput.editText?.setText(userInfo?.address ?: "")

        if (userInfo?.address.isNullOrEmpty()
            && userInfo?.city.isNullOrEmpty()
            && userInfo?.state.isNullOrEmpty()
            && userInfo?.country.isNullOrEmpty()
        ) profileViewModel.getUserLocation(this)
    }

    private fun validateUserInputs(): Boolean {
        return when {
            binding.nameInput.editText?.text.isNullOrEmpty() -> {
                popupErrorMessage(binding.nameInput, getString(R.string.name_cant_be_blank))
                false
            }

            binding.addressInput.editText?.text.isNullOrEmpty() -> {
                popupErrorMessage(binding.addressInput, getString(R.string.address_cant_be_blank))
                false
            }

            binding.cityInput.editText?.text.isNullOrEmpty() -> {
                popupErrorMessage(binding.cityInput, getString(R.string.city_cant_be_blank))
                false
            }

            binding.stateInput.editText?.text.isNullOrEmpty() -> {
                popupErrorMessage(binding.stateInput, getString(R.string.state_cant_be_blank))
                false
            }

            binding.countryInput.editText?.text.isNullOrEmpty() -> {
                popupErrorMessage(binding.countryInput, getString(R.string.country_cant_be_blank))
                false
            }

            else -> {
                true
            }
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.let {
            it.findItem(R.id.save)?.isEnabled = !binding.progressOverlay.isVisible
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.save) {
            val userName = binding.nameInput.editText?.text.toString().trim()
            val address = binding.addressInput.editText?.text.toString().trim()
            val city = binding.cityInput.editText?.text.toString().trim()
            val state = binding.stateInput.editText?.text.toString().trim()
            val country = binding.countryInput.editText?.text.toString().trim()

            if (validateUserInputs())
                profileViewModel.updatePersonalInfo(
                    userName,
                    address,
                    state,
                    city,
                    country
                )
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.update_info_menu_item, menu)
        return super.onCreateOptionsMenu(menu)
    }
}