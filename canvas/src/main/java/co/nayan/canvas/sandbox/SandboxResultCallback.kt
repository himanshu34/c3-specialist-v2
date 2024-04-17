package co.nayan.canvas.sandbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.WfStep
import co.nayan.c3v2.core.models.c3_module.requests.SandboxRefreshDataRequest
import co.nayan.c3v2.core.utils.Constants.Extras
import co.nayan.c3v2.core.utils.parcelable

class SandboxResultCallback :
    ActivityResultContract<SandboxCallbackInput, SandboxRefreshDataRequest?>() {
    override fun createIntent(context: Context, input: SandboxCallbackInput): Intent {
        return Intent(context, SandboxActivity::class.java).apply {
            putExtra(Extras.WF_STEP, input.wfStep)
            putExtra(Extras.SANDBOX_RECORD, input.record)
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): SandboxRefreshDataRequest? {
        return when {
            resultCode != Activity.RESULT_OK -> null
            else -> intent?.parcelable("SandboxData")
        }
    }
}

data class SandboxCallbackInput(
    val wfStep: WfStep?,
    val record: Record
)