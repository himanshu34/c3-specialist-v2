package com.nayan.nayancamv2.ui.cam

import android.app.Activity
import android.content.Context
import android.location.Location
import android.media.ImageReader
import androidx.lifecycle.*
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.CameraConfig
import co.nayan.c3v2.core.models.driver_module.LocationData
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nayan.nayancamv2.helper.BaseCameraPreviewListener
import com.nayan.nayancamv2.helper.CameraHelper
import com.nayan.nayancamv2.helper.GlobalParams.exceptionHandler
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.helper.GlobalParams.isRecordingVideo
import com.nayan.nayancamv2.helper.GlobalParams.shouldStartRecordingOnceBufferIsFilled
import com.nayan.nayancamv2.helper.IRecordingHelper
import com.nayan.nayancamv2.helper.OpticalFlowPyrLK
import com.nayan.nayancamv2.isValidLatLng
import com.nayan.nayancamv2.model.RecordingData
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.repository.repository_cam.INayanCamRepository
import com.nayan.nayancamv2.repository.repository_graphopper.IGraphHopperRepository
import com.nayan.nayancamv2.repository.repository_location.ILocationRepository
import com.nayan.nayancamv2.repository.repository_uploader.IVideoUploaderRepository
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.ui.views.AutoFitTextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel to handle all camera related operations
 */
class NayanCamViewModel @Inject constructor(
    private val cameraConfig: CameraConfig,
    private val cameraHelper: CameraHelper,
    private val storageUtil: StorageUtil,
    private val nayanCamRepository: INayanCamRepository,
    private val locationRepository: ILocationRepository,
    private val graphhopperRepository: IGraphHopperRepository,
    private val videoUploaderRepository: IVideoUploaderRepository,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor,
    private val iRecordingHelper: IRecordingHelper
) : ViewModel(), LifecycleObserver {

    private var textureView: AutoFitTextureView? = null
    private var baseCameraPreviewListener: BaseCameraPreviewListener? = null
    private var imageAvailableListener: ImageReader.OnImageAvailableListener? = null
    val recordingState: StateFlow<RecordingState?> = cameraHelper.recordingState

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onStart() {
        Timber.d("NayanCamViewModel:onStart()")
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            if (isInitialized()) {
                cameraHelper.setupNayanCamera(
                    textureView = textureView,
                    baseCameraPreviewListener = baseCameraPreviewListener,
                    imageAvailableListener = imageAvailableListener,
                )
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onStop() {
        Timber.d("NayanCamViewModel:onStop()")
        viewModelScope.launch(Dispatchers.IO) {
            videoUploaderRepository.getOfflineVideosBatch()
            cameraHelper.closeCamera()
        }
    }

    /***
     * This function provide texture view to Nayan camera implementation
     * to setup camera preview.
     * This function must be called in on create method of fragment and activity
     */
    fun attachCameraPreview(
        cameraPreview: AutoFitTextureView,
        baseCameraPreviewListener: BaseCameraPreviewListener? = null,
        imageAvailableListener: ImageReader.OnImageAvailableListener? = null
    ) {
        this.textureView = cameraPreview
        this.baseCameraPreviewListener = baseCameraPreviewListener
        this.imageAvailableListener = imageAvailableListener
    }

    fun initOpenCvForOpticalFlow(
        context: Context
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        OpticalFlowPyrLK.initOpenCV(context)
    }

    fun recordVideo(
        userLocation: UserLocation? = null,
        context: Context,
        modelName: String = "",
        labelName: String = "",
        confidence: String = "",
        isManual: Boolean = false,
        recordedWorkFlowMetaData: String = "",
        onLocationError: () -> Unit
    ) = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        baseCameraPreviewListener?.setIsManualRecording(isManual)
        val currentUserLocation = userLocation ?: UserLocation()
        val isTapped = labelName.contains("Tap")
        val deviceModel = nayanCamModuleInteractor.getDeviceModel()
        when {
            (isInCorrectScreenOrientation.not()) -> return@launch

            ((!storageUtil.isMemoryAvailableForRecording(deviceModel))) -> {
                Timber.e("🦀recordVideo() : Memory Not available for recording")
                if (!deviceModel.isKentCam()) {
                    nayanCamModuleInteractor.startSettingsActivity(context, isStorageFull = true)
                    if (context is Activity) context.finish()
                }
                return@launch
            }

            (nayanCamModuleInteractor.isSurveyor() && isRecordingVideo) -> return@launch

            (!shouldStartRecordingOnceBufferIsFilled && isRecordingVideo) -> {
                shouldStartRecordingOnceBufferIsFilled = true
                storageUtil.createNewVideoFile(
                    location = currentUserLocation,
                    isManual = isManual,
                    isManualTap = isTapped
                )?.also { file ->
                    val recordingStartTime = System.currentTimeMillis()
                    cameraHelper.setDelayVideoRecordingData(
                        RecordingData(
                            file,
                            isManual,
                            labelName,
                            modelName,
                            recordingStartTime,
                            recordedWorkFlowMetaData
                        )
                    )
                }
                return@launch
            }
        }

        if (!isValidLatLng(currentUserLocation.latitude, currentUserLocation.longitude))
            onLocationError.invoke()
        else {
            storageUtil.createNewVideoFile(
                location = currentUserLocation,
                isManual = isManual,
                isManualTap = isTapped
            )?.also { file ->
                val videoData = iRecordingHelper.recordVideo(
                    file,
                    currentUserLocation,
                    modelName,
                    labelName,
                    confidence,
                    isManual,
                    recordedWorkFlowMetaData,
                )
                videoData?.let { cameraHelper.saveVideo(it) }
            }
        }
    }

    private fun isInitialized() =
        (textureView != null && baseCameraPreviewListener != null && imageAvailableListener != null)

    fun getCameraConfig(): CameraConfig = cameraConfig

    fun getStorageUtil(): StorageUtil = storageUtil

    fun setConfig() = viewModelScope.launch(exceptionHandler) {
        nayanCamModuleInteractor.getId().let { userID ->
            storageUtil.sharedPrefManager.getCameraParams().let { params ->
                if (params.isNotEmpty() || params.isNotBlank()) {
                    val jsonObject: JsonObject = JsonParser().parse(params).asJsonObject
                    nayanCamRepository.setConfig(userID.toString(), jsonObject)
                }
            }
        }
    }

    fun onAIScanning() = cameraHelper.onAIScanning()

    fun onWithInSurgeLocationStatus() = cameraHelper.onWithInSurgeLocationStatus()

    fun getSensorLiveData(context: Context) = nayanCamRepository.getSensorLiveData(context)

    // region temperature
    fun startTempObserving() = nayanCamRepository.startTemperatureUpdate()
    fun stopTempObserving() = nayanCamRepository.stopTemperatureUpdate()
    fun getTemperatureLiveData() = nayanCamRepository.getTemperatureLiveData()

    fun syncOfflineCount() = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
        val videoCount = videoUploaderRepository.getOfflineVideosCount()
        videoUploaderRepository.updateVideoCount(videoCount.toString())
    }

    suspend fun getAllSegments(): MutableList<SegmentTrackData> {
        return graphhopperRepository.getAllSegments()
    }

    fun addLocationToDatabase(
        location: Location,
        callback: ((Long) -> Unit)? = null
    ) = viewModelScope.launch {
        locationRepository.addLocationToDatabase(
            LocationData(
                location.latitude,
                location.longitude,
                System.currentTimeMillis(),
                location.accuracy.toDouble()
            )
        )

        val allLocationHistory = locationRepository.getCompleteLocationHistory()
        if (allLocationHistory.isNotEmpty() && allLocationHistory.size >= 5)
            callback?.invoke(locationRepository.getLastLocationSyncTimeStamp())
    }

    fun resetNightMode() = viewModelScope.launch {
        cameraHelper.resetNightMode()
    }

    fun updateCameraISOExposure() = viewModelScope.launch {
        cameraHelper.updateISOExposureBuffer()
    }

    // Location State Observers
    val locationState: LiveData<ActivityState> = nayanCamRepository.getLocationState()

    fun checkForLocationRequest() {
        nayanCamRepository.checkForLocationRequest()
    }

    fun startReceivingLocationUpdates() {
        Timber.d("start receiving location update")
        nayanCamRepository.startLocationUpdate()
    }

    fun stopReceivingLocationUpdates() {
        nayanCamRepository.stopLocationUpdate()
        Timber.d("stop receiving location update")
    }
}