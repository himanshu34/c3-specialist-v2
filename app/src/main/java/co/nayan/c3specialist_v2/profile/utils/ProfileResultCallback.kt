package co.nayan.c3specialist_v2.profile.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.ProfileConstants
import co.nayan.c3specialist_v2.driversetting.DriverSettingActivity
import co.nayan.c3specialist_v2.profile.details.*
import co.nayan.c3specialist_v2.referral.ReferralActivity
import co.nayan.c3v2.core.models.c3_module.ProfileIntentInputData
import timber.log.Timber

class ProfileResultCallback : ActivityResultContract<ProfileIntentInputData, String?>() {
    override fun createIntent(context: Context, input: ProfileIntentInputData): Intent {
        val className = when (input.screenName) {
            ProfileConstants.PERSONAL_INFO -> PersonalInfoActivity::class.java
            ProfileConstants.PAN -> PanDetailsActivity::class.java
            ProfileConstants.PHOTO_ID -> PhotoIdDetailsActivity::class.java
            ProfileConstants.BANK_DETAILS -> BankDetailsActivity::class.java
            ProfileConstants.RESET_PASSWORD -> ResetPasswordActivity::class.java
            ProfileConstants.SETTINGS -> DriverSettingActivity::class.java
            ProfileConstants.REFERRAL -> ReferralActivity::class.java
            else -> null
        }
        return Intent(context, className).apply {
            putExtra(Extras.TOKEN, input.token)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        Timber.d("parseResult:$resultCode")
        return when {
            resultCode != Activity.RESULT_OK -> null
            else -> intent?.getStringExtra(Extras.UPDATED_MESSAGE)
        }
    }
}