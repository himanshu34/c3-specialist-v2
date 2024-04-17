package co.nayan.c3specialist_v2.repositories

import co.nayan.c3specialist_v2.storage.SpecialistSharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.config.Role.SPECIALIST
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.c3_module.requests.SandboxSubmitAnnotationRequest
import co.nayan.c3v2.core.models.c3_module.responses.AddTemplateRequest
import co.nayan.c3v2.core.models.c3_module.responses.SandboxRecordResponse
import co.nayan.c3v2.core.models.c3_module.responses.SandboxRecordsResponse
import co.nayan.c3v2.core.models.c3_module.responses.SandboxSubmitAnswerResponse
import co.nayan.canvas.interfaces.SandboxRepositoryInterface
import javax.inject.Inject

class SpecialistSandboxRepository @Inject constructor(
    private val sharedStorage: SpecialistSharedStorage,
    private val apiClientFactory: ApiClientFactory
) : SandboxRepositoryInterface {

    override suspend fun specialistNextRecord(sandboxTrainingId: Int): SandboxRecordResponse? {
        return apiClientFactory.apiClientBase.specialistNextRecord(sandboxTrainingId)
    }

    override suspend fun submitSpecialistAnnotation(
        sandboxTrainingId: Int?, request: SandboxSubmitAnnotationRequest
    ): SandboxRecordResponse? {
        return apiClientFactory.apiClientBase.submitSpecialistAnnotation(
            sandboxTrainingId,
            request
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

    override suspend fun adminRecords(sandboxTrainingId: Int): SandboxRecordsResponse? {
        return apiClientFactory.apiClientBase.adminRecords(sandboxTrainingId)
    }

    override suspend fun submitAdminAnnotation(
        recordId: Int?, request: SandboxSubmitAnnotationRequest
    ): SandboxSubmitAnswerResponse {
        return apiClientFactory.apiClientBase.submitAdminAnnotation(recordId, request)
    }

    override fun saveContrast(value: Int) {
        sharedStorage.saveContrast(value)
    }

    override fun getContrast(): Int {
        return sharedStorage.getContrast()
    }

    override fun shouldPlayHelpVideo(applicationMode: String): Boolean {
        return sharedStorage.shouldPlayHelpVideo(applicationMode)
    }

    override fun saveRecentSearchedTemplate(workStepId: Int?, template: Template) {
        sharedStorage.saveRecentSearchedTemplate(workStepId, template)
    }

    override suspend fun getLearningVideo(applicationMode: String): Video? {
        return sharedStorage.getLearningVideos(SPECIALIST)?.find {
            applicationMode.equals(it.applicationModeName, ignoreCase = true)
        }
    }

    override fun getSpanCount(): Int {
        return sharedStorage.getSpanCount()
    }

    override fun saveSpanCount(count: Int) {
        sharedStorage.saveSpanCount(count)
    }
}