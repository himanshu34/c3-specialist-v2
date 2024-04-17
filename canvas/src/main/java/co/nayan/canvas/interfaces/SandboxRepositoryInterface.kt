package co.nayan.canvas.interfaces

import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.c3_module.requests.SandboxSubmitAnnotationRequest
import co.nayan.c3v2.core.models.c3_module.responses.SandboxRecordResponse
import co.nayan.c3v2.core.models.c3_module.responses.SandboxRecordsResponse
import co.nayan.c3v2.core.models.c3_module.responses.SandboxSubmitAnswerResponse

interface SandboxRepositoryInterface {
    suspend fun specialistNextRecord(sandboxTrainingId: Int): SandboxRecordResponse?
    suspend fun submitSpecialistAnnotation(
        sandboxTrainingId: Int?, request: SandboxSubmitAnnotationRequest
    ): SandboxRecordResponse?
    fun getSpanCount(): Int
    fun saveSpanCount(count: Int)
    suspend fun fetchTemplates(wfStepId: Int?): List<Template>
    suspend fun addLabel(wfStepId: Int?, displayImage: String?, labelText: String): Pair<String?, List<Template>?>
    suspend fun adminRecords(sandboxTrainingId: Int): SandboxRecordsResponse?
    suspend fun submitAdminAnnotation(
        recordId: Int?, request: SandboxSubmitAnnotationRequest
    ): SandboxSubmitAnswerResponse

    fun saveContrast(value: Int)
    fun getContrast(): Int
    fun shouldPlayHelpVideo(applicationMode: String): Boolean
    fun saveRecentSearchedTemplate(workStepId: Int?, template: Template)
    suspend fun getLearningVideo(applicationMode: String): Video?
}