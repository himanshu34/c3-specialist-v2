package co.nayan.c3v2.core.models

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class HeaderAuthProvider(
    var access_token: String,
    var client: String,
    var expiry: String,
    var uid: String
) : Parcelable

@Keep
@Parcelize
data class User(
    val id: Int,
    val email: String?,
    val name: String?,
    var userImage: String?,
    var address: String?,
    var state: String?,
    val phoneNumber: String?,
    val defaultLanguage: String?,
    val activeRoles: MutableList<String>?,
    val profilePhoto: Image?,
    val panNumber: String?,
    val panImage: Image?,
    val panState: String?,
    val photoIdNumber: String?,
    val photoIdImage: Image?,
    val photoIdState: String?,
    val isPhoneVerified: Boolean?,
    val kycVerified: Boolean?,
    val allowCopy: Boolean?,
    val walkThroughEnabled: Boolean?,
    var country: String?,
    var city: String?,
    val identificationType: String?,
    val deviceModel: String?,
    val isSurveyor: Boolean?,
    val phoneIdToken: String?,
    val referrer: User?,
    val referalCode: String?,
    val isDroneLocationOnly: Boolean?
) : Parcelable

@Keep
@Parcelize
data class Image(
    val url: String?
) : Parcelable

@Keep
@Parcelize
data class BankDetails(
    val beneficiaryName: String?,
    val accountNumber: String?,
    val bankName: String?,
    val bankIfsc: String?,
    val message: String?
) : Parcelable