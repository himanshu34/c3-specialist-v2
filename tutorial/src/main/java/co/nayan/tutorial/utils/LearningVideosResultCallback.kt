package co.nayan.tutorial.utils

import android.app.Activity.RESULT_CANCELED
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.tutorial.LearningVideoPlayerActivity
import co.nayan.tutorial.config.LearningVideosExtras.IS_VIDEO_COMPLETED
import co.nayan.tutorial.config.LearningVideosExtras.LEARNING_VIDEO
import co.nayan.tutorial.config.LearningVideosExtras.SHOW_DONE_BUTTON
import co.nayan.tutorial.config.LearningVideosExtras.WORK_ASSIGNMENT

class LearningVideosResultCallback :
    ActivityResultContract<LearningVideosContractInput, LearningVideosContractOutput?>() {
    override fun createIntent(context: Context, input: LearningVideosContractInput): Intent {
        return Intent(context, LearningVideoPlayerActivity::class.java).apply {
            putExtra(SHOW_DONE_BUTTON, true)
            putExtra(LEARNING_VIDEO, input.video)
            putExtra(WORK_ASSIGNMENT, input.workAssignment)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): LearningVideosContractOutput? {
        return when {
            resultCode != RESULT_CANCELED -> null
            else -> LearningVideosContractOutput(
                isVideoCompleted = intent?.getBooleanExtra(IS_VIDEO_COMPLETED, false),
                video = intent?.parcelable(LEARNING_VIDEO),
                workAssignment = intent?.parcelable(WORK_ASSIGNMENT)
            )
        }
    }
}

data class LearningVideosContractInput(
    val showDoneButton: Boolean?,
    val video: Video?,
    val workAssignment: WorkAssignment?
)

data class LearningVideosContractOutput(
    val isVideoCompleted: Boolean?,
    val video: Video?,
    val workAssignment: WorkAssignment?
)