package co.nayan.c3specialist_v2.faq

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.illustration.IllustrationActivity
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.parcelable

class FaqResultCallback : ActivityResultContract<FaqCallbackInput, WorkAssignment?>() {
    override fun createIntent(context: Context, input: FaqCallbackInput): Intent {
        val className = if (input.isIllustration) IllustrationActivity::class.java
        else FAQActivity::class.java
        return Intent(context, className).apply {
            putExtra(Extras.WORK_ASSIGNMENT, input.workAssignment)
            putExtra(Extras.USER_ROLE, input.userRole)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): WorkAssignment? {
        return when {
            resultCode != Activity.RESULT_OK -> null
            else -> intent?.parcelable(Extras.WORK_ASSIGNMENT)
        }
    }
}

data class FaqCallbackInput(
    val workAssignment: WorkAssignment?,
    val userRole: String?,
    val isIllustration: Boolean
)