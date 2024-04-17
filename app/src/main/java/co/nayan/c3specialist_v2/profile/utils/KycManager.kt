package co.nayan.c3specialist_v2.profile.utils

import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.KycStatus
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.models.c3_module.KycStatusDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class KycManager @Inject constructor(private val sharedStorage: SharedStorage) {

    suspend fun getKycStatusDetails(): KycStatusDetails {
        val status = getKycStatus()
        return getStatusDetails(status)
    }

    suspend fun getPanDetails(): KycStatusDetails {
        val panStatus = getPanStatus()
        return getStatusDetails(panStatus.first, panStatus.second)
    }

    suspend fun getPhotoIdDetails(): KycStatusDetails {
        val photoIdStatus = getPhotoIdStatus()
        return getStatusDetails(photoIdStatus.first, photoIdStatus.second)
    }

    private suspend fun getStatusDetails(
        status: String,
        number: String? = null
    ): KycStatusDetails = withContext(Dispatchers.Default) {
        val statusColorId: Int
        val statusIconId: Int
        val statusTextId: Int
        when (status) {
            KycStatus.NOT_UPLOADED -> {
                statusIconId = R.drawable.ic_missing
                statusColorId = R.color.missing
                statusTextId = R.string.not_uploaded
            }

            KycStatus.PENDING_VERIFICATION -> {
                statusIconId = R.drawable.ic_pending
                statusColorId = R.color.pending
                statusTextId = R.string.pending_verification
            }

            KycStatus.REJECTED -> {
                statusIconId = R.drawable.ic_rejected
                statusColorId = R.color.rejected
                statusTextId = R.string.rejected
            }

            else -> {
                statusIconId = R.drawable.ic_verified
                statusColorId = R.color.verified
                statusTextId = R.string.verified
            }
        }

        return@withContext KycStatusDetails(
            status,
            statusIconId,
            statusColorId,
            number,
            statusTextId
        )
    }

    private fun getPanStatus(): Pair<String, String?> {
        val userInfo = sharedStorage.getUserProfileInfo()
        val number = userInfo?.panNumber
        val state = userInfo?.panState ?: KycStatus.NOT_UPLOADED
        return Pair(state, number)
    }

    private fun getPhotoIdStatus(): Pair<String, String?> {
        val userInfo = sharedStorage.getUserProfileInfo()
        val number = userInfo?.photoIdNumber
        val state = userInfo?.photoIdState ?: KycStatus.NOT_UPLOADED
        return Pair(state, number)
    }

    private suspend fun getKycStatus(): String = withContext(Dispatchers.Default) {
        val panStatus = getPanStatus().first
        val photoIdStatus = getPhotoIdStatus().first

        return@withContext if (panStatus == photoIdStatus) panStatus
        else {
            if (panStatus == KycStatus.NOT_UPLOADED || photoIdStatus == KycStatus.NOT_UPLOADED) {
                KycStatus.NOT_UPLOADED
            } else if (panStatus == KycStatus.REJECTED || photoIdStatus == KycStatus.REJECTED) {
                KycStatus.REJECTED
            } else if (panStatus == KycStatus.PENDING_VERIFICATION || photoIdStatus == KycStatus.PENDING_VERIFICATION) {
                KycStatus.PENDING_VERIFICATION
            } else KycStatus.VERIFIED
        }
    }
}