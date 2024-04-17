package co.nayan.c3specialist_v2.profile.details

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputFilter.AllCaps
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityBankDetailsBinding
import co.nayan.c3specialist_v2.profile.ProfileViewModel
import co.nayan.c3specialist_v2.profile.utils.BankInfoManager
import co.nayan.c3specialist_v2.profile.utils.IfscCodeVerificationListener
import co.nayan.c3specialist_v2.utils.TextChangedListener
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.BankDetails
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.IfscVerificationResponse
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import com.skydoves.balloon.showAlignBottom
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class BankDetailsActivity : BaseActivity() {

    private val profileViewModel: ProfileViewModel by viewModels()
    private val binding: ActivityBankDetailsBinding by viewBinding(ActivityBankDetailsBinding::inflate)

    @Inject
    lateinit var errorUtils: ErrorUtils

    @Inject
    lateinit var bankInfoManager: BankInfoManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar, true)
        title = getString(R.string.bank_account_info)

        profileViewModel.state.observe(this, stateObserver)
        setupViews()

        val token = intent.getStringExtra(Extras.TOKEN)
        profileViewModel.fetchBankDetails(token)
    }

    private fun setupDetails(bankDetails: BankDetails) {
        binding.beneficiaryNameInput.editText?.setText(bankDetails.beneficiaryName)
        binding.accountNumberInput.editText?.setText(bankDetails.accountNumber)
        binding.ifscInput.editText?.setText(bankDetails.bankIfsc)
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                binding.progressOverlay.visible()
            }
            is ProfileViewModel.BankDetailsSuccessState -> {
                binding.progressOverlay.gone()
                setupDetails(it.bankDetails)

                it.bankDetails.message?.let { message ->
                    val intent = Intent().apply {
//                        val message = String.format(
//                            getString(R.string.update_message), ProfileConstants.BANK_DETAILS
//                        )
                        putExtra(Extras.UPDATED_MESSAGE, message)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }
            is ErrorState -> {
                binding.progressOverlay.gone()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
        invalidateOptionsMenu()
    }

    private val ifscCodeVerificationListener = object : IfscCodeVerificationListener {
        override fun failure() {
            updateIfscView(true)
        }

        override fun success(response: IfscVerificationResponse) {
            updateIfscView(false, response)
        }
    }

    private fun setupViews() {
        binding.beneficiaryNameInput.error = getString(R.string.beneficiary_name_update_error)
        binding.ifscInput.editText?.apply {
            filters = arrayOf<InputFilter>(AllCaps())
            addTextChangedListener(onIfscTextChangeListener)
        }
        bankInfoManager.setIfscCodeVerificationListener(ifscCodeVerificationListener)
    }

    private val onIfscTextChangeListener = object : TextChangedListener() {
        override fun onTextChanged(char: CharSequence?, start: Int, before: Int, count: Int) {
            super.onTextChanged(char, start, before, count)
            if (char.toString().length == 11) {
                binding.ifscStateIv.gone()
                binding.ifscProgressBar.visible()
                verifyIfscCode(char.toString())
            } else {
                binding.ifscStateIv.gone()
                binding.ifscDetailsContainer.gone()
                invalidateOptionsMenu()
            }
        }
    }

    private fun updateIfscView(
        isFailed: Boolean, bankInformation: IfscVerificationResponse? = null
    ) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                if (isFailed) {
                    binding.ifscProgressBar.gone()
                    binding.ifscStateIv.visible()
                    binding.ifscStateIv.setImageDrawable(
                        ContextCompat.getDrawable(this@BankDetailsActivity, R.drawable.ic_rejected)
                    )
                    binding.ifscDetailsContainer.gone()
                    binding.ifscInput.showAlignBottom(
                        viewUtils.getErrorBalloon(getString(R.string.invalid_ifsc_code)).build()
                    )
                } else {
                    binding.ifscProgressBar.gone()
                    binding.ifscStateIv.visible()
                    binding.ifscStateIv.setImageDrawable(
                        ContextCompat.getDrawable(this@BankDetailsActivity, R.drawable.ic_verified)
                    )
                    binding.ifscDetailsContainer.visible()
                    setupBankInformation(bankInformation)
                    invalidateOptionsMenu()
                }
                invalidateOptionsMenu()
            }
        }
    }

    private fun verifyIfscCode(ifscCode: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                bankInfoManager.verifyIFSCCode(ifscCode)
            }
        }
    }

    private fun setupBankInformation(bankInformation: IfscVerificationResponse?) {
        val bankName = bankInformation?.BANK
        val branch = bankInformation?.BRANCH
        val city = bankInformation?.CITY
        val state = bankInformation?.STATE

        if (bankName.isNullOrEmpty()) {
            binding.bankNameContainer.gone()
        } else {
            binding.bankNameContainer.visible()
            binding.bankNameTxt.text = bankName
        }

        if (branch.isNullOrEmpty()) {
            binding.branchContainer.gone()
        } else {
            binding.branchContainer.visible()
            binding.branchNameTxt.text = branch
        }

        if (city.isNullOrEmpty()) {
            binding.cityContainer.gone()
        } else {
            binding.cityContainer.visible()
            binding.cityTxt.text = city
        }

        if (state.isNullOrEmpty()) {
            binding.stateContainer.gone()
        } else {
            binding.stateContainer.visible()
            binding.stateTxt.text = state
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressOverlay, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun validateInputs(beneficiaryName: String, accountNumber: String): Boolean {
        return when {
            beneficiaryName.isEmpty() -> {
                popupErrorMessage(
                    binding.beneficiaryNameInput, getString(R.string.beneficiary_name_cant_be_blank)
                )
                false
            }
            accountNumber.isEmpty() -> {
                popupErrorMessage(
                    binding.accountNumberInput,
                    getString(R.string.beneficiary_account_number_cant_be_blank)
                )
                false
            }
            else -> {
                true
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.let {
            it.findItem(R.id.save)?.isEnabled =
                !binding.progressOverlay.isVisible && binding.ifscDetailsContainer.isVisible
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.update_info_menu_item, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.save) {
            val beneficiaryName = binding.beneficiaryNameInput.editText?.text.toString().trim()
            val accountNumber = binding.accountNumberInput.editText?.text.toString().trim()
            val ifscCode = binding.ifscInput.editText?.text.toString().trim()
            val bankName = binding.bankNameTxt.text.toString()
            val token = intent.getStringExtra(Extras.TOKEN)

            if (validateInputs(beneficiaryName, accountNumber)) {
                profileViewModel.updateBankDetails(
                    beneficiaryName, accountNumber, ifscCode, bankName, token
                )
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}