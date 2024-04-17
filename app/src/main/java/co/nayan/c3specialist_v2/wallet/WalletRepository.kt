package co.nayan.c3specialist_v2.wallet

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.CreatePayoutResponse
import javax.inject.Inject

class WalletRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchWalletDetails() = apiClientFactory.apiClientBase.fetchWalletDetails()

    suspend fun fetchTransactions(walletType: String, pageNumber: Int) =
        apiClientFactory.apiClientBase.fetchTransactions(walletType, pageNumber)

    suspend fun fetchReferralTransactions() =
        apiClientFactory.apiClientBase.fetchReferralTransactions()

    suspend fun createPayout(walletType: String?): CreatePayoutResponse {
        return apiClientFactory.apiClientBase.createPayout(walletType)
    }
}