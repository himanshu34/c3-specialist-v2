package co.nayan.c3specialist_v2.applanguage

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.LanguageSuccessResponse
import javax.inject.Inject

class AppLanguageRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun updateLanguage(language: String): LanguageSuccessResponse {
        return apiClientFactory.apiClientBase.saveLanguage(language)
    }
}