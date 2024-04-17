package co.nayan.c3specialist_v2.repositories

import androidx.appcompat.app.AppCompatActivity
import co.nayan.c3specialist_v2.storage.ManagerSharedStorage
import co.nayan.c3specialist_v2.utils.createAnnotationRequest
import co.nayan.c3specialist_v2.utils.createJudgmentRequest
import co.nayan.c3specialist_v2.utils.createReviewRequest
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.responses.AddTemplateRequest
import co.nayan.canvas.interfaces.CanvasRepositoryInterface
import co.nayan.review.incorrectreviews.IncorrectReviewsActivity
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import javax.inject.Inject

class ManagerCanvasRepository @Inject constructor(
    private val sharedStorage: ManagerSharedStorage,
    private val apiClientFactory: ApiClientFactory
) : CanvasRepositoryInterface {

    override suspend fun fetchRecords(workAssignmentId: Int?, role: String?): List<Record> {
        // Then fetch new records from server
        return apiClientFactory.apiClientBase.managerNextRecords(workAssignmentId)?.nextRecords ?: emptyList()
    }

    /**
     * This function stores the judgements locally to start with. Once there are
     * enough judgements locally, then it pushes them to the server.
     *
     * NOTE: We may want to store these in memory instead of SharedStorage. Discuss.
     */
    override fun submitJudgment(recordJudgement: RecordJudgment) {
        sharedStorage.addJudgment(recordJudgement)
    }

    override fun submitReview(recordReview: RecordReview) {
        sharedStorage.addReview(recordReview)
    }

    override fun submitAnnotation(recordAnnotation: RecordAnnotation) {
        sharedStorage.addAnnotation(recordAnnotation)
    }

    private suspend fun syncJudgments(): SubmittedAnswers {
        val toSync = sharedStorage.getUnsyncedJudgments()
        if (toSync.isNotEmpty()) {
            val request = toSync.createJudgmentRequest()
            sharedStorage.clearJudgments(toSync)
            try {
                val response = apiClientFactory.apiClientBase.managerSubmitJudgments(request)
                return SubmittedAnswers(
                    annotationIds = response.successIds ?: emptyList(),
                    recordIds = (request.sniffingCorrect ?: emptyList()) +
                            (request.sniffingIncorrect ?: emptyList()),
                    isAccountLocked = false,
                    incorrectSniffingIds = null,
                    sniffingPassed = response.sniffingPassed ?: true
                )
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                sharedStorage.addAllJudgments(toSync)
                throw e
            }
        }
        return SubmittedAnswers(
            emptyList(), emptyList(), isAccountLocked = false,
            incorrectSniffingIds = null
        )
    }

    private suspend fun syncAnnotations(): Pair<Boolean, List<Int>> {
        val toSync = sharedStorage.getUnsyncedAnnotations()
        if (toSync.isNotEmpty()) {
            val request = toSync.createAnnotationRequest()
            sharedStorage.clearAnnotations(toSync)
            try {
                val response = apiClientFactory.apiClientBase.managerSubmitAnnotations(request)
                return Pair(
                    response.sniffingPassed ?: true,
                    (response.successIds ?: emptyList()) +
                            (request.sniffingCorrect ?: emptyList()) +
                            (request.sniffingIncorrect ?: emptyList())
                )
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                sharedStorage.addAllAnnotations(toSync)
                throw e
            }
        }
        return Pair(true, emptyList())
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

    override fun isAllAnswersSubmitted(): Boolean {
        return sharedStorage.getUnsyncedJudgments()
            .isEmpty() && sharedStorage.getUnsyncedAnnotations().isEmpty()
                && sharedStorage.getUnsyncedReviews().isEmpty()
    }

    override suspend fun submitSavedAnswers(): SubmittedAnswers {
        // If there are any unsynced judgements, touchDown sync them. Otherwise, the server will
        // return the same records again.
        val annotationIds = mutableListOf<Int>()
        val recordIds = mutableListOf<Int>()
        var isAccountLocked = false
        val incorrectSniffingIds = mutableListOf<Int>()
        var isSniffingPassed = true
        if (sharedStorage.getUnsyncedJudgments().isNotEmpty()) {
            val submissions = syncJudgments()
            isSniffingPassed = submissions.sniffingPassed
            annotationIds.addAll(submissions.annotationIds ?: emptyList())
            recordIds.addAll(submissions.recordIds ?: emptyList())
        }
        if (sharedStorage.getUnsyncedAnnotations().isNotEmpty()) {
            val submissions = syncAnnotations()
            isSniffingPassed = submissions.first
            recordIds.addAll(submissions.second)
        }
        if (sharedStorage.getUnsyncedReviews().isNotEmpty()) {
            val submissions = syncReviews()
            isAccountLocked = submissions.first ?: false
            if (isAccountLocked) {
                incorrectSniffingIds.addAll(submissions.second)
            } else {
                recordIds.addAll(submissions.second)
            }
        }

        return SubmittedAnswers(
            recordIds = recordIds,
            annotationIds = annotationIds,
            isAccountLocked = isAccountLocked,
            incorrectSniffingIds = incorrectSniffingIds,
            sniffingPassed = isSniffingPassed
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

    override fun undoJudgement() {
        sharedStorage.undoJudgment()
    }

    override fun undoAnnotation(): RecordAnnotation? {
        return sharedStorage.undoAnnotation()
    }

    override fun saveContrast(value: Int) {
        sharedStorage.saveContrast(value)
    }

    override fun getContrast(): Int {
        return sharedStorage.getContrast()
    }

    override fun currentRole(): String? {
        return sharedStorage.getRoleForCanvas()
    }

    override fun clearAnswers() {
        sharedStorage.clearAnnotations(sharedStorage.getUnsyncedAnnotations())
        sharedStorage.clearJudgments(sharedStorage.getUnsyncedJudgments())
        sharedStorage.clearReviews(sharedStorage.getUnsyncedReviews())
    }

    override suspend fun assignWork(): Work? {
        return apiClientFactory.apiClientBase.managerWorkAssignment()
    }

    override suspend fun submitBNCAnnotations(recordAnnotations: List<RecordAnnotation>) {
        if (recordAnnotations.isNotEmpty()) {
            val request = recordAnnotations.createAnnotationRequest()
            apiClientFactory.apiClientBase.managerSubmitAnnotations(request)
        }
    }

    override suspend fun getLearningVideo(applicationMode: String): Video? {
        return sharedStorage.getLearningVideos(currentRole())?.find {
            applicationMode.equals(it.applicationModeName, ignoreCase = true)
        }
    }

    override fun incorrectReviewsRecordsActivityClass(): Class<out AppCompatActivity> {
        return IncorrectReviewsActivity::class.java
    }

    override suspend fun sendCorruptCallback(dataRecordsCorrupt: DataRecordsCorrupt): List<Record> {
        return apiClientFactory.apiClientBase.sendCorruptCallback(dataRecordsCorrupt)?.nextRecords ?: emptyList()
    }

    override fun getSpanCount(): Int {
        return sharedStorage.getSpanCount()
    }
}