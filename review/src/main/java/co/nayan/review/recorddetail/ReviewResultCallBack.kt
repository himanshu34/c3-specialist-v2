package co.nayan.review.recorddetail

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.parcelableArrayList
import co.nayan.review.config.Extras
import co.nayan.review.recordsgallery.RecordItem

class ReviewResultCallBack : ActivityResultContract<ReviewCallbackInput, ArrayList<RecordItem>?>() {
    override fun createIntent(context: Context, input: ReviewCallbackInput): Intent {
        return Intent(context, RecordDetailActivity::class.java).apply {
            putExtra(Extras.LANDED_FROM, input.landedFrom)
            putExtra(Extras.CURRENT_TAB, input.currentSelectedTab)
            putExtra(Extras.CURRENT_RECORD_ID, input.recordId)
            putExtra(Extras.SHOW_STAR, true)
            putExtra(Extras.CONTRAST_VALUE, input.contrastValue)
            putExtra(Extras.QUESTION, input.question)
            putExtra(Extras.APPLICATION_MODE, input.applicationMode)
            putExtra(Extras.APP_FLAVOR, input.appFlavor)
            putExtra(Extras.WORK_ASSIGNMENT, input.workAssignment)
            putParcelableArrayListExtra(Extras.RECORD_ITEMS, input.recordItems)
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): ArrayList<RecordItem>? {
        return when {
            resultCode != Activity.RESULT_OK -> null
            else -> intent?.parcelableArrayList(Extras.RECORD_ITEMS)
        }
    }
}

data class ReviewCallbackInput(
    val landedFrom: Int?,
    val currentSelectedTab: Int?,
    val recordId: Int?,
    val contrastValue: Int?,
    val question: String?,
    val applicationMode: String?,
    val appFlavor: String?,
    val workAssignment: WorkAssignment?,
    val recordItems: ArrayList<RecordItem>? = null
)