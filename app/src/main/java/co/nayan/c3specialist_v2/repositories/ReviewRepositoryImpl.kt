package co.nayan.c3specialist_v2.repositories

import androidx.appcompat.app.AppCompatActivity
import co.nayan.c3specialist_v2.record_visualization.video_type_record.VideoTypeRecordActivity
import co.nayan.c3specialist_v2.storage.ManagerSharedStorage
import co.nayan.c3specialist_v2.utils.createReviewRequest
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.RecordReview
import co.nayan.c3v2.core.models.SubmittedAnswers
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.c3_module.requests.SubmitReviewRequest
import co.nayan.c3v2.core.models.c3_module.responses.SubmitReviewResponse
import co.nayan.review.recordsgallery.ReviewRepositoryInterface
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import javax.inject.Inject

class ReviewRepositoryImpl @Inject constructor(
    private val sharedStorage: ManagerSharedStorage,
    private val apiClientFactory: ApiClientFactory
) : ReviewRepositoryInterface {

    override suspend fun nextRecords(workAssignmentId: Int?): List<Record>? {
        return apiClientFactory.apiClientBase.managerNextRecords(workAssignmentId)?.nextRecords
    }

    override suspend fun fetchTemplates(wfStepId: Int?): List<Template> {
        return apiClientFactory.apiClientBase.fetchTemplates(wfStepId)?.templates
            ?: emptyList()
    }

    override suspend fun submitReview(
        rejectedIds: List<Int>,
        approvedIds: List<Int>,
        sniffingCorrectIds: List<Int>,
        sniffingIncorrectIds: List<Int>
    ): SubmitReviewResponse {
        return apiClientFactory.apiClientBase.submitManagerReview(
            SubmitReviewRequest(rejectedIds, approvedIds, sniffingCorrectIds, sniffingIncorrectIds)
        )
    }

    override suspend fun submitDeveloperReview(
        rejectedIds: List<Int>,
        approvedIds: List<Int>
    ): Boolean {
        return apiClientFactory.apiClientBase.submitAdminReview(
            SubmitReviewRequest(rejectedIds, approvedIds, null, null)
        ).success ?: false
    }

    override fun saveContrast(value: Int) {
        sharedStorage.saveContrast(value)
    }

    override fun getContrast(): Int {
        return sharedStorage.getContrast()
    }

    override fun undoReview() {
        sharedStorage.undoReview()
    }

    override fun saveReview(recordReview: RecordReview) {
        sharedStorage.addReview(recordReview)
    }

    override suspend fun submitSavedReviews(): SubmittedAnswers {
        // If there are any unsynced records, touchDown sync them. Otherwise, the server will
        // return the same records again.
        val annotationIds = mutableListOf<Int>()
        val recordIds = mutableListOf<Int>()
        var isAccountLocked = false
        val incorrectSniffingIds = mutableListOf<Int>()
        val isSniffingPassed = true
        if (sharedStorage.getUnsyncedReviews().count() > 0) {
            val submissions = syncReviews()
            isAccountLocked = submissions.first ?: false
            if (isAccountLocked) incorrectSniffingIds.addAll(submissions.second)
            else recordIds.addAll(submissions.second)
        }

        return SubmittedAnswers(
            recordIds = recordIds,
            annotationIds = annotationIds,
            isAccountLocked = isAccountLocked,
            incorrectSniffingIds = incorrectSniffingIds,
            sniffingPassed = isSniffingPassed
        )
    }

    private suspend fun syncReviews(): Pair<Boolean?, List<Int>> {
        val toSync = sharedStorage.getUnsyncedReviews()
        if (toSync.isNotEmpty()) {
            val request = toSync.createReviewRequest()
            sharedStorage.clearReviews(toSync)
            return try {
                val response = apiClientFactory.apiClientBase.submitManagerReview(request)
                if (response.userAccountLocked == true) {
                    Pair(response.userAccountLocked, request.sniffingIncorrect ?: emptyList())
                } else {
                    Pair(
                        response.userAccountLocked,
                        (request.approved) +
                                (request.rejected) +
                                (request.sniffingCorrect ?: emptyList()) +
                                (request.sniffingIncorrect ?: emptyList())
                    )
                }
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                sharedStorage.addAllReviews(toSync)
                throw e
            }
        }
        return Pair(false, emptyList())
    }

    override fun saveSpanCount(count: Int) {
        sharedStorage.saveSpanCount(count)
    }

    override fun getSpanCount(): Int {
        return sharedStorage.getSpanCount()
    }

    override fun currentRole(): String? {
        return sharedStorage.getRoleForCanvas()
    }

    override fun videoVisualizationActivityClass(): Class<out AppCompatActivity> {
        return VideoTypeRecordActivity::class.java
    }
}