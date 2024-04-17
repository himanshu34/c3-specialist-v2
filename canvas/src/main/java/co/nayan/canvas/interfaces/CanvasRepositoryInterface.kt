package co.nayan.canvas.interfaces

import androidx.appcompat.app.AppCompatActivity
import co.nayan.c3v2.core.models.*

interface CanvasRepositoryInterface {
    suspend fun fetchRecords(workAssignmentId: Int?, role: String?): List<Record>?
    fun submitJudgment(recordJudgement: RecordJudgment)
    fun submitReview(recordReview: RecordReview)
    fun submitAnnotation(recordAnnotation: RecordAnnotation)
    fun undoJudgement()
    fun undoAnnotation(): RecordAnnotation?
    fun saveContrast(value: Int)
    fun getContrast(): Int
    fun getSpanCount(): Int
    fun isAllAnswersSubmitted(): Boolean
    suspend fun submitSavedAnswers(): SubmittedAnswers
    suspend fun fetchTemplates(wfStepId: Int?): List<Template>
    suspend fun addLabel(wfStepId: Int?, displayImage: String?, labelText: String): Pair<String?, List<Template>?>
    fun saveRecentSearchedTemplate(workStepId: Int?, template: Template)
    fun getRecentSearchedTemplate(workStepId: Int?): MutableList<Template>
    fun currentRole(): String?
    suspend fun assignWork(): Work?
    fun clearAnswers()
    suspend fun submitBNCAnnotations(recordAnnotations: List<RecordAnnotation>)
    suspend fun getLearningVideo(applicationMode: String): Video?
    fun incorrectReviewsRecordsActivityClass(): Class<out AppCompatActivity>
    suspend fun sendCorruptCallback(dataRecordsCorrupt: DataRecordsCorrupt): List<Record>?
}