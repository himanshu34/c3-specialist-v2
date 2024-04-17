package com.nayan.nayancamv2.helper

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.SurgeMetaData
import co.nayan.c3v2.core.models.driver_module.VideoUploaderData
import co.nayan.c3v2.core.toPrettyJson
import co.nayan.nayancamv2.R
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nayan.nayancamv2.checkForKmlBoundaries
import com.nayan.nayancamv2.checkForSurgeLocations
import com.nayan.nayancamv2.getCurrentTime
import com.nayan.nayancamv2.helper.GlobalParams.currentTemperature
import com.nayan.nayancamv2.helper.GlobalParams.isNightModeActive
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.model.SensorMeta
import com.nayan.nayancamv2.model.UserLocationMeta
import com.nayan.nayancamv2.model.VideoData
import com.nayan.nayancamv2.nightmode.NightModeConstraintSelector
import com.nayan.nayancamv2.repository.repository_uploader.IVideoUploaderRepository
import com.nayan.nayancamv2.storage.FileMetaDataEditor
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_CORRUPTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_FAILED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_SUCCESSFUL
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan

@Singleton
class MetaDataHelperImpl @Inject constructor(
    private val sharedPrefManager: SharedPrefManager,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor,
    private val nightModeConstraintSelector: NightModeConstraintSelector?,
    private val fileMetaDataEditor: FileMetaDataEditor,
    private val mContext: Context,
    private val videoUploaderRepository: IVideoUploaderRepository
) : IMetaDataHelper {

    private var videoStartAt = 0L
    private var videoEndAt = 0L
    private var locationMetaDataBuffer = ArrayList<UserLocationMeta>()
    private var sensorMetaDataBuffer = ArrayList<SensorMeta>()
    private var locationMetadata = listOf<UserLocationMeta>()
    private var sensorMetaData = listOf<SensorMeta>()
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }
    private val helperScope = CoroutineScope(Dispatchers.IO + coroutineExceptionHandler)
    private val TAG = MetaDataHelperImpl::class.java.simpleName
    private var recordingStartTime = 0L

    override fun setMetaData() = helperScope.launch {
        val startTime = if (recordingStartTime > 0) recordingStartTime - 100 else 0
        val endTime = System.currentTimeMillis()
        locationMetadata =
            ArrayList(locationMetaDataBuffer).filter { it.timeStamp in startTime..endTime }
        sensorMetaData =
            ArrayList(sensorMetaDataBuffer).filter { it.timeStamp in startTime..endTime }
        // Clear Meta Data Buffer list till filtered meta data last element found
        val lastLocationMetaData =
            if (locationMetadata.isEmpty()) null else locationMetadata.last()
        val indexOfLocationLastMatch = locationMetaDataBuffer.indexOf(lastLocationMetaData)
        if (indexOfLocationLastMatch != -1)
            locationMetaDataBuffer.subList(0, indexOfLocationLastMatch).clear()
        val lastSenorMetaData = if (sensorMetaData.isEmpty()) null else sensorMetaData.last()
        sensorMetaDataBuffer.contains(lastSenorMetaData)

        val indexOfSensorLastMatch = sensorMetaDataBuffer.lastIndexOf(lastSenorMetaData)
        if (indexOfSensorLastMatch != -1)
            sensorMetaDataBuffer.subList(0, indexOfSensorLastMatch).clear()
    }

    override fun setRecordingStartTime(recordingStartTime: Long) {
        this.recordingStartTime = recordingStartTime
    }

    override suspend fun saveMetaData(
        currentLocationMeta: UserLocationMeta?,
        currentSensorData: SensorMeta?
    ) = helperScope.launch {
        try {
            currentLocationMeta?.let { locationMetaDataBuffer.add(it) }
            currentSensorData?.let { sensorMetaDataBuffer.add(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun addVideoMetadata(
        file: File, workFlowMetaData: String, videoData: VideoData
    ) = withContext(Dispatchers.IO) {
        Timber.tag(TAG).d("addVideoMetadata called")
        val currentMilliseconds = System.currentTimeMillis()
        val currentTime: Pair<String, String> = getCurrentTime(currentMilliseconds)
        val metaData = HashMap<String, String>()
        metaData["country"] = videoData.userLocation?.countryCode.toString()
        metaData["speed"] = "${videoData.userLocation?.speedKpH} KpH"
        metaData["lat"] = videoData.userLocation?.latitude.toString()
        metaData["lng"] = videoData.userLocation?.longitude.toString()
        metaData["altitude"] = videoData.userLocation?.altitude.toString()
        metaData["address"] = videoData.userLocation?.address.toString()
        metaData["recordingSessionId"] = nayanCamModuleInteractor.getId().toString()
        metaData["localTime"] = currentTime.first
        metaData["recordedOn"] = currentMilliseconds.toString()
        metaData["recordedOnUTC"] = currentTime.second
        val latLng = LatLng(
            videoData.userLocation?.latitude ?: 0.0,
            videoData.userLocation?.longitude ?: 0.0
        )
        val surgeId = checkForSurgeLocations(latLng, nayanCamModuleInteractor.getSurgeLocations())
            ?: checkForKmlBoundaries(latLng, nayanCamModuleInteractor.getCityKmlBoundaries())
        metaData["surgeData"] = if (surgeId.isNullOrEmpty().not())
            SurgeMetaData(mutableListOf(surgeId.toString())).toPrettyJson()
        else JsonObject().toPrettyJson()

        metaData["workFlowMetadataList"] = workFlowMetaData
        metaData["videoStartAt"] = videoStartAt.toString()
        metaData["videoEndAt"] = videoEndAt.toString()
        metaData["recordedAtTemperature"] = currentTemperature.toString()
        metaData["previewHeight"] = videoData.previewSize.height.toString()
        metaData["previewWidth"] = videoData.previewSize.width.toString()
        metaData["appVersion"] = sharedPrefManager.getCurrentVersion()
        metaData["isNightModeSettingsEnabled"] =
            nightModeConstraintSelector?.isFeatureEnabled().toString()
        metaData["isNightModeEnabled"] = isNightModeActive.toString()
        metaData["locationHistory"] = locationMetadata.toPrettyJson()
        metaData["sensorData"] = sensorMetaData.toPrettyJson()
        try {
            if (currentTemperature <= (nayanCamModuleInteractor.getOverheatingRestartTemperature()))
                addExtraMetaData(metaData, videoData)

            fileMetaDataEditor.addMetaDataToFile(file, metaData)
            // Add file details to video uploader database
            videoUploaderRepository.addToDatabase(
                VideoUploaderData(
                    videoName = file.name,
                    localVideoFilePath = file.path,
                    createdAtTimestamp = currentMilliseconds
                )
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e("Exception ${e.message}")
            Firebase.crashlytics.recordException(e)
            Timber.tag(TAG).e(e, "Exception!")
        }
        Timber.tag(TAG).d("addVideoMetadata end")
    }

    private suspend fun addExtraMetaData(
        metaData: HashMap<String, String>, videoData: VideoData
    ) = withContext(Dispatchers.IO) {
        Timber.tag(TAG).d("addExtraMetaData called")
        try {
            sharedPrefManager.getCameraParams().let { params ->
                if (params.isNotEmpty() || params.isNotBlank()) JsonParser().parse(params).asJsonObject
                else JsonObject()
            }?.let {
                metaData["LENS_INFO_AVAILABLE_APERTURES"] =
                    Gson().toJson(it.get("LENS_INFO_AVAILABLE_APERTURES"))
                metaData["LENS_INFO_AVAILABLE_FILTER_DENSITIES"] =
                    Gson().toJson(it.get("LENS_INFO_AVAILABLE_FILTER_DENSITIES"))
                metaData["LENS_INFO_AVAILABLE_FOCAL_LENGTHS"] =
                    Gson().toJson(it.get("LENS_INFO_AVAILABLE_FOCAL_LENGTHS"))
                metaData["LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION"] =
                    Gson().toJson(it.get("LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION"))
                metaData["LENS_INFO_HYPERFOCAL_DISTANCE"] =
                    Gson().toJson(it.get("LENS_INFO_HYPERFOCAL_DISTANCE"))
                metaData["LENS_INFO_MINIMUM_FOCUS_DISTANCE"] =
                    Gson().toJson(it.get("LENS_INFO_MINIMUM_FOCUS_DISTANCE"))
                metaData["LENS_INFO_FOCUS_DISTANCE_CALIBRATION"] =
                    Gson().toJson(it.get("LENS_INFO_FOCUS_DISTANCE_CALIBRATION"))
                metaData["LENS_FACING"] = Gson().toJson(it.get("LENS_FACING"))
                metaData["LENS_POSE_ROTATION"] = Gson().toJson(it.get("LENS_POSE_ROTATION"))
                metaData["LENS_POSE_TRANSLATION"] = Gson().toJson(it.get("LENS_POSE_TRANSLATION"))
                metaData["LENS_INTRINSIC_CALIBRATION"] =
                    Gson().toJson(it.get("LENS_INTRINSIC_CALIBRATION"))
                metaData["LENS_RADIAL_DISTORTION"] = Gson().toJson(it.get("LENS_RADIAL_DISTORTION"))
                metaData["LENS_POSE_REFERENCE"] = Gson().toJson(it.get("LENS_POSE_REFERENCE"))
                metaData["LENS_DISTORTION"] = Gson().toJson(it.get("LENS_DISTORTION"))

                metaData["sensorWidth"] = videoData.sensorSize?.width.toString()
                metaData["sensorHeight"] = videoData.sensorSize?.height.toString()

                val horizontalAngle = ((2f * atan(
                    (((videoData.sensorSize?.width ?: 0.0F) / (videoData.focalLength
                        ?: (0.0F * 2f)))).toDouble()
                )) * 180.0) / Math.PI
                val verticalAngle = ((2f * atan(
                    (((videoData.sensorSize?.height ?: 0.0F) / (videoData.focalLength
                        ?: (0.0F * 2f)))).toDouble()
                )) * 180.0) / Math.PI

                metaData["horizontalAngle"] = horizontalAngle.toString()
                metaData["verticalAngle"] = verticalAngle.toString()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Exception ${e.message}")
            Firebase.crashlytics.recordException(e)
        }
    }

    override suspend fun changeRecordingStatus(
        status: Int, file: File, workFlowMetaData: String, videoData: VideoData
    ): RecordingState = withContext(Dispatchers.IO) {
        try {
            if (status == 0) {
                val fileSizeInMB = ((file.length().toDouble()) / 1024) / 1024
                if (fileSizeInMB >= 2) {
                    Firebase.crashlytics.log("::fun fileSaveComplete fileSize - $fileSizeInMB MB")
                    async { addVideoMetadata(file, workFlowMetaData, videoData) }.await()
                    RecordingState(
                        RECORDING_SUCCESSFUL,
                        retrieveVideoData(fileSizeInMB, file)
                    )
                } else {
                    file.delete()
                    Firebase.crashlytics.log("Corrupted file created Exception! - $fileSizeInMB MB")
                    RecordingState(
                        RECORDING_CORRUPTED,
                        mContext.getString(R.string.recording_corrupted)
                    )
                }
            } else {
                Timber.e("${mContext.getString(R.string.recording_failed)} Exception!")
                Firebase.crashlytics.recordException(Exception("${mContext.getString(R.string.recording_failed)} Exception!"))
                RecordingState(RECORDING_FAILED, mContext.getString(R.string.recording_failed))
            }
        } catch (ex: Exception) {
            Timber.e(ex, "${mContext.getString(R.string.recording_failed)} Exception!")
            Firebase.crashlytics.recordException(ex)
            RecordingState(RECORDING_FAILED, mContext.getString(R.string.recording_failed))
        }
    }

    private suspend fun retrieveVideoData(
        fileSizeInMB: Double, file: File
    ): String = withContext(Dispatchers.IO) {
        val message: StringBuilder = StringBuilder()
        videoStartAt = 0L
        videoEndAt = System.currentTimeMillis()
        try {
            message.append("F: ${roundOff(fileSizeInMB)}MB")
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(mContext, Uri.fromFile(file))
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                    ?.let {
                        videoStartAt = videoEndAt - it
                        message.append(", D: ${TimeUnit.MILLISECONDS.toSeconds(it)}Sec")
                    } ?: run {
                    videoStartAt = if (fileSizeInMB > 4) videoEndAt - TimeUnit.SECONDS.toMillis(10)
                    else videoEndAt - TimeUnit.SECONDS.toMillis(1)
                }
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                Timber.tag(TAG).e(e, "Exception!")
                videoStartAt = if (fileSizeInMB > 4) videoEndAt - TimeUnit.SECONDS.toMillis(10)
                else videoEndAt - TimeUnit.SECONDS.toMillis(1)
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            Timber.tag(TAG).e(e, "Exception!")
            message.append(mContext.getString(R.string.event_recorded))
        }

        return@withContext message.toString()
    }

    private fun roundOff(number: Double): String {
        val df = DecimalFormat("#.##")
        df.roundingMode = RoundingMode.CEILING
        return df.format(number)
    }
}