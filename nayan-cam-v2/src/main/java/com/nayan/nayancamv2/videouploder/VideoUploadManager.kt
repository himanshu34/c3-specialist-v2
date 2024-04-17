@file:Suppress("LocalVariableName")

package com.nayan.nayancamv2.videouploder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.models.CameraConfig
import co.nayan.c3v2.core.models.FailureState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.driver_module.VideoFilesStatusRequest
import co.nayan.c3v2.core.models.driver_module.VideoUploaderData
import co.nayan.c3v2.core.toPrettyJson
import co.nayan.c3v2.core.utils.Constants.VideoUploadStatus.DUPLICATE
import co.nayan.c3v2.core.utils.Constants.VideoUploadStatus.NOT_UPLOADED
import co.nayan.c3v2.core.utils.Constants.VideoUploadStatus.UPLOADED
import co.nayan.nayancamv2.R
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.gson.JsonObject
import com.nayan.nayancamv2.getActualVideoFile
import com.nayan.nayancamv2.getBatteryLevel
import com.nayan.nayancamv2.getCurrentDayOfMonth
import com.nayan.nayancamv2.getDayOfMonth
import com.nayan.nayancamv2.getLatitudeFromFileName
import com.nayan.nayancamv2.getListOfVideoFiles
import com.nayan.nayancamv2.getLongitudeFromFileName
import com.nayan.nayancamv2.getUTCTime
import com.nayan.nayancamv2.helper.GlobalParams.videoUploadingStatus
import com.nayan.nayancamv2.repository.repository_uploader.IVideoUploaderRepository
import com.nayan.nayancamv2.storage.FileMetaDataEditor
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.util.Constants.MOBILE_DATA
import com.nayan.nayancamv2.util.Constants.VIDEO_UPLOADER_STATUS
import com.nayan.nayancamv2.util.Constants.WIFI_DATA
import com.nayan.nayancamv2.util.DriverErrorUtils
import com.nayan.nayancamv2.util.ServerErrorCallback
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Util class to upload video
 *
 * @property context
 * @property storageUtil
 * @property videoUploaderRepository
 * @property fileMetaDataEditor
 * @property cameraConfig
 * @property nayanCamModuleInteractor
 * @property driverErrorUtils
 */
class VideoUploadManager @Inject constructor(
    private val context: Context,
    private val storageUtil: StorageUtil,
    private val videoUploaderRepository: IVideoUploaderRepository,
    private val fileMetaDataEditor: FileMetaDataEditor,
    private val cameraConfig: CameraConfig,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor,
    private val driverErrorUtils: DriverErrorUtils
) : ServerErrorCallback {

    private val tag = VIDEO_UPLOADER_STATUS
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var totalFilesToUpload = 0
    private var unSyncVideoFiles = listOf<VideoUploaderData>()
    private var currentIndex: Int = 0
    private val uploadVideoJob = SupervisorJob()
    private val uploadVideoScope = CoroutineScope(Dispatchers.IO + uploadVideoJob)

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        run {
            Timber.tag(tag).e(t)
            Firebase.crashlytics.log("Video upload coroutineExceptionHandler")
            Firebase.crashlytics.recordException(t)
            videoUploadingStatus.postValue(FailureState)
        }
    }

    suspend fun checkVideoFilesStatus() = uploadVideoScope.launch(coroutineExceptionHandler) {
        driverErrorUtils.setCallback(this@VideoUploadManager)
        if (videoUploadingStatus.value != ProgressState) {
            val uploadedVideoFiles = videoUploaderRepository.getSyncVideosBatch()
            if (uploadedVideoFiles.isNotEmpty()) {
                updateNotification(context.getString(R.string.upload_syncing_video_files))
                videoUploaderRepository.checkUploadedVideoFilesStatus(
                    VideoFilesStatusRequest(uploadedVideoFiles)
                ).onStart {
                    videoUploadingStatus.postValue(ProgressState)
                }.catch { ex ->
                    videoUploadingStatus.postValue(FailureState)
                    val errorMessage = driverErrorUtils.parseExceptionMessage(ex)
                    if (errorMessage != context.getString(R.string.duplicate_file_error))
                        updateFailedNotification(errorMessage)
                    Timber.tag(tag).e("CheckVideoFilesStatus failure exception $errorMessage")
                    Firebase.crashlytics.log("CheckVideoFilesStatus failure exception $errorMessage")
                    Firebase.crashlytics.recordException(ex)
                }.collect { response ->
                    videoUploadingStatus.postValue(FinishedState)
                    coroutineScope {
                        val goingToDeleteBatch = response.goingToDelete ?: mutableListOf()
                        val syncVideoBatch = response.syncedData ?: mutableListOf()
                        async { deleteSyncVideos(goingToDeleteBatch, syncVideoBatch) }.await()
                        uploadFiles()
                    }
                }
            } else uploadFiles()
        } else {
            Timber.tag(tag).e("Upload already in progress. Skipping.")
            return@launch
        }
    }

    private suspend fun deleteSyncVideos(
        goingToDeleteBatch: MutableList<String>,
        syncVideoBatch: MutableList<String>
    ) = withContext(Dispatchers.IO) {
        try {
            videoUploaderRepository.changeVideoFilesStatus(goingToDeleteBatch, syncVideoBatch)

            // Delete synced videos
            syncVideoBatch.forEach { getActualVideoFile(storageUtil, it)?.delete() }
        } catch (fileException: Exception) {
            Timber.tag(tag).e(fileException)
            Firebase.crashlytics.log("Exception while deleting video file ${fileException.message}")
            Firebase.crashlytics.recordException(fileException)
        }
    }

    private suspend fun uploadFiles() = coroutineScope {
        videoUploadingStatus.postValue(InitialState)
        unSyncVideoFiles = getListOfVideoFiles(videoUploaderRepository.getUnsyncVideosBatch())
        when {
            (!nayanCamModuleInteractor.getRoles().contains(Role.DRIVER)) -> return@coroutineScope
            (unSyncVideoFiles.isEmpty()) -> return@coroutineScope
            else -> {
                Timber.tag(tag).e("uploadFiles.")
                currentIndex = 0 // Start video upload
                totalFilesToUpload = unSyncVideoFiles.size
                videoUploadingStatus.postValue(ProgressState)
                uploadRecursively()
            }
        }
    }

    private suspend fun uploadRecursively() {
        withContext(Dispatchers.IO) {
            try {
                Timber.tag(tag).e("uploadRecursively.")
                if (totalFilesToUpload > currentIndex) {
                    Timber.tag(tag).e("index: $currentIndex / files.size: $totalFilesToUpload")
                    if (canUpload()) {
                        val batteryLevel = context.getBatteryLevel()
                        val isSufficientBattery = if (nayanCamModuleInteractor.isSurveyor()
                            || nayanCamModuleInteractor.getDeviceModel().isKentCam()
                        ) true else (batteryLevel >= 15)
                        when {
                            (isSufficientBattery.not()) -> {
                                updateFailedNotification(context.getString(R.string.low_battery_warning))
                                Firebase.crashlytics.recordException(Exception(context.getString(R.string.low_battery_warning)))
                            }

                            (shouldTrackMobileData && isDataLimitExceedForTheDay()) -> {
                                updateFailedNotification(context.getString(R.string.upload_notification_data_limit_message))
                                Firebase.crashlytics.recordException(Exception(context.getString(R.string.upload_notification_data_limit_message)))
                            }

                            else -> {
                                val videoUploaderData = unSyncVideoFiles[currentIndex]
                                val file = File(videoUploaderData.localVideoFilePath)
                                val map = fileMetaDataEditor.extractMetaDataFromFile(file)
                                val lat = getLatitudeFromFileName(file.name)
                                val lng = getLongitudeFromFileName(file.name)
                                val fileSizeInMB = ((file.length().toDouble() / 1024) / 1024)
                                if (file.exists() && fileSizeInMB > 1f
                                    && lat.isNotEmpty()
                                    && lng.isNotEmpty()
                                ) upload(videoUploaderData, file, lat, lng, map)
                                else changeVideoStatusAndContinue(
                                    NOT_UPLOADED,
                                    0,
                                    videoUploaderData,
                                    true
                                )
                            }
                        }
                    } else {
                        updateFailedNotification(context.getString(R.string.upload_notification_netwrork_error))
                        Timber.tag(tag).e("can not upload")
                        Firebase.crashlytics.recordException(Exception("Unable to upload, Network not connected."))
                    }
                }
            } catch (e: Exception) {
                updateFailedNotification(context.getString(R.string.upload_failed_message))
                Firebase.crashlytics.recordException(e)
            }
        }
    }

    private suspend fun upload(
        videoUploaderData: VideoUploaderData,
        file: File,
        latitude: String,
        longitude: String,
        map: HashMap<String, String>
    ) = withContext(Dispatchers.IO) {
        val progressMessage =
            "${context.getString(R.string.upload_notification_title)} ${currentIndex + 1} / $totalFilesToUpload"
        updateNotification(progressMessage)
        Timber.tag(tag).e(progressMessage)

        val startTime = System.currentTimeMillis()
        try {
            val recordedOn = getUTCTime(map, file.name)
            val surgeId = map["surgeData"] ?: JsonObject().toPrettyJson()
            val key_file = if (cameraConfig.isDubaiPoliceEnabled == true) "file" else "video[link]"
            val latPart = latitude.toRequestBody("multipart/form-data".toMediaTypeOrNull())
            val lngPart = longitude.toRequestBody("multipart/form-data".toMediaTypeOrNull())
            val timePart = recordedOn.toRequestBody("multipart/form-data".toMediaTypeOrNull())
            val reqFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
            val count = (videoUploaderRepository.getOfflineVideosCount() - 1).toString()
            val offlineCount = count.toRequestBody("multipart/form-data".toMediaTypeOrNull())
            val surgePart = surgeId.toRequestBody("multipart/form-data".toMediaTypeOrNull())
            val videoBodyPart =
                MultipartBody.Part.createFormData(key_file, file.name, reqFile)
            val recordedById =
                file.name.split("-")[1].toRequestBody("multipart/form-data".toMediaTypeOrNull())
            val speed = map["speed"]?.toRequestBody("multipart/form-data".toMediaTypeOrNull())

            Timber.e(tag, "Upload started...")
            videoUploaderRepository.uploadVideo(
                latitude = latPart,
                longitude = lngPart,
                recordedOn = timePart,
                offlineVideoCount = offlineCount,
                surge = surgePart,
                recordedById = recordedById,
                speed = speed,
                video = videoBodyPart
            ).catch { ex ->
                videoUploadingStatus.postValue(FailureState)
                val errorMessage = driverErrorUtils.parseExceptionMessage(ex, videoUploaderData)
                if (errorMessage != context.getString(R.string.duplicate_file_error))
                    updateFailedNotification(errorMessage)
                Timber.tag(tag).e("VideoFile ::${file.name} upload failure exception $errorMessage")
                Firebase.crashlytics.log("VideoFile ::${file.name} upload failure exception $errorMessage")
                Firebase.crashlytics.recordException(ex)
            }.collect { response ->
                response?.let {
                    Timber.tag(tag).d("🦀Upload Response: ${response.success}")
                    val endTime = System.currentTimeMillis()
                    Timber.tag(tag).d("Time to upload: ${(endTime - startTime) / 1000}")
                    Firebase.crashlytics.log("Time to upload: ${(endTime - startTime) / 1000}")
                    if (shouldTrackMobileData) {
                        val data = storageUtil.getCurrentDataUsage().data
                        val fileSize = (file.length().toDouble()) / (1024 * 1024)
                        val updatedUsage = data + fileSize
                        storageUtil.setCurrentDataUsage(updatedUsage)
                    }

                    changeVideoStatusAndContinue(UPLOADED, response.videoId, videoUploaderData)
                } ?: run {
                    updateFailedNotification(context.getString(R.string.upload_failed_message))
                }
            }
        } catch (e: Exception) {
            Timber.tag(tag).e(e)
            updateFailedNotification(context.getString(R.string.upload_failed_message))
            Firebase.crashlytics.log("Video upload failure ${file.name}")
            Firebase.crashlytics.log("Video upload failure exception ${e.message}")
            Firebase.crashlytics.recordException(e)
        }

        Timber.tag(tag).d("Uploaded $currentIndex")
    }

    private suspend fun changeVideoStatusAndContinue(
        status: Int,
        videoId: Int = 0,
        videoUploaderData: VideoUploaderData,
        isVideoCorrupted: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            if (isVideoCorrupted)
                videoUploaderRepository.clearSpecifiedVideoData(videoUploaderData.videoName)
            else videoUploaderRepository.updateVideoStatus(
                status,
                videoId,
                videoUploaderData.videoName
            )
            if (unSyncVideoFiles.last().videoName == videoUploaderData.videoName) {
                Timber.tag(tag).e("Upload finished.")
                videoUploaderRepository.getOfflineVideosBatch()
                videoUploadingStatus.postValue(FinishedState)
                notificationManager.cancel(R.id.upload_notification)
            } else {
                currentIndex += 1 // Next file index
                uploadRecursively()
            }
        } catch (exception: Exception) {
            Timber.tag(tag).e(exception)
            Firebase.crashlytics.log("Exception while deleting video file ${exception.message}")
            Firebase.crashlytics.recordException(exception)
        }
    }

    private fun isDataLimitExceedForTheDay(): Boolean {
        if (!storageUtil.sharedPrefManager.isDataUsageLimitEnabled()) return false
        val dataLimitInMB = storageUtil.getDataLimitForTheDay() / 100f *
                SharedPrefManager.DEFAULT_MAX_DATA_USAGE_LIMIT
        val currentUsage = storageUtil.getCurrentDataUsage()
        val currentDate = getCurrentDayOfMonth()
        val usageDate = getDayOfMonth(currentUsage.timestamp)
        Timber.tag(tag)
            .d("isDataLimitExceedForTheDay() currentDate:$currentDate usageDate:$usageDate dataUsed:${currentUsage.data}")
        if (currentUsage.data > (dataLimitInMB) && currentDate == usageDate) {
            updateFailedNotification(context.getString(R.string.data_limit_alert))
            return true
        }
        // resetting the current data usage
        if (currentDate != usageDate) storageUtil.setCurrentDataUsage(0.0)
        return false
    }

    private var shouldTrackMobileData = true

    private fun canUpload(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val preferredNetworkType = storageUtil.sharedPrefManager.getUploadNetworkType()
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return when (preferredNetworkType) {
            WIFI_DATA -> {
                shouldTrackMobileData = false
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }

            MOBILE_DATA -> {
                val connection =
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                shouldTrackMobileData =
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                connection
            }

            else -> false
        }
    }

    private fun updateNotification(progress: String) {
        val title = context.getString(R.string.upload_notification_title)
        notificationManager.notify(R.id.upload_notification, getUploadNotification(title, progress))
    }

    private fun updateFailedNotification(message: String) {
        val title = context.getString(R.string.upload_notification_failed_title)
        notificationManager.notify(R.id.upload_notification, getUploadNotification(title, message))
    }

    fun getUploadNotification(title: String, message: String): Notification {
        val id = context.getString(R.string.upload_notification_channel_id)
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = context.getString(R.string.upload_notification_channel_name)
            val description: String = context.getString(R.string.upload_notification_channel_desc)
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }
        notificationBuilder = NotificationCompat.Builder(context, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(message)
            .setOngoing(false)
            .setSmallIcon(R.drawable.ic_upload)

        return notificationBuilder.build()
    }

    override fun unAuthorisedUserError() {
        uploadVideoScope.launch {
            storageUtil.clearPreferences()
            nayanCamModuleInteractor.clearPreferences()
        }
    }

    override fun duplicateVideoFileError(videoUploaderData: VideoUploaderData?) {
        videoUploaderData?.let {
            uploadVideoScope.launch { changeVideoStatusAndContinue(DUPLICATE, 0, it) }
        }
    }
}