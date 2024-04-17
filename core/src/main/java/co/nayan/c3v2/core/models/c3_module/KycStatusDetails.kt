package co.nayan.c3v2.core.models.c3_module

data class KycStatusDetails(
    val status: String,
    val statusIconId: Int,
    val statusColorId: Int,
    val kycNumber: String? = null,
    val statusTextId: Int
)