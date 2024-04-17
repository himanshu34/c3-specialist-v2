package co.nayan.c3specialist_v2.screen_sharing.utils

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class RequestMediaProjection : ActivityResultContract<Intent, Intent?>() {
    override fun createIntent(context: Context, input: Intent): Intent {
        return input
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
        return if (resultCode != RESULT_OK) {
            null
        } else {
            intent
        }
    }
}