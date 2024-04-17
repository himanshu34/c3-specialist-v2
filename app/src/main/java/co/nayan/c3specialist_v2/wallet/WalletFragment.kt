package co.nayan.c3specialist_v2.wallet

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.WalletType
import co.nayan.c3specialist_v2.databinding.FragmentWalletBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.WalletDetails
import co.nayan.c3v2.core.models.c3_module.WalletErrorModel
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.HttpException
import javax.inject.Inject

@AndroidEntryPoint
class WalletFragment : BaseFragment(R.layout.fragment_wallet) {

    private val walletViewModel: WalletViewModel by activityViewModels()
    private val binding by viewBinding(FragmentWalletBinding::bind)

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        walletViewModel.walletDetails.observe(viewLifecycleOwner, walletDetailsObserver)
        walletViewModel.state.observe(viewLifecycleOwner, stateObserver)
        walletViewModel.isDriverWalletPresent.observe(viewLifecycleOwner, driverWalletObserver)
        walletViewModel.isSpecialistWalletPresent.observe(viewLifecycleOwner, specialistWalObserver)
        walletViewModel.isManagerWalletPresent.observe(viewLifecycleOwner, managerWalletObserver)
        walletViewModel.isBonusWalletPresent.observe(viewLifecycleOwner, bonusWalletObserver)
        walletViewModel.isReferralWalletPresent.observe(viewLifecycleOwner, referralWalletObserver)
        walletViewModel.isLeaderWalletPresent.observe(viewLifecycleOwner, leaderWalletObserver)
        walletViewModel.fetchWalletDetails()
        setupClicks()
    }

    private fun setupClicks() {
        binding.specialistWalletCheckoutBtn.setOnClickListener {
            walletViewModel.createPayout(WalletType.SPECIALIST)
        }
        binding.managerWalletCheckoutBtn.setOnClickListener {
            walletViewModel.createPayout(WalletType.MANAGER)
        }
        binding.bonusWalletCheckoutBtn.setOnClickListener {
            walletViewModel.createPayout(WalletType.BONUS)
        }
        binding.referralWalletCheckoutBtn.setOnClickListener {
            walletViewModel.createPayout(WalletType.REFERRAL)
        }
        binding.driverWalletCheckoutBtn.setOnClickListener {
            walletViewModel.createPayout(WalletType.DRIVER)
        }
        binding.leaderWalletCheckoutBtn.setOnClickListener {
            walletViewModel.createPayout(WalletType.LEADER)
        }
    }

    private val managerWalletObserver: Observer<Boolean> = Observer { isPresent ->
        if (isPresent) binding.managerWallet.visible()
        else binding.managerWallet.gone()
    }

    private val bonusWalletObserver: Observer<Boolean> = Observer { isPresent ->
        if (isPresent) binding.bonusWallet.visible()
        else binding.bonusWallet.gone()
    }

    private val referralWalletObserver: Observer<Boolean> = Observer { isPresent ->
        if (isPresent) binding.referralWallet.visible()
        else binding.referralWallet.gone()
    }

    private val driverWalletObserver: Observer<Boolean> = Observer { isPresent ->
        if (isPresent) binding.driverWallet.visible()
        else binding.driverWallet.gone()
    }

    private val specialistWalObserver: Observer<Boolean> = Observer { isPresent ->
        if (isPresent) binding.specialistWallet.visible()
        else binding.specialistWallet.gone()
    }

    private val leaderWalletObserver: Observer<Boolean> = Observer { isPresent ->
        if (isPresent) binding.leaderWallet.visible()
        else binding.leaderWallet.gone()
    }

    private val walletDetailsObserver: Observer<WalletDetails?> = Observer {
        binding.checkoutLimitTxt.text =
            String.format(getString(R.string.check_out_limit_text), it?.checkoutLimit ?: 0f)

        binding.specialistWalletAmountTxt.text =
            String.format(getString(R.string.amount_text), it?.specialistWalletAmount ?: 0f)
        binding.specialistWalletPointsTxt.text =
            String.format(getString(R.string.points_earned_text), it?.specialistWalletPoints ?: 0f)

        binding.bonusWalletAmountTxt.text =
            String.format(getString(R.string.amount_text), it?.bonusAmount ?: 0f)
        binding.bonusWalletPointsTxt.text =
            String.format(getString(R.string.points_earned_text), it?.bonusPoints ?: 0f)

        binding.referralWalletAmountTxt.text =
            String.format(getString(R.string.amount_text), it?.referralAmount ?: 0f)
        binding.referralWalletPointsTxt.text =
            String.format(getString(R.string.points_earned_text), it?.referralPoints ?: 0f)

        binding.managerWalletAmountTxt.text =
            String.format(getString(R.string.amount_text), it?.managerWalletAmount ?: 0f)
        binding.managerWalletPointsTxt.text =
            String.format(getString(R.string.points_earned_text), it?.managerWalletPoints ?: 0f)

        binding.driverWalletAmountTxt.text =
            String.format(getString(R.string.amount_text), it?.driverWalletAmount ?: 0f)
        binding.driverWalletPointsTxt.text =
            String.format(getString(R.string.points_earned_text), it?.driverWalletPoints ?: 0f)

        binding.leaderWalletAmountTxt.text =
            String.format(getString(R.string.amount_text), it?.leaderWalletAmount ?: 0f)
        binding.leaderWalletPointsTxt.text =
            String.format(getString(R.string.points_earned_text), it?.leaderWalletPoints ?: 0f)
    }

    private fun showCheckoutSuccessDialog(message: String) {
        context?.let {
            AlertDialog.Builder(it).apply {
                setTitle(getString(R.string.checkout_status))
                setMessage(message)
                setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    dialog.dismiss()
                }
                show()
            }
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                disableViews()
            }

            is WalletViewModel.WalletDetailsSuccessState -> {
                enableViews()
                it.message?.let { message ->
                    showMessage(message)
                }
            }

            is WalletViewModel.CreatePayoutSuccessState -> {
                enableViews()
                it.message?.let { message ->
                    showCheckoutSuccessDialog(message)
                }
            }

            is WalletViewModel.CheckOutNotPossibleState -> {
                enableViews()
                showMessage(getString(R.string.min_payout_message).format(WalletViewModel.MIN_PAYOUT_LIMIT))
            }

            is ErrorState -> {
                enableViews()
                var errorMessage: String? = null
                if (it.exception is HttpException) {
                    val walletError = errorUtils.getWalletHttpError(it.exception as HttpException)
                    if (walletError == null)
                        errorMessage = getString(co.nayan.c3v2.core.R.string.something_went_wrong)
                    else setupWalletError(walletError)
                } else errorMessage = errorUtils.parseExceptionMessage(it.exception)

                errorMessage?.let { message -> showMessage(message) }
            }
        }
    }

    private fun enableViews() {
        binding.specialistWalletCheckoutBtn.enabled()
        binding.bonusWalletCheckoutBtn.enabled()
        binding.referralWalletCheckoutBtn.enabled()
        binding.managerWalletCheckoutBtn.enabled()
        binding.driverWalletCheckoutBtn.enabled()
        binding.leaderWalletCheckoutBtn.enabled()
        binding.progressBar.invisible()
    }

    private fun disableViews() {
        binding.specialistWalletCheckoutBtn.disabled()
        binding.bonusWalletCheckoutBtn.disabled()
        binding.referralWalletCheckoutBtn.disabled()
        binding.managerWalletCheckoutBtn.disabled()
        binding.driverWalletCheckoutBtn.disabled()
        binding.leaderWalletCheckoutBtn.disabled()
        binding.progressBar.visible()
    }

    private fun showWalletErrorDialog(message: String) {
        context?.let { dialogContext ->
            AlertDialog.Builder(dialogContext)
                .setTitle(getString(R.string.alert))
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }.setCancelable(false)
                .show()
        }
    }

    private fun setupWalletError(error: WalletErrorModel) {
        var errorMessage = error.message
        if (!errorMessage.isNullOrEmpty() &&
            (error.hasFailedForBankDetails == true || error.hasFailedForSubscription == true)
        ) showWalletErrorDialog(errorMessage)
        else {
            errorMessage = if (!error.errors.isNullOrEmpty())
                error.errors?.joinToString(",").toString()
            else if (!error.message.isNullOrEmpty()) error.message.toString()
            else getString(co.nayan.c3v2.core.R.string.something_went_wrong)
            showMessage(errorMessage)
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_LONG).show()
    }
}