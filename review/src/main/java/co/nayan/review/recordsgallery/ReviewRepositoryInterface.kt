package co.nayan.review.recordsgallery

import androidx.appcompat.app.AppCompatActivity
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.RecordReview
import co.nayan.c3v2.core.models.SubmittedAnswers
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.c3_module.responses.SubmitReviewResponse

interface ReviewRepositoryInterface {
    suspend fun nextRecords(workAssignmentId: Int?): List<Record>?
    suspend fun fetchTemplates(wfStepId: Int?): List<Template>
    fun getSpanCount(): Int
    fun saveSpanCount(count: Int)
    fun saveContrast(value: Int)
    fun getContrast(): Int
    fun undoReview()
    fun saveReview(recordReview: RecordReview)
    suspend fun submitSavedReviews(): SubmittedAnswers
    suspend fun submitReview(
        rejectedIds: List<Int>,
        approvedIds: List<Int>,
        sniffingCorrectIds: List<Int>,
        sniffingIncorrectIds: List<Int>
    ): SubmitReviewResponse

    suspend fun submitDeveloperReview(rejectedIds: List<Int>, approvedIds: List<Int>): Boolean
    fun currentRole(): String?
    fun videoVisualizationActivityClass(): Class<out AppCompatActivity>
}