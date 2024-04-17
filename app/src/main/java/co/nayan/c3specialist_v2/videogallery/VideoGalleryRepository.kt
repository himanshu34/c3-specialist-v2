package co.nayan.c3specialist_v2.videogallery

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.LearningVideosResponse
import javax.inject.Inject

class VideoGalleryRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory
) {
    suspend fun fetchLearningVideos(): LearningVideosResponse? {
        return apiClientFactory.apiClientBase.getLearningVideos()
    }
}