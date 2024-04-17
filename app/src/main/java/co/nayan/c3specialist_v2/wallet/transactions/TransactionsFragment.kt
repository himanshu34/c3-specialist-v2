package co.nayan.c3specialist_v2.wallet.transactions

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.TransactionStatus
import co.nayan.c3specialist_v2.config.WalletType
import co.nayan.c3specialist_v2.databinding.FragmentTransactionsBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3specialist_v2.wallet.WalletViewModel
import co.nayan.c3specialist_v2.wallet.invoice.InvoiceActivity
import co.nayan.c3specialist_v2.wallet.referral_transactions.ReferralNetworkAdapter
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.c3_module.Transaction
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.utils.SimpleDividerItemDecoration
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TransactionsFragment : BaseFragment(R.layout.fragment_transactions) {

    private val walletViewModel: WalletViewModel by activityViewModels()
    private val binding by viewBinding(FragmentTransactionsBinding::bind)

    @Inject
    lateinit var errorUtils: ErrorUtils

    private val onTransactionClickListener = object :
        OnTransactionClickListener {
        override fun onClick(transaction: Transaction) {
            if (transaction.aasmState == TransactionStatus.PROCESSED) {
                Intent(activity, InvoiceActivity::class.java).apply {
                    putExtra(Extras.TRANSACTION, transaction)
                    startActivity(this)
                }
            }
        }
    }

    private var transactionsAdapter: TransactionsAdapter? = null
    private var referralNetworkAdapter: ReferralNetworkAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        walletViewModel.state.observe(viewLifecycleOwner, stateObserver)
        walletViewModel.walletType.observe(viewLifecycleOwner, walletTypeObserver)
        walletViewModel.isDriverWalletPresent.observe(viewLifecycleOwner, driverWalletObserver)
        walletViewModel.isSpecialistWalletPresent.observe(viewLifecycleOwner, specialistWalObserver)
        walletViewModel.isManagerWalletPresent.observe(viewLifecycleOwner, managerWalletObserver)
        walletViewModel.isLeaderWalletPresent.observe(viewLifecycleOwner, leaderWalletObserver)
        walletViewModel.isBonusWalletPresent.observe(viewLifecycleOwner, bonusWalletObserver)
        walletViewModel.isReferralWalletPresent.observe(viewLifecycleOwner, referralWalletObserver)

        setupClicks()

        binding.walletSelector.setOnCheckedChangeListener(onWalletSelectionListener)
        binding.pullToRefresh.setOnRefreshListener {
            walletViewModel.fetchWalletDetails()
            val selectedChips =
                binding.walletSelector.children.toList().filter { (it as Chip).isChecked }
            val referralNetworkChip =
                binding.walletSelector.findViewById<Chip>(R.id.referral_network)
            if (selectedChips.contains(referralNetworkChip))
                walletViewModel.fetchReferralTransactions(WalletType.REFERRAL_NETWORK)
            else {
                val walletType = walletViewModel.walletType.value ?: WalletType.DRIVER
                walletViewModel.loadMoreTransactions(walletType)
            }
        }
    }

    private val onWalletSelectionListener = ChipGroup.OnCheckedChangeListener { _, checkedId ->
        when (checkedId) {
            R.id.specialistWallet -> {
                binding.filterChip.visible()
                walletViewModel.loadMoreTransactions(WalletType.SPECIALIST)
            }

            R.id.managerWallet -> {
                binding.filterChip.visible()
                walletViewModel.loadMoreTransactions(WalletType.MANAGER)
            }

            R.id.driverWallet -> {
                binding.filterChip.visible()
                walletViewModel.loadMoreTransactions(WalletType.DRIVER)
            }

            R.id.bonus -> {
                binding.filterChip.visible()
                walletViewModel.loadMoreTransactions(WalletType.BONUS)
            }

            R.id.leaderWallet -> {
                binding.filterChip.visible()
                walletViewModel.loadMoreTransactions(WalletType.LEADER)
            }

            R.id.referral -> {
                binding.filterChip.visible()
                walletViewModel.loadMoreTransactions(WalletType.REFERRAL)
            }

            R.id.referral_network -> {
                binding.filterChip.gone()
                walletViewModel.fetchReferralTransactions(WalletType.REFERRAL_NETWORK)
            }
        }
    }

    private val managerWalletObserver: Observer<Boolean> = Observer {
        if (it) binding.managerWallet.visible()
        else binding.managerWallet.gone()
    }

    private val specialistWalObserver: Observer<Boolean> = Observer {
        if (it) binding.specialistWallet.visible()
        else binding.specialistWallet.gone()
    }

    private val bonusWalletObserver: Observer<Boolean> = Observer {
        if (it) binding.bonus.visible()
        else binding.bonus.gone()
    }

    private val referralWalletObserver: Observer<Boolean> = Observer {
        if (it) binding.referral.visible()
        else binding.referral.gone()
        // since we don't want to hide referral network tab at all.
        binding.referralNetwork.visible()
    }

    private val driverWalletObserver: Observer<Boolean> = Observer {
        if (it) binding.driverWallet.visible()
        else binding.driverWallet.gone()
    }

    private val leaderWalletObserver: Observer<Boolean> = Observer {
        if (it) binding.leaderWallet.visible()
        else binding.leaderWallet.gone()
    }

    private val walletTypeObserver: Observer<String> = Observer {
        when (it) {
            WalletType.DRIVER -> binding.walletSelector.check(R.id.driverWallet)
            WalletType.SPECIALIST -> binding.walletSelector.check(R.id.specialistWallet)
            WalletType.MANAGER -> binding.walletSelector.check(R.id.managerWallet)
            WalletType.BONUS -> binding.walletSelector.check(R.id.bonus)
            WalletType.REFERRAL -> binding.walletSelector.check(R.id.referral)
            WalletType.REFERRAL_NETWORK -> binding.walletSelector.check(R.id.referral_network)
            WalletType.LEADER -> binding.walletSelector.check(R.id.leaderWallet)
        }
    }

    private fun setupClicks() {
        binding.filterChip.setOnClickListener {
            if (transactionsAdapter?.isTransactionsPresent() == true) showFilterDialog()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private val onFiltersSelection = object : OnFiltersSelection {
        override fun apply(filters: List<String>) {
            context?.let {
                binding.filterChip.chipIcon = ContextCompat.getDrawable(it, R.drawable.ic_check)
            }
            transactionsAdapter?.setFilters(filters)
        }

        override fun clear() {
            context?.let {
                binding.filterChip.chipIcon = ContextCompat.getDrawable(it, R.drawable.ic_drop_down)
            }
            transactionsAdapter?.setFilters(emptyList())
        }
    }

    private fun showFilterDialog() {
        TransactionsFilterDialog.newInstance(
            onFiltersSelection,
            transactionsAdapter?.getPresentFilterItems(),
            transactionsAdapter?.getAppliedFilters()
        ).show(childFragmentManager, getString(R.string.filters))
    }

    private fun setupAdapter() {
        binding.transactionsView.apply {
            transactionsAdapter = TransactionsAdapter(walletViewModel, onTransactionClickListener)
            layoutManager = LinearLayoutManager(context)
            adapter = transactionsAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                    val totalItemCount = layoutManager.itemCount
                    if (!walletViewModel.isLoadingTransactions() && lastVisibleItemPosition >= totalItemCount - 1)
                        walletViewModel.loadMoreTransactions()
                }
            })
        }

        binding.referralNetworkView.apply {
            referralNetworkAdapter = ReferralNetworkAdapter(requireContext())
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(
                SimpleDividerItemDecoration(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ref_line_divider)
                )
            )
            adapter = referralNetworkAdapter
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            WalletViewModel.TransactionProgressState -> {
                if (!binding.pullToRefresh.isRefreshing) {
                    binding.shimmerViewContainer.visible()
                    binding.shimmerViewContainer.startShimmer()
                    binding.transactionsView.gone()
                    binding.referralNetworkView.gone()
                    binding.noTransactionContainer.gone()
                }
                binding.walletSelector.disabled()
                binding.filterChip.disabled()
            }

            WalletViewModel.TransactionLoadMoreState -> {
                binding.shimmerViewContainer.gone()
                binding.transactionsView.visible()
                binding.referralNetworkView.gone()
                binding.noTransactionContainer.gone()
                binding.walletSelector.disabled()
                binding.filterChip.disabled()
            }

            WalletViewModel.NoTransactionState -> enableViews()

            is WalletViewModel.TransactionsSuccessState -> {
                enableViews()

                if (it.transactions.isNullOrEmpty()) {
                    binding.noTransactionContainer.visible()
                    binding.noTransactionContainer.text = getString(R.string.no_transaction_found)
                    binding.transactionsView.gone()
                    binding.referralNetworkView.gone()
                } else {
                    binding.noTransactionContainer.gone()
                    binding.transactionsView.visible()
                    binding.referralNetworkView.gone()
                    transactionsAdapter?.addAll(it.transactions)
                    transactionsAdapter?.notifyDataSetChanged()
                }
            }

            is WalletViewModel.ReferralTransactionsSuccessState -> {
                enableViews()

                if (it.referralNetwork.isNullOrEmpty()) {
                    binding.noTransactionContainer.visible()
                    binding.noTransactionContainer.text = getString(R.string.no_referral_found)
                    binding.transactionsView.gone()
                    binding.referralNetworkView.gone()
                } else {
                    binding.noTransactionContainer.gone()
                    binding.transactionsView.gone()
                    binding.referralNetworkView.visible()
                    referralNetworkAdapter?.addAll(it.referralNetwork)
                    referralNetworkAdapter?.notifyDataSetChanged()
                }
            }

            is ErrorState -> enableViews()
        }
    }

    private fun enableViews() {
        binding.walletSelector.enabled()
        binding.filterChip.enabled()
        binding.shimmerViewContainer.gone()
        binding.shimmerViewContainer.stopShimmer()
        binding.pullToRefresh.isRefreshing = false
    }

    override fun onDestroyView() {
        walletViewModel.resetPageNumber()
        super.onDestroyView()
    }
}