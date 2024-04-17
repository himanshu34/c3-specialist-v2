package co.nayan.c3specialist_v2.referral

interface IReferralManager {

    fun onApplyReferral(referralCode: String)
    fun onDialogDismiss()
}