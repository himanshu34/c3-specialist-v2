package com.nayan.nayancamv2.repository.repository_uploader

import co.nayan.c3v2.core.api.SafeApiRequest
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.driver_module.VideoFilesStatusRequest
import co.nayan.c3v2.core.models.driver_module.VideoFilesStatusResponse
import co.nayan.c3v2.core.models.driver_module.VideoUploadRes
import co.nayan.c3v2.core.models.driver_module.VideoUploaderData
import co.nayan.c3v2.core.utils.Constants.VideoUploadStatus.DUPLICATE
import com.nayan.nayancamv2.di.database.VideoUploaderDAO
import com.nayan.nayancamv2.storage.StorageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoUploaderRepositoryImpl @Inject constructor(
    private val nayanCamModuleInteractor: NayanCamModuleInteractor,
    private val storageUtil: StorageUtil,
    private val videoUploaderDAO: VideoUploaderDAO
) : SafeApiRequest(), IVideoUploaderRepository {

    override fun getApiClientFactory() = nayanCamModuleInteractor.getApiClientFactory()

    override suspend fun addToDatabase(videoUploaderData: VideoUploaderData) =
        withContext(Dispatchers.IO) { videoUploaderDAO.addToDatabase(videoUploaderData) }

    override suspend fun entryExists(fileName: String): Int =
        withContext(Dispatchers.IO) { videoUploaderDAO.entryExists(fileName) }

    override suspend fun getOfflineVideosCount() = withContext(Dispatchers.IO) {
        getOfflineVideosBatch().size
    }

    override suspend fun getSyncVideosBatch() = withContext(Dispatchers.IO) {
        videoUploaderDAO.getSyncVideosBatch()
    }

    override suspend fun getOfflineVideosBatch(): MutableList<String> =
        withContext(Dispatchers.IO) {
            storageUtil.saveNDVVideoBatch(videoUploaderDAO.getNDVVideosBatch())
            val offlineVideoBatch = videoUploaderDAO.getOfflineVideosBatch()
            storageUtil.saveOfflineVideoBatch(offlineVideoBatch)
            offlineVideoBatch
        }

    override suspend fun getUnsyncVideosBatch() = withContext(Dispatchers.IO) {
        videoUploaderDAO.getUnsyncVideosBatch()
    }

    override suspend fun updateVideoStatus(
        status: Int, videoId: Int, videoName: String
    ) = withContext(Dispatchers.IO) {
        val currentTimeMillis = System.currentTimeMillis()
        if (status == DUPLICATE)
            videoUploaderDAO.updateDuplicateFileStatus(status, videoName, currentTimeMillis)
        else videoUploaderDAO.updateUploadStatus(status, videoId, videoName, currentTimeMillis)
        getOfflineVideosBatch()
    }

    override suspend fun clearSpecifiedVideoData(videoName: String) = withContext(Dispatchers.IO) {
        videoUploaderDAO.clearSpecifiedVideoData(videoName)
    }

    override suspend fun uploadVideo(
        latitude: RequestBody?,
        longitude: RequestBody?,
        recordedOn: RequestBody?,
        offlineVideoCount: RequestBody?,
        surge: RequestBody?,
        recordedById: RequestBody?,
        speed: RequestBody?,
        video: MultipartBody.Part
    ): Flow<VideoUploadRes> =
        makeSafeRequestForFlow {
            getApiClientFactory().apiClientNayanCam.uploadVideo(
                latitude,
                longitude,
                recordedOn,
                offlineVideoCount,
                surge,
                recordedById,
                speed,
                video
            )
        }


    override suspend fun updateVideoCount(offlineVideoCount: String): Flow<JSONObject> =
        makeSafeRequestForFlow {
            getApiClientFactory().apiClientNayanCam.updateVideoCount(offlineVideoCount)
        }

    override suspend fun checkUploadedVideoFilesStatus(
        videoFilesStatusRequest: VideoFilesStatusRequest
    ): Flow<VideoFilesStatusResponse> = makeSafeRequestForFlow {
        getApiClientFactory().apiClientNayanCam
            .checkUploadedVideoFilesStatus(videoFilesStatusRequest)
    }

    override suspend fun changeVideoFilesStatus(
        goingToDeleteBatch: MutableList<String>,
        syncVideoBatch: MutableList<String>
    ) = withContext(Dispatchers.IO) {
        // Update status of videos which are going to be deleted in next batch to NOT_UPLOADED
        videoUploaderDAO.updateGoingToDeleteVideoBatch(goingToDeleteBatch)
        // Clear database for the list which are confirmed from server
        videoUploaderDAO.clearSyncVideoBatch(syncVideoBatch)
        getOfflineVideosBatch()
    }
}