package co.nayan.c3specialist_v2.wallet

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import co.nayan.c3specialist_v2.config.BaseViewModel
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.config.WalletType
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.PayoutResponse
import co.nayan.c3v2.core.models.c3_module.ReferralUser
import co.nayan.c3v2.core.models.c3_module.Transaction
import co.nayan.c3v2.core.models.c3_module.WalletDetails
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _state: MutableLiveData<ActivityState> = MutableLiveData()
    val state: LiveData<ActivityState> = _state

    private val _walletDetails: MutableLiveData<WalletDetails?> = MutableLiveData(null)
    val walletDetails: LiveData<WalletDetails?> = _walletDetails

    private val _walletType: MutableLiveData<String> = MutableLiveData(WalletType.DRIVER)
    val walletType: LiveData<String> = _walletType

    private val _isManagerWalletPresent: MutableLiveData<Boolean> = MutableLiveData(false)
    val isManagerWalletPresent: LiveData<Boolean> = _isManagerWalletPresent

    private val _isDriverWalletPresent: MutableLiveData<Boolean> = MutableLiveData(false)
    val isDriverWalletPresent: LiveData<Boolean> = _isDriverWalletPresent

    private val _isSpecialistWalletPresent: MutableLiveData<Boolean> = MutableLiveData(false)
    val isSpecialistWalletPresent: LiveData<Boolean> = _isSpecialistWalletPresent

    private val _isBonusWalletPresent: MutableLiveData<Boolean> = MutableLiveData(false)
    val isBonusWalletPresent: LiveData<Boolean> = _isBonusWalletPresent

    private val _isReferralWalletPresent: MutableLiveData<Boolean> = MutableLiveData(false)
    val isReferralWalletPresent: LiveData<Boolean> = _isReferralWalletPresent

    private val _isLeaderWalletPresent: MutableLiveData<Boolean> = MutableLiveData(false)
    val isLeaderWalletPresent: LiveData<Boolean> = _isLeaderWalletPresent

    private var transactionsLoadingJob: Job? = null
    private var currentPage = 0
    var shouldStopLoading = false
    private val loadedPageNumbers = mutableSetOf<Int>()
    private val transactionsList = mutableListOf<Transaction>()

    fun fetchWalletDetails() = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        val response = walletRepository.fetchWalletDetails()
        setupWalletDetails(response)
        _state.value = WalletDetailsSuccessState(response.message)
    }

    fun createPayout(walletType: String) = viewModelScope.launch(exceptionHandler) {
        _state.value = ProgressState
        _state.value = if (walletType.canCheckout()) {
            val response = walletRepository.createPayout(walletType)
            fetchWalletDetails()
            if (walletType == WalletType.REFERRAL_NETWORK)
                fetchReferralTransactions(walletType)
            else loadMoreTransactions(walletType)
            _walletType.value = walletType
            CreatePayoutSuccessState(response.message)
        } else CheckOutNotPossibleState(walletType)
    }

    private fun setupWalletDetails(data: PayoutResponse) {
        val userActiveRoles = userRepository.getUserRoles()
        val driverWallet = data.wallets?.find { it.walletType == WalletType.DRIVER }
        val specialistWallet = data.wallets?.find { it.walletType == WalletType.SPECIALIST }
        val managerWallet = data.wallets?.find { it.walletType == WalletType.MANAGER }
        val bonusWallet = data.wallets?.find { it.walletType == WalletType.BONUS }
        val leaderWallet = data.wallets?.find { it.walletType == WalletType.LEADER }
        val referralWallet = data.wallets?.find { it.walletType == WalletType.REFERRAL }

        _isManagerWalletPresent.value = if (userActiveRoles.contains(Role.MANAGER))
            managerWallet != null else false
        _isDriverWalletPresent.value = if (userActiveRoles.contains(Role.DRIVER))
            driverWallet != null else false
        _isSpecialistWalletPresent.value = if (userActiveRoles.contains(Role.SPECIALIST))
            specialistWallet != null else false
        _isBonusWalletPresent.value = bonusWallet != null
        _isLeaderWalletPresent.value = if (userActiveRoles.contains(Role.LEADER))
            leaderWallet != null else false
        _isReferralWalletPresent.value = referralWallet != null

        val checkoutLimit = data.accruedBalance
        val pointsPerRupee = data.pointsPerRupee

        val driverWalletPoints = driverWallet?.score?.toFloat() ?: 0f
        val specialistWalletPoints = specialistWallet?.score?.toFloat() ?: 0f
        val managerWalletPoints = managerWallet?.score?.toFloat() ?: 0f
        val bonusPoints = bonusWallet?.score?.toFloat() ?: 0f
        val referralPoints = referralWallet?.score?.toFloat() ?: 0f
        val leaderPoints = leaderWallet?.score?.toFloat() ?: 0f

        val specialistWalletAmount: Float
        val managerWalletAmount: Float
        val driverWalletAmount: Float
        val bonusAmount: Float
        val referralAmount: Float
        val leaderAmount: Float

        if (pointsPerRupee == null) {
            specialistWalletAmount = 0f
            managerWalletAmount = 0f
            driverWalletAmount = 0f
            bonusAmount = 0f
            referralAmount = 0f
            leaderAmount = 0f
        } else {
            specialistWalletAmount = specialistWalletPoints / pointsPerRupee
            managerWalletAmount = managerWalletPoints / pointsPerRupee
            driverWalletAmount = driverWalletPoints / pointsPerRupee
            bonusAmount = bonusPoints / pointsPerRupee
            referralAmount = referralPoints / pointsPerRupee
            leaderAmount = leaderPoints / pointsPerRupee
        }

        _walletDetails.value = WalletDetails(
            checkoutLimit,
            specialistWalletAmount,
            specialistWalletPoints,
            managerWalletAmount,
            managerWalletPoints,
            driverWalletAmount,
            driverWalletPoints,
            bonusAmount,
            bonusPoints,
            referralAmount,
            referralPoints,
            leaderAmount,
            leaderPoints
        )
    }

    fun resetPageNumber() = viewModelScope.launch {
        shouldStopLoading = false
        loadedPageNumbers.clear()
        transactionsList.clear()
        if (isLoadingTransactions()) transactionsLoadingJob?.cancelAndJoin()
    }

    fun isLoadingTransactions() = transactionsLoadingJob?.isActive ?: false

    private fun findNextPageNumberToLoad(): Int {
        currentPage = loadedPageNumbers.maxOrNull() ?: 0
        return (++currentPage)
    }

    fun loadMoreTransactions(walletType: String? = null) = viewModelScope.launch(exceptionHandler) {
        val isSameWalletType = (walletType == null || _walletType.value == walletType)
        if (isSameWalletType.not()) resetPageNumber().join()
        val type = walletType ?: run { _walletType.value ?: return@launch }
        if (shouldStopLoading.not() && transactionsLoadingJob == null || !isLoadingTransactions()) {
            transactionsLoadingJob = fetchTransactions(type, findNextPageNumberToLoad())
        }
    }

    private fun fetchTransactions(
        walletType: String,
        pageNumber: Int
    ) = viewModelScope.launch(exceptionHandler) {
        _walletType.value = walletType
        _state.value = if (pageNumber <= 1) TransactionProgressState else TransactionLoadMoreState
        val data = walletRepository.fetchTransactions(walletType, pageNumber)
        _state.value = data.transactions?.let { transactions ->
            loadedPageNumbers.add(pageNumber)
            val sortedTransactions = transactions.sortedByDescending { it.updatedAt }
            transactionsList.addAll(sortedTransactions)
            shouldStopLoading = (sortedTransactions.isEmpty() || sortedTransactions.size < 10)
            TransactionsSuccessState(transactionsList, data.message, walletType)
        } ?: run {
            shouldStopLoading = true
            NoTransactionState
        }
    }

    fun fetchReferralTransactions(
        walletType: String
    ) = viewModelScope.launch(exceptionHandler) {
        _walletType.value = walletType
        _state.value = TransactionProgressState
        val data = walletRepository.fetchReferralTransactions()
        val actualReferralNetwork =
            data.referalNetwork?.filter { it.isNotEmpty() }?.toMutableList()
        _state.value = ReferralTransactionsSuccessState(actualReferralNetwork)
    }

    override fun throwException(e: Exception) {
        _state.postValue(ErrorState(e))
        Timber.e(e)
        Firebase.crashlytics.recordException(e)
    }

    private fun String.canCheckout(): Boolean {
        return walletDetails.value?.let { details ->
            when (this) {
                WalletType.SPECIALIST -> details.specialistWalletPoints >= MIN_PAYOUT_LIMIT
                WalletType.MANAGER -> details.managerWalletPoints >= MIN_PAYOUT_LIMIT
                WalletType.BONUS -> details.bonusPoints >= MIN_PAYOUT_LIMIT
                WalletType.DRIVER -> details.driverWalletPoints >= MIN_PAYOUT_LIMIT
                WalletType.LEADER -> details.leaderWalletPoints >= MIN_PAYOUT_LIMIT
                WalletType.REFERRAL -> details.referralPoints >= MIN_PAYOUT_LIMIT
                else -> false
            }
        } ?: false
    }

    data class WalletDetailsSuccessState(val message: String?) : ActivityState()
    data class CreatePayoutSuccessState(val message: String?) : ActivityState()
    data class TransactionsSuccessState(
        val transactions: List<Transaction>?, val message: String?, val walletType: String
    ) : ActivityState()

    data class ReferralTransactionsSuccessState(
        val referralNetwork: MutableList<MutableList<ReferralUser?>>?
    ) : ActivityState()

    data class CheckOutNotPossibleState(val walletType: String) : ActivityState()

    object TransactionProgressState : ActivityState()
    object TransactionLoadMoreState : ActivityState()
    object NoTransactionState : ActivityState()

    companion object {
        const val MIN_PAYOUT_LIMIT = 100
    }
}