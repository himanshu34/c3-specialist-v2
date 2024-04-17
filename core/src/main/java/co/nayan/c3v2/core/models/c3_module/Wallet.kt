package co.nayan.c3v2.core.models.c3_module

import android.os.Parcelable
import co.nayan.c3v2.core.models.User
import kotlinx.parcelize.Parcelize
import java.util.*

data class PayoutResponse(
    val wallets: List<Wallet>?,
    val minPayout: Int?,
    val accruedBalance: Float?,
    val pointsPerRupee: Float?,
    val transactions: List<Transaction>?,
    val message: String?
)

data class ReferralPayoutResponse(
    val referalNetwork: MutableList<MutableList<ReferralUser?>>?,
)

data class ReferralUser(
    val name: String?,
    val photoUrl: String?,
    val amount: String?
)

data class Wallet(
    val id: Int,
    val score: String?,
    val walletType: String?,
    val dispersedAmountRupees: Int?,
    val dispersedAmountPaise: Int?,
    val lastCalculatedAt: String?
)

@Parcelize
data class Transaction(
    val id: Int,
    val score: String?,
    val amountRupees: Int?,
    val amountPaise: Int?,
    val aasmState: String?,
    val transactionReference: String?,
    val errorMessage: String?,
    val createdAt: Date?,
    val updatedAt: Date?,
    val walletId: Int?,
    val transactionMethod: String?,
    val companyName: String?,
    val companyAddress: String?,
    val companyGst: String?,
    val payoutAmount: Float?,
    val payoutFee: Float?,
    val payoutTds: Float?,
    val referralAmount: Float?,
    val referralFee: Float?,
    val referralTds: Float?,
    val totalFee: Float?,
    val totalTds: Float?,
    val bonusAmount: Float?,
    val penaltyAmount: Float?,
    val amountBeforeAdjustments: Float?,
    val amountAfterAdjustments: Float?,
    val netAmount: Float?,
    val finalAmount: Float?,
    val totalAmount: Float?,
    val user: User?,
    val receiptUrl: String?
) : Parcelable

data class AuthenticationResponse(
    val message: String?,
    val isCorrectPassword: Boolean?,
    val idToken: String?
)

data class CreatePayoutResponse(
    val message: String?
)

data class WalletDetails(
    val checkoutLimit: Float?,
    val specialistWalletAmount: Float,
    val specialistWalletPoints: Float,
    val managerWalletAmount: Float,
    val managerWalletPoints: Float,
    val driverWalletAmount: Float,
    val driverWalletPoints: Float,
    val bonusAmount: Float,
    val bonusPoints: Float,
    val referralAmount: Float,
    val referralPoints: Float,
    val leaderWalletAmount: Float,
    val leaderWalletPoints: Float
)

class WalletErrorModel {
    var message: String? = null
    var hasFailedForBankDetails: Boolean? = null
    var hasFailedForSubscription: Boolean? = null
    var success: Boolean? = null
    var errors: MutableList<String>? = null
}