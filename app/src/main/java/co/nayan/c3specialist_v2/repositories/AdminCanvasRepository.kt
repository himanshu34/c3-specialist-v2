package co.nayan.c3specialist_v2.repositories

import androidx.appcompat.app.AppCompatActivity
import co.nayan.c3specialist_v2.storage.AdminSharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.requests.AdminWorkAssignment
import co.nayan.c3v2.core.models.c3_module.requests.SubmitReviewRequest
import co.nayan.c3v2.core.models.c3_module.responses.AddTemplateRequest
import co.nayan.canvas.interfaces.CanvasRepositoryInterface
import co.nayan.review.incorrectreviews.IncorrectReviewsActivity
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import javax.inject.Inject

class AdminCanvasRepository @Inject constructor(
    private val sharedStorage: AdminSharedStorage,
    private val apiClientFactory: ApiClientFactory
) : CanvasRepositoryInterface {

    override suspend fun fetchRecords(workAssignmentId: Int?, role: String?): List<Record> {
        // Then fetch new records from server
        return apiClientFactory.apiClientBase.adminNextRecords(workAssignmentId)?.nextRecords
            ?: emptyList()
    }

    /**
     * This function stores the judgements locally to start with. Once there are
     * enough judgements locally, then it pushes them to the server.
     *
     * NOTE: We may want to store these in memory instead of SharedStorage. Discuss.
     */
    override fun submitJudgment(recordJudgement: RecordJudgment) {}

    override fun submitReview(recordReview: RecordReview) {
        sharedStorage.addReview(recordReview)
    }

    override fun submitAnnotation(recordAnnotation: RecordAnnotation) {}

    private suspend fun syncReviews(): List<Int> {
        val toSync = sharedStorage.getUnsyncedReviews()
        if (toSync.isNotEmpty()) {
            val request = SubmitReviewRequest(
                toSync.filter { !it.review }.map { it.recordId },
                toSync.filter { it.review }.map { it.recordId },
                null, null
            )
            sharedStorage.clearReviews(toSync)
            return try {
                val response = apiClientFactory.apiClientBase.submitAdminReview(request)
                if (response.success == true) toSync.map { it.recordId }
                else {
                    sharedStorage.addAllReviews(toSync)
                    emptyList()
                }
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                sharedStorage.addAllReviews(toSync)
                throw e
            }
        }
        return emptyList()
    }

    override fun isAllAnswersSubmitted(): Boolean {
        return sharedStorage.getUnsyncedReviews().isEmpty()
    }

    override suspend fun submitSavedAnswers(): SubmittedAnswers {
        val recordIds = mutableListOf<Int>()
        if (sharedStorage.getUnsyncedReviews().isNotEmpty()) {
            val ids = syncReviews()
            recordIds.addAll(ids)
        }
        return SubmittedAnswers(
            recordIds = recordIds,
            annotationIds = emptyList(),
            incorrectSniffingIds = null
        )
    }

    override suspend fun fetchTemplates(wfStepId: Int?): List<Template> {
        return apiClientFactory.apiClientBase.fetchTemplates(wfStepId)?.templates ?: emptyList()
    }

    override suspend fun addLabel(
        wfStepId: Int?,
        displayImage: String?,
        labelText: String
    ): Pair<String?, List<Template>?> {
        val apiRequest = apiClientFactory.apiClientBase.addLabel(
            AddTemplateRequest(
                Template(
                    labelText,
                    remoteTemplateIconUrl = displayImage
                ), wfStepId
            )
        )
        return Pair(apiRequest?.message, apiRequest?.templates)
    }

    override fun saveRecentSearchedTemplate(workStepId: Int?, template: Template) {
        sharedStorage.saveRecentSearchedTemplate(workStepId, template)
    }

    override fun getRecentSearchedTemplate(workStepId: Int?): MutableList<Template> {
        return sharedStorage.getRecentSearchedTemplate(workStepId)
    }

    override fun undoJudgement() {}

    override fun undoAnnotation(): RecordAnnotation? {
        return null
    }

    override fun saveContrast(value: Int) {
        sharedStorage.saveContrast(value)
    }

    override fun getContrast(): Int {
        return sharedStorage.getContrast()
    }

    override fun getSpanCount(): Int {
        return sharedStorage.getSpanCount()
    }

    override fun currentRole(): String? {
        return sharedStorage.getRoleForCanvas()
    }

    override fun clearAnswers() {
        sharedStorage.clearReviews(sharedStorage.getUnsyncedReviews())
    }

    override suspend fun assignWork(): Work? {
        return apiClientFactory.apiClientBase.adminWorkAssignment(AdminWorkAssignment(null))
    }

    override suspend fun submitBNCAnnotations(recordAnnotations: List<RecordAnnotation>) {}

    override suspend fun getLearningVideo(applicationMode: String): Video? {
        return null
    }

    override fun incorrectReviewsRecordsActivityClass(): Class<out AppCompatActivity> {
        return IncorrectReviewsActivity::class.java
    }

    override suspend fun sendCorruptCallback(dataRecordsCorrupt: DataRecordsCorrupt): List<Record> {
        return apiClientFactory.apiClientBase.sendCorruptCallback(dataRecordsCorrupt)?.nextRecords
            ?: emptyList()
    }
}