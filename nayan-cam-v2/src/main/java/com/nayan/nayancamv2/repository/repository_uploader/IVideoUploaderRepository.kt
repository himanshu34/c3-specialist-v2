package com.nayan.nayancamv2.repository.repository_uploader

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.driver_module.VideoFilesStatusRequest
import co.nayan.c3v2.core.models.driver_module.VideoFilesStatusResponse
import co.nayan.c3v2.core.models.driver_module.VideoUploadRes
import co.nayan.c3v2.core.models.driver_module.VideoUploaderData
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject

interface IVideoUploaderRepository {

    fun getApiClientFactory(): ApiClientFactory

    suspend fun addToDatabase(videoUploaderData: VideoUploaderData)
    suspend fun entryExists(fileName: String): Int
    suspend fun getOfflineVideosCount(): Int
    suspend fun getSyncVideosBatch(): MutableList<String>
    suspend fun getOfflineVideosBatch(): MutableList<String>
    suspend fun getUnsyncVideosBatch(): MutableList<VideoUploaderData>
    suspend fun updateVideoStatus(status: Int, videoId: Int, videoName: String): MutableList<String>
    suspend fun clearSpecifiedVideoData(videoName: String)

    suspend fun uploadVideo(
        latitude: RequestBody?,
        longitude: RequestBody?,
        recordedOn: RequestBody?,
        offlineVideoCount: RequestBody?,
        surge: RequestBody?,
        recordedById: RequestBody?,
        speed: RequestBody?,
        video: MultipartBody.Part
    ): Flow<VideoUploadRes?>

    suspend fun updateVideoCount(offlineVideoCount: String): Flow<JSONObject?>

    suspend fun checkUploadedVideoFilesStatus(
        videoFilesStatusRequest: VideoFilesStatusRequest
    ): Flow<VideoFilesStatusResponse>

    suspend fun changeVideoFilesStatus(
        goingToDeleteBatch: MutableList<String>,
        syncVideoBatch: MutableList<String>
    ): MutableList<String>
}