package co.nayan.c3specialist_v2.referral

import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState

interface IGooglePlayInstallManager {

    fun initListener()
    fun subscribe(): MutableLiveData<ActivityState>
    fun getReferralCode(): String?
}