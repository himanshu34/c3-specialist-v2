package com.nayan.nayancamv2.hovermode

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.location.Location
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.di.NayanCamModuleDependencies
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.CameraConfig
import co.nayan.c3v2.core.models.LocationFailureState
import co.nayan.c3v2.core.models.LocationSuccessState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.driver_module.LocationData
import co.nayan.c3v2.core.utils.Constants.LocationService.FETCHING_LOCATION_COMPLETE
import co.nayan.c3v2.core.utils.Constants.LocationService.FETCHING_LOCATION_ERROR
import co.nayan.c3v2.core.utils.Constants.LocationService.FETCHING_LOCATION_STARTED
import co.nayan.c3v2.core.utils.Constants.LocationService.LOCATION_UNAVAILABLE
import co.nayan.c3v2.core.utils.LocaleHelper
import co.nayan.nayancamv2.R
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.BaseHoverService
import com.nayan.nayancamv2.ai.AIModelManager
import com.nayan.nayancamv2.ai.CameraProcessor
import com.nayan.nayancamv2.ai.ObjectOfInterestListener
import com.nayan.nayancamv2.di.DaggerNayanCamComponent
import com.nayan.nayancamv2.funIfPointFallsInKmlBoundaries
import com.nayan.nayancamv2.funIfUserLocationFallsWithInSurge
import com.nayan.nayancamv2.funIfUserRecordingOnBlackLines
import com.nayan.nayancamv2.getDefaultUserLocation
import com.nayan.nayancamv2.helper.BaseCameraPreviewListener
import com.nayan.nayancamv2.helper.CameraHelper
import com.nayan.nayancamv2.helper.CameraPreviewListener
import com.nayan.nayancamv2.helper.GlobalParams.SPATIAL_PROXIMITY_THRESHOLD
import com.nayan.nayancamv2.helper.GlobalParams.SPATIAL_STICKINESS_CONSTANT
import com.nayan.nayancamv2.helper.GlobalParams.appHasLocationUpdates
import com.nayan.nayancamv2.helper.GlobalParams.exceptionHandler
import com.nayan.nayancamv2.helper.GlobalParams.hasValidSpeed
import com.nayan.nayancamv2.helper.GlobalParams.ifUserLocationFallsWithInSurge
import com.nayan.nayancamv2.helper.GlobalParams.ifUserRecordingOnBlackLines
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.helper.GlobalParams.isRecordingVideo
import com.nayan.nayancamv2.helper.GlobalParams.shouldStartRecordingOnceBufferIsFilled
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.helper.OpticalFlowPyrLK
import com.nayan.nayancamv2.impl.SyncWorkflowManagerImpl
import com.nayan.nayancamv2.isValidLatLng
import com.nayan.nayancamv2.model.RecordingData
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.model.UserLocationMeta
import com.nayan.nayancamv2.prepareSpatialTreeForAllSegments
import com.nayan.nayancamv2.repository.repository_graphopper.IGraphHopperRepository
import com.nayan.nayancamv2.repository.repository_location.ILocationRepository
import com.nayan.nayancamv2.repository.repository_notification.INotificationHelper
import com.nayan.nayancamv2.repository.repository_uploader.IVideoUploaderRepository
import com.nayan.nayancamv2.startAttendanceSyncingRequest
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.util.Constants.ACTION_EXIT
import com.nayan.nayancamv2.util.Constants.ACTION_OPEN_DASHBOARD
import com.nayan.nayancamv2.util.Constants.ACTION_OPEN_RECORDER
import com.nayan.nayancamv2.util.Constants.ACTION_OPTIMIZE_FRAMES
import com.nayan.nayancamv2.util.Constants.ACTION_STOP_CAMERA_SERVICE
import com.nayan.nayancamv2.util.Constants.RADIAN_TO_METER_DIVISOR_EARTH
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_10
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.SURVEYOR_SPEED_MAX_THRESHOLD
import com.nayan.nayancamv2.util.RecordingEventState.DRIVING_FAST
import com.nayan.nayancamv2.util.RecordingEventState.NOT_IN_SURGE
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_CORRUPTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_FAILED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_STARTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_SUCCESSFUL
import com.nayan.nayancamv2.util.VolumeChangeManager
import com.nayan.nayancamv2.videouploder.AttendanceSyncManager
import com.nayan.nayancamv2.videouploder.GraphHopperSyncManager
import com.vividsolutions.jts.index.SpatialIndex
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

abstract class HoverService : BaseHoverService() {

    @Inject
    lateinit var cameraConfig: CameraConfig

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    lateinit var cameraHelper: CameraHelper

    @Inject
    lateinit var locationRepository: ILocationRepository

    @Inject
    lateinit var graphhopperRepository: IGraphHopperRepository

    @Inject
    lateinit var videoUploaderRepository: IVideoUploaderRepository

    @Inject
    lateinit var aiModelManager: AIModelManager

    @Inject
    lateinit var imageProcessor: CameraProcessor

    @Inject
    lateinit var cameraPreviewListener: CameraPreviewListener

    @Inject
    lateinit var graphHopperSyncManager: GraphHopperSyncManager

    @Inject
    lateinit var attendanceSyncManager: AttendanceSyncManager

    @Inject
    lateinit var notificationHelper: INotificationHelper

    @Inject
    lateinit var syncWorkflowManagerImpl: SyncWorkflowManagerImpl

    var mHandler: Handler? = null
    private lateinit var spatialIndex: SpatialIndex
    private val locationRequestDelay by lazy { TimeUnit.SECONDS.toMillis(5) }
    private val locationRequestHandler by lazy { Handler(Looper.getMainLooper()) }
    private val locationRequestRunnable = Runnable {
        nayanCamRepository.checkForLocationRequest()
    }

    private val hoverJob = SupervisorJob()
    private val hoverScope = CoroutineScope(Dispatchers.IO + hoverJob + exceptionHandler)
    private var activityState: ActivityState? = null
    private var syncActivityState: ActivityState? = null
    private var mAllowedSurveys = 1

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("*********************[Hover Service Starting....]**********************\n")
        hoverScope.launch { OpticalFlowPyrLK.initOpenCV(this@HoverService) }

        this.applicationContext.contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI, true,
            mVolumeObserver
        )

        DaggerNayanCamComponent.builder()
            .context(this)
            .appDependencies(
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    NayanCamModuleDependencies::class.java
                )
            ).build().inject(this)

        if (mNayanCamModuleInteractor.getSpatialProximityThreshold() > 0.0) SPATIAL_PROXIMITY_THRESHOLD =
            mNayanCamModuleInteractor.getSpatialProximityThreshold() / RADIAN_TO_METER_DIVISOR_EARTH.toDouble()
        if (mNayanCamModuleInteractor.getSpatialStickinessConstant() > 0.0) SPATIAL_STICKINESS_CONSTANT =
            mNayanCamModuleInteractor.getSpatialStickinessConstant() / RADIAN_TO_METER_DIVISOR_EARTH.toDouble()
        mAllowedSurveys = mNayanCamModuleInteractor.getAllowedSurveys()
        VolumeChangeManager.setUpVolume(getSystemService(AUDIO_SERVICE) as AudioManager)
        locationRequestHandler.post(locationRequestRunnable)
        imageProcessor.initClassifiers(this)
        imageProcessor.mObjectOfInterestListener = objectListener
        cameraPreviewListener.scheduleSampling()
        cameraPreviewListener.onOpticalFlowMotionDetected =
            object : BaseCameraPreviewListener.OnOpticalFlowMotionDetected {
                override fun onMotionlessContentDetected() {
                    onMotionlessContentDetectedStatus()
                }

                override fun onMotionContentDetected() {
                    onMotionContentDetectedStatus()
                }
            }
        setUpObservers()
        syncOfflineCount()
    }

    private fun setUpObservers() = lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            spatialIndex = prepareSpatialTreeForAllSegments(storageUtil.getAllSegments())
        }

        attendanceSyncManager.locationSyncLiveData.observe(this@HoverService) { activityState = it }

        syncWorkflowManagerImpl.subscribeSegmentsData().observe(this@HoverService) {
            when (it) {
                is SyncWorkflowManagerImpl.SuccessSegmentState -> {
                    lifecycleScope.launch(Dispatchers.Default) {
                        spatialIndex = prepareSpatialTreeForAllSegments(it.segmentsList)
                    }
                }
            }
        }

        syncWorkflowManagerImpl.subscribe().observe(this@HoverService) {
            syncActivityState = it
            when (it) {
                is SyncWorkflowManagerImpl.SyncWorkflowSuccessState -> {
                    lifecycleScope.launch(Dispatchers.Default) {
                        imageProcessor.updateWorkFlowList(it.workFlowList)
                    }
                }
            }
        }

        nayanCamRepository.getSensorLiveData(applicationContext).observe(this@HoverService) {
            it.timeStamp = System.currentTimeMillis()
            cameraHelper.currentSensorMeta = it
        }

        nayanCamRepository.getLocationState().observe(this@HoverService, locationUpdateObserver)
        cameraHelper.recordingState.collect(recordingStateObserver)
    }

    abstract fun restartService()

    private val recordingStateObserver = FlowCollector<RecordingState?> { recordingState ->
        recordingState?.let {
            when (it.recordingState) {
                RECORDING_STARTED -> showRecordingAlert(
                    it.recordingState,
                    R.raw.recording,
                    it.message
                )

                RECORDING_SUCCESSFUL -> showRecordingAlert(
                    it.recordingState,
                    R.raw.recorded,
                    it.message
                )

                RECORDING_CORRUPTED -> showRecordingAlert(
                    it.recordingState,
                    R.raw.camera_error_alert,
                    it.message
                )

                RECORDING_FAILED -> showRecordingAlert(
                    it.recordingState,
                    R.raw.camera_error_alert,
                    it.message
                )

                DRIVING_FAST -> showRecordingAlert(
                    it.recordingState,
                    R.raw.speed_alert,
                    it.message
                )

                NOT_IN_SURGE -> showRecordingAlert(
                    it.recordingState,
                    0,
                    it.message
                )

                else -> Timber.e("RecordingState -> ${it.recordingState}")
            }
        }
    }

    private val locationUpdateObserver = Observer<ActivityState> {
        when (it) {
            ProgressState -> onLocationUpdateStatus(FETCHING_LOCATION_STARTED)
            is LocationSuccessState -> {
                it.location?.let { loc ->
                    if (isInCorrectScreenOrientation) {
                        hoverScope.launch {
                            Timber.tag("HoverService").e("Location Received with ${loc.time}")
                            appHasLocationUpdates = true
                            onLocationUpdateStatus(FETCHING_LOCATION_COMPLETE)
                            mNayanCamModuleInteractor.saveLastLocation(loc.latitude, loc.longitude)
                            userLocation = getDefaultUserLocation(loc)
                            checkSurveyorWarningStatus(loc)
                            startSyncWorkflow()
                        }
                    }
                }
            }

            is LocationFailureState -> {
                it.exception?.let {
                    onLocationUpdateStatus(FETCHING_LOCATION_ERROR)
                } ?: run { onLocationUpdateStatus(LOCATION_UNAVAILABLE) }
                locationRequestHandler.postDelayed(locationRequestRunnable, locationRequestDelay)
            }
        }
    }

    private fun checkSurveyorWarningStatus(location: Location) = lifecycleScope.launch {
        ifUserLocationFallsWithInSurge = if (mNayanCamModuleInteractor.isSurveyor()) {
            val surgeLocations = mNayanCamModuleInteractor.getSurgeLocations()
            val cityKmlBoundaries = mNayanCamModuleInteractor.getCityKmlBoundaries()
            val point = LatLng(location.latitude, location.longitude)
            (funIfPointFallsInKmlBoundaries(point, cityKmlBoundaries) ||
                    funIfUserLocationFallsWithInSurge(point, surgeLocations))
        } else true

        hasValidSpeed =
            if (mNayanCamModuleInteractor.isSurveyor() && ifUserLocationFallsWithInSurge)
                location.speed <= SURVEYOR_SPEED_MAX_THRESHOLD
            else true

        onSurveyorWarningStatus(ifUserLocationFallsWithInSurge, hasValidSpeed)

        ifUserRecordingOnBlackLines = if (mNayanCamModuleInteractor.isSurveyor() &&
            mNayanCamModuleInteractor.getRecordingOnBlackLines().not()
        ) {
            async {
                funIfUserRecordingOnBlackLines(
                    location,
                    spatialIndex,
                    SPATIAL_PROXIMITY_THRESHOLD,
                    SPATIAL_STICKINESS_CONSTANT,
                    mAllowedSurveys
                )
            }.await()
        } else false

        onDrivingOnBlackSegmentsStatus(ifUserRecordingOnBlackLines)

        val hasValidConditionsForSurveyor =
            (mNayanCamModuleInteractor.isSurveyor() && ifUserLocationFallsWithInSurge)
        if (ifUserRecordingOnBlackLines.not() && hasValidSpeed
            && (hasValidConditionsForSurveyor || mNayanCamModuleInteractor.isAIMode())
        ) {
            cameraHelper.currentLocationMeta = UserLocationMeta(
                location.latitude,
                location.longitude,
                location.altitude,
                location.speed.toString(),
                location.bearing.toString(),
                System.currentTimeMillis()
            )

            addLocationToDatabase(location) { lastSyncTimeStamp ->
                val diff = System.currentTimeMillis() - lastSyncTimeStamp
                if ((activityState != ProgressState) && diff >= DELAYED_10) {
                    attendanceSyncManager.locationSyncLiveData.postValue(ProgressState)
                    startAttendanceSyncingRequest(false)
                }
            }
        }
    }

    private fun addLocationToDatabase(
        location: Location,
        callback: ((Long) -> Unit)? = null
    ) = lifecycleScope.launch {
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

    private fun recordVideo(
        modelName: String = "",
        labelName: String = "",
        confidence: String = "",
        isManual: Boolean = false,
        recordedWorkFlowMetaData: String = ""
    ) = hoverScope.launch {
        val isTapped = labelName.contains("Tap")
        val deviceModel = mNayanCamModuleInteractor.getDeviceModel()
        when {
            (!storageUtil.isMemoryAvailableForRecording(deviceModel)) -> {
                Timber.d("🦀recordVideo() : Memory Not available for recording")
                Firebase.crashlytics.recordException(Exception("Storage running out of space."))
                if (!deviceModel.isKentCam()) {
                    mNayanCamModuleInteractor.startSettingsActivity(
                        this@HoverService,
                        isStorageFull = true
                    )
                    stopSelf()
                } else restartService()
                return@launch
            }

            (mNayanCamModuleInteractor.isSurveyor() && isRecordingVideo) -> return@launch

            (isManual.not() && isRecordingVideo) -> {
                // Start recording once buffer is filled
                shouldStartRecordingOnceBufferIsFilled = true
                storageUtil.createNewVideoFile(
                    location = userLocation ?: UserLocation(),
                    isManual = isManual,
                    isManualTap = isTapped
                )?.also { file ->
                    cameraHelper.setDelayVideoRecordingData(
                        RecordingData(
                            file,
                            isManual,
                            labelName,
                            modelName,
                            System.currentTimeMillis(),
                            recordedWorkFlowMetaData
                        )
                    )
                }
                return@launch
            }
        }

        userLocation?.let { locationData ->
            when {
                (!isValidLatLng(locationData.latitude, locationData.longitude)) -> {
                    Timber.e("######## UserLocation is null ###############")
                    Timber.e("######## Error due to latitude ${locationData.latitude} longitude ${locationData.longitude} ###############")
                    Timber.e("######## Location error ###############")
                    onLocationUpdateStatus(FETCHING_LOCATION_ERROR, true)
                }

                else -> {
                    cameraPreviewListener.setIsManualRecording(isManual)
                    storageUtil.createNewVideoFile(
                        location = locationData,
                        isManual = isManual,
                        isManualTap = isTapped
                    )?.also { file ->
                        val videoData = iRecordingHelper.recordVideo(
                            file,
                            locationData,
                            modelName,
                            labelName,
                            confidence,
                            isManual,
                            recordedWorkFlowMetaData
                        )
                        videoData?.let { cameraHelper.saveVideo(it) }
                    }
                }
            }
        } ?: run {
            Timber.e("######## ${getString(R.string.location_error)} ###############")
            Timber.e("######## start recording ###############")
            Timber.e("######## location error ###############")
            onLocationUpdateStatus(FETCHING_LOCATION_ERROR, true)
        }
    }

    private val objectListener = object : ObjectOfInterestListener {

        override fun onStartRecording(
            modelName: String,
            labelName: String,
            confidence: String,
            recordedWorkFlowMetaData: String
        ) {
            if (appHasLocationUpdates && isInCorrectScreenOrientation)
                recordVideo(
                    modelName,
                    labelName,
                    confidence,
                    recordedWorkFlowMetaData = recordedWorkFlowMetaData
                )
        }

        override fun onRunningOutOfRAM(availMem: Float) {
            onRunningOutOfRAMStatus(availMem)
        }

        override fun isWorkflowAvailable(status: Boolean) {
            isWorkflowAvailableStatus(status)
        }

        override fun onAIScanning() {
            onAIScanningStatus()
        }

        override fun updateCameraISOExposure() {
            cameraHelper.updateISOExposureBuffer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReceivingLocationUpdates()
        locationRequestHandler.removeCallbacksAndMessages(null)
        hoverScope.launch {
            videoUploaderRepository.getOfflineVideosBatch()
            cameraHelper.closeCamera()
        }
        this.applicationContext.contentResolver.unregisterContentObserver(mVolumeObserver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        handleActions(intent)
        return START_STICKY
    }

    private fun handleActions(intent: Intent?) {
        if (intent != null && intent.action != null) {
            when (intent.action) {
                ACTION_OPEN_DASHBOARD -> openDashboard()
                ACTION_EXIT -> exit("Hover service closed manually by dragging exit")
                ACTION_OPEN_RECORDER -> openRecorder()
                ACTION_OPTIMIZE_FRAMES -> optimizeFrames()
                ACTION_STOP_CAMERA_SERVICE -> updateHoverViewForError()
                else -> startCamera()
            }
        } else startCamera()
    }

    private fun startCamera() = hoverScope.launch {
        Timber.e("hover--> startCamera")
        cameraHelper.setupNayanCamera(
            baseCameraPreviewListener = cameraPreviewListener,
            imageAvailableListener = cameraPreviewListener
        )
    }

    abstract fun showRecordingAlert(
        recordingState: Int,
        resource: Int,
        message: String = ""
    )

    private suspend fun startSyncWorkflow() = withContext(Dispatchers.IO) {
        userLocation?.let {
            Timber.d("location: ${it.latitude} -- ${it.longitude}, ${it.address}")
            val latLng = LatLng(it.latitude, it.longitude)
            if (syncActivityState != ProgressState) syncWorkflowManagerImpl.onLocationUpdate(latLng)
        }
    }

    private fun stopReceivingLocationUpdates() {
        nayanCamRepository.stopLocationUpdate()
        appHasLocationUpdates = false
    }

    abstract fun openDashboard()
    abstract fun optimizeFrames()
    abstract fun exit(message: String)
    abstract fun updateHoverViewForError()
    abstract fun openRecorder()
    abstract fun onRunningOutOfRAMStatus(availMem: Float)
    abstract fun isWorkflowAvailableStatus(status: Boolean)
    abstract fun onAIScanningStatus()

    abstract fun onLocationUpdateStatus(locationStatus: Int, isRecordingVideo: Boolean = false)
    abstract fun onMotionlessContentDetectedStatus()
    abstract fun onMotionContentDetectedStatus()
    abstract fun onSensorReCalibrationStatus(currentThresholds: Float)
    abstract fun onDrivingOnBlackSegmentsStatus(drivingStatus: Boolean)

    private val mVolumeObserver: ContentObserver = object : ContentObserver(mHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val isVolumeChanged =
                VolumeChangeManager.isVolumeChanged(getSystemService(AUDIO_SERVICE) as AudioManager)
            Timber.e("selfChange: $selfChange ##### isVolumeChanged: $isVolumeChanged")
            if (!selfChange && isVolumeChanged) {
                if (appHasLocationUpdates && isInCorrectScreenOrientation)
                    recordVideo(labelName = "Manual/Volume", isManual = true)
            }
        }
    }

    private fun syncOfflineCount() = hoverScope.launch {
        val videoCount = videoUploaderRepository.getOfflineVideosCount()
        videoUploaderRepository.updateVideoCount(videoCount.toString())
    }
}