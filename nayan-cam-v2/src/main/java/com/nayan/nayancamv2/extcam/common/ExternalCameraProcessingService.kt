package com.nayan.nayancamv2.extcam.common

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.graphics.Bitmap
import android.location.Location
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.di.NayanCamModuleDependencies
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.LocationFailureState
import co.nayan.c3v2.core.models.LocationSuccessState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.driver_module.LocationData
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.AIResultsHelperImpl
import com.nayan.nayancamv2.AIResultsHelperImpl.HoverSwitchState
import com.nayan.nayancamv2.AIResultsHelperImpl.SwitchToActivity
import com.nayan.nayancamv2.AIResultsHelperImpl.SwitchToService
import com.nayan.nayancamv2.BaseHoverService
import com.nayan.nayancamv2.ai.CameraProcessor
import com.nayan.nayancamv2.ai.InitState
import com.nayan.nayancamv2.ai.ObjectOfInterestListener
import com.nayan.nayancamv2.ai.createYuvImage
import com.nayan.nayancamv2.ai.yuvImageToBitmap
import com.nayan.nayancamv2.di.DaggerNayanCamComponent
import com.nayan.nayancamv2.extcam.dashcam.DashcamStreamingActivity
import com.nayan.nayancamv2.extcam.dashcam.SocketServer
import com.nayan.nayancamv2.extcam.drone.DroneStreamingActivity
import com.nayan.nayancamv2.extcam.media.VideoStreamDecoder
import com.nayan.nayancamv2.funIfPointFallsInKmlBoundaries
import com.nayan.nayancamv2.funIfUserLocationFallsWithInSurge
import com.nayan.nayancamv2.getCurrentEnabledWorkflows
import com.nayan.nayancamv2.helper.GlobalParams.appHasLocationUpdates
import com.nayan.nayancamv2.helper.GlobalParams.currentTemperature
import com.nayan.nayancamv2.helper.GlobalParams.hasValidSpeed
import com.nayan.nayancamv2.helper.GlobalParams.ifUserLocationFallsWithInSurge
import com.nayan.nayancamv2.helper.GlobalParams.ifUserRecordingOnBlackLines
import com.nayan.nayancamv2.helper.GlobalParams.isCameraExternal
import com.nayan.nayancamv2.helper.GlobalParams.isProcessingFrame
import com.nayan.nayancamv2.helper.GlobalParams.isRecordingVideo
import com.nayan.nayancamv2.helper.GlobalParams.shouldStartRecordingOnceBufferIsFilled
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.isValidLatLng
import com.nayan.nayancamv2.model.DroneLocation
import com.nayan.nayancamv2.model.ExtCamType
import com.nayan.nayancamv2.model.RecognizedObject
import com.nayan.nayancamv2.model.RecordingData
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.model.UserLocationMeta
import com.nayan.nayancamv2.repository.repository_location.ILocationRepository
import com.nayan.nayancamv2.startAttendanceSyncingRequest
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.temperature.TemperatureUtil
import com.nayan.nayancamv2.util.CommonUtils
import com.nayan.nayancamv2.util.Constants.IS_FROM_HOVER
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_2_sec
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.LAST_CAMERA_FRAME_THRESHOLD
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.SURVEYOR_SPEED_MAX_THRESHOLD
import com.nayan.nayancamv2.util.RecordingEventState.DRIVING_FAST
import com.nayan.nayancamv2.util.RecordingEventState.NOT_IN_SURGE
import com.nayan.nayancamv2.util.RecordingEventState.ORIENTATION_ERROR
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_CORRUPTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_FAILED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_STARTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_SUCCESSFUL
import com.nayan.nayancamv2.util.SamplingRate
import com.nayan.nayancamv2.videouploder.AttendanceSyncManager
import dagger.hilt.android.EntryPointAccessors
import dji.common.flightcontroller.FlightControllerState
import dji.sdk.camera.VideoFeeder
import dji.sdk.camera.VideoFeeder.VideoDataListener
import dji.sdk.codec.DJICodecManager
import dji.sdk.flightcontroller.FlightController
import dji.sdk.products.Aircraft
import dji.sdk.sdkmanager.DJISDKManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ExternalCameraProcessingService : BaseHoverService(),
    DJICodecManager.YuvDataCallback, VideoDataListener, FlightControllerState.Callback {

    @Inject
    lateinit var locationRepository: ILocationRepository

    @Inject
    lateinit var attendanceSyncManager: AttendanceSyncManager

    private val iaiResultsHelper: IAIResultsHelper = AIResultsHelperImpl
    private var shouldCheckForBlackFrame = true
    private val locationRequestDelay by lazy { TimeUnit.SECONDS.toMillis(5) }
    private val locationRequestHandler by lazy { Handler(Looper.getMainLooper()) }
    private val isAIMode by lazy { mNayanCamModuleInteractor.isAIMode() }
    private val isCRMode by lazy { mNayanCamModuleInteractor.isCRMode() }
    private var isHoverMode = false
    private var activityState: ActivityState? = null
    private val locationRequestRunnable = Runnable { nayanCamRepository.checkForLocationRequest() }
    private lateinit var bitmap: Bitmap

    private val streamDecoderForAI: VideoStreamDecoder by lazy {
        VideoStreamDecoder(
            iRecordingHelper,
            iMetaDataHelper,
            mNayanCamModuleInteractor.getDeviceModel(),
            false
        ).apply {
            width = 1280
            height = 720
            shouldGetFrameData = true
        }
    }

    private val encoderStreamDecoder: VideoStreamDecoder by lazy {
        VideoStreamDecoder(
            iRecordingHelper,
            iMetaDataHelper,
            mNayanCamModuleInteractor.getDeviceModel(),
            true
        )
    }

    @Inject
    lateinit var imageProcessor: CameraProcessor

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    private var lastAIProcessTime = 0L
    var count = 0
    private var isServiceRunning = false
    private var mSamplingRate: Int = SamplingRate.SAMPLING_RATE
    private lateinit var standardVideoFeeder: VideoFeeder.VideoFeed
    private var camType = ExtCamType.DashCamera
    private var mFlightController: FlightController? = null
    private var droneLocationLat: Double = 0.0
    private var droneLocationLng: Double = 0.0
    private var droneAlt: Double = 0.0
    private var droneHeading: Float = 0.0F
    private var lastLocationPostTime = 0L
    private var prefix = ""

    override fun onCreate() {
        super.onCreate()
        isCameraExternal = true
        DaggerNayanCamComponent.builder()
            .context(this)
            .appDependencies(
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    NayanCamModuleDependencies::class.java
                )
            ).build().inject(this)

        iaiResultsHelper.resetAiResults()
        locationRequestHandler.post(locationRequestRunnable)
    }

    private val hoverSwitchObserver = Observer<HoverSwitchState> {
        when (it) {
            is SwitchToService -> {
                isHoverMode = true
                postDelayed(TimeUnit.SECONDS.toMillis(3)) {
                    showHoverIcon()
                }
            }

            is SwitchToActivity -> {
                isHoverMode = false
                hideHoverIcon()
            }
        }
    }

    private fun setSensorObserver() {
        //adding phones sensor data to sensor meta data list
        nayanCamRepository.getSensorLiveData(this@ExternalCameraProcessingService)
            .observe(this@ExternalCameraProcessingService) {
                encoderStreamDecoder.currentSensorMeta = it
            }
    }

    private fun setLocationObserver() {
        // use drones location meta in case of Drone
        if (camType == ExtCamType.Drone) initFlightController()
        nayanCamRepository.getLocationState()
            .observe(this@ExternalCameraProcessingService, locationUpdateObserver)
    }

    private val locationUpdateObserver = Observer<ActivityState> {
        when (it) {
            is LocationSuccessState -> {
                it.location?.let { loc ->
                    appHasLocationUpdates = true
                    userLocation = UserLocation(loc.latitude, loc.longitude)
                    if (camType == ExtCamType.Drone &&
                        (mNayanCamModuleInteractor.getIsDroneLocationOnly() || iaiResultsHelper.getDroneLocationState().value == DroneLocation.Available)
                    ) return@let

                    checkLocationWarningChecks(loc)
                }
            }

            is LocationFailureState -> {
                locationRequestHandler.postDelayed(locationRequestRunnable, locationRequestDelay)
            }
        }
    }

    private fun checkLocationWarningChecks(location: Location) = lifecycleScope.launch {
        ifUserLocationFallsWithInSurge = if (mNayanCamModuleInteractor.isSurveyor()) {
            val surgeLocations = mNayanCamModuleInteractor.getSurgeLocations()
            val cityKmlBoundaries = mNayanCamModuleInteractor.getCityKmlBoundaries()
            val point = LatLng(location.latitude, location.longitude)
            (funIfPointFallsInKmlBoundaries(point, cityKmlBoundaries) ||
                    funIfUserLocationFallsWithInSurge(point, surgeLocations))
        } else true

        hasValidSpeed = if (camType == ExtCamType.DashCamera) {
            if (mNayanCamModuleInteractor.isSurveyor() && ifUserLocationFallsWithInSurge)
                location.speed <= SURVEYOR_SPEED_MAX_THRESHOLD
            else true
        } else true
        updateSurveyorStatus()

        val hasValidConditionsForSurveyor =
            (mNayanCamModuleInteractor.isSurveyor() && ifUserLocationFallsWithInSurge)
        if (ifUserRecordingOnBlackLines.not() && hasValidSpeed
            && (hasValidConditionsForSurveyor || isAIMode)
        ) {
            withContext(Dispatchers.IO) {
                encoderStreamDecoder.currentLocationMeta = UserLocationMeta(
                    location.latitude,
                    location.longitude,
                    location.altitude,
                    location.speed.toString(),
                    location.bearing.toString(),
                    System.currentTimeMillis()
                )
                addLocationToDatabase(location) { lastSyncTimeStamp ->
                    val diff = System.currentTimeMillis() - lastSyncTimeStamp
                    if ((activityState != ProgressState) && diff >= DEVICE_PERFORMANCE.DELAYED_10) {
                        attendanceSyncManager.locationSyncLiveData.postValue(ProgressState)
                        startAttendanceSyncingRequest(false)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.getStringExtra("camType").equals("drone")) {
            sharedPrefManager.setDefaultDashcam(false)
            camType = ExtCamType.Drone
            setDefaultHoverIcon(camType)
        } else if (intent?.getStringExtra("camType").equals("dashcam")) {
            sharedPrefManager.setDefaultDashcam(true)
            camType = ExtCamType.DashCamera
            setDefaultHoverIcon(camType)
        }
        setGenericObservers()
        setLocationObserver()
        setSensorObserver()
        if (camType == ExtCamType.Drone) encoderStreamDecoder.shouldGetFrameData = true
        if (!isServiceRunning) {
            initializeCameraAndVideo()
            startAIProcessing()
            isServiceRunning = true
        }
        return START_STICKY
    }

    private fun setGenericObservers() = lifecycleScope.launch {
        BlackFrameUtil.isBlackFrame.observe(this@ExternalCameraProcessingService) {
            if (it.not()) shouldCheckForBlackFrame = false
        }
        encoderStreamDecoder.getFileSaveLD().observe(this@ExternalCameraProcessingService) {
            encoderStreamDecoder.fileSaveInProgress = it
        }
        iaiResultsHelper.switchHoverActivityLiveData()
            .observe(this@ExternalCameraProcessingService, hoverSwitchObserver)
        attendanceSyncManager.locationSyncLiveData.observe(this@ExternalCameraProcessingService) {
            activityState = it
        }
        encoderStreamDecoder.getRecordingStateLD().collect(recordingStateObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        //  saveLogs()
        stopAIProcessing()
        releaseCamera()
        stopReceivingLocationUpdates()
        BlackFrameUtil.reset()
        isServiceRunning = false
    }

    override fun checkTemperature(
        temperature: Float,
        driverLiteThreshold: Float,
        overHeatingThreshold: Float
    ) {
        currentTemperature = temperature
        Timber.d("checkTemperature: %s", temperature)
        val currentTime = System.currentTimeMillis()
        val secondsBeforeLastMessage =
            TimeUnit.MILLISECONDS.toSeconds(currentTime - lastShownTempMessage)
        Timber.e("secondsBeforeLastMessage %s", secondsBeforeLastMessage)

        if (secondsBeforeLastMessage > 25 || temperature >= overHeatingThreshold) {
            lastShownTempMessage = currentTime
            val message = TemperatureUtil.getTempMessage(
                this,
                temperature,
                driverLiteThreshold,
                overHeatingThreshold
            )
            temperatureMessageTxt.text = message
            messageParentLayout.visible()
            postDelayed(DELAYED_2_sec) { messageParentLayout.gone() }
        }

        when {
            (temperature >= overHeatingThreshold) -> {
                updateHoverIcon(HoverIconType.TemperatureError)
            }

            else -> if ((camType == ExtCamType.Drone && iaiResultsHelper.getDroneLocationState().value != DroneLocation.Error) || (ifUserLocationFallsWithInSurge && hasValidSpeed))
                updateHoverIcon(HoverIconType.DefaultHoverIcon)
        }
    }

    override fun setNotification() {
        val notificationId = 1234
        val channelId = "co.nayan.android.external_service"
        val notificationMessage = if (camType == ExtCamType.DashCamera)
            resources.getString(R.string.recording_dashcam)
        else resources.getString(R.string.recording_drone)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel(channelId, getString(R.string.hover_service))
        val notification = NotificationCompat.Builder(this, channelId).apply {
            setContentTitle(getString(R.string.hover_service))
            setContentText(notificationMessage)
            setSmallIcon(R.drawable.notification_icon)
            priority = NotificationCompat.PRIORITY_HIGH
        }.build()
        notification.flags =
            NotificationCompat.FLAG_ONGOING_EVENT or NotificationCompat.FLAG_NO_CLEAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val serviceType = FOREGROUND_SERVICE_TYPE_LOCATION
            ServiceCompat.startForeground(this, notificationId, notification, serviceType)
        } else startForeground(notificationId, notification)
    }

    private fun stopReceivingLocationUpdates() {
        nayanCamRepository.stopLocationUpdate()
        mFlightController?.setStateCallback(null)
        appHasLocationUpdates = false
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun initializeCameraAndVideo() {
        // NativeHelper.getInstance().init()
        streamDecoderForAI.init(this@ExternalCameraProcessingService, null)
        streamDecoderForAI.resume()
        streamDecoderForAI.setYuvDataListener(this@ExternalCameraProcessingService)
        encoderStreamDecoder.init(this@ExternalCameraProcessingService, null)
        encoderStreamDecoder.resume()
        when (camType) {
            ExtCamType.Drone -> {
                standardVideoFeeder = VideoFeeder.getInstance().primaryVideoFeed
                standardVideoFeeder.addVideoDataListener(this)
                prefix = "dro"

            }

            ExtCamType.DashCamera -> {
                SocketServer.videoStreamDecoders.add(encoderStreamDecoder)
                prefix = "dsh"
            }

            else -> {
                stopSelf()
            }
        }
        imageProcessor.mObjectOfInterestListener = objectOfInterestListener
        imageProcessor.initClassifiers(this@ExternalCameraProcessingService)
    }

    private fun startAIProcessing() {
        imageProcessor.onStateChanged(InitState)
    }

    private fun stopAIProcessing() {
        encoderStreamDecoder.stop()
        streamDecoderForAI.stop()
    }

    private fun releaseCamera() {
        encoderStreamDecoder.stop()
        if (camType == ExtCamType.Drone)
            VideoFeeder.getInstance().primaryVideoFeed.removeVideoDataListener(this)
        else SocketServer.videoStreamDecoders.clear()
    }

    override fun onYuvDataReceived(
        format: MediaFormat,
        yuvFrame: ByteBuffer,
        dataSize: Int,
        width: Int,
        height: Int
    ) {
        Timber.e("isProcessingFrame : $isProcessingFrame, width: $width, height: $height")
        if ((count++ % 30 != 0) || isProcessingFrame || appHasLocationUpdates.not() || hasValidSpeed.not()
            || ifUserLocationFallsWithInSurge.not() || ifUserRecordingOnBlackLines
        ) return
        if (camType == ExtCamType.Drone && (mNayanCamModuleInteractor.getIsDroneLocationOnly()
                    && iaiResultsHelper.getDroneLocationState().value != DroneLocation.Available)
        ) return

        val currentTimeMillis = System.currentTimeMillis()
        val diffGap = currentTimeMillis - lastAIProcessTime
        if ((isRecordingVideo || isProcessingFrame)
            && TimeUnit.MILLISECONDS.toSeconds(diffGap) > LAST_CAMERA_FRAME_THRESHOLD
        ) {
            isRecordingVideo = false
            isProcessingFrame = false
            return
        }

        try {
            Timber.e("isProcessingFrame2 [onYuvDataReceived] dataSize : $dataSize")
            val bytes = ByteArray(dataSize)
            yuvFrame[bytes]
            val yuvImage = bytes.createYuvImage(width, height)
            if (yuvImage != null) {
                Timber.e("isProcessingFrame3 [yuvImage] available")
                bitmap = yuvImage.yuvImageToBitmap(width, height)
                processImage(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processImage(
        bitmap: Bitmap
    ) = lifecycleScope.launch(Dispatchers.IO) {
        imageProcessor.onStateChanged(InitState)
        try {
            Timber.e("**********************************[processImage] StartProcessingImage *************************")
            val diffGap = System.currentTimeMillis() - lastAIProcessTime
            val nextScan = TimeUnit.MILLISECONDS.toSeconds(diffGap)
            val allowSamplingRate = (nextScan >= mSamplingRate)
            if (allowSamplingRate) {
                if (mNayanCamModuleInteractor.isSurveyor().not()
                    && isCRMode.not() && isAIMode.not()
                ) return@launch
                isProcessingFrame = true
                Timber.e("############################")
                Timber.e("isProcessingFrame: $isProcessingFrame")
                Timber.e("############################")
                processAI(bitmap)
            }
        } catch (ex: Exception) {
            Firebase.crashlytics.recordException(ex)
            Timber.e(ex, "Exception!")
        }
    }

    private suspend fun processAI(imageFrame: Bitmap) = withContext(Dispatchers.IO) {
        Timber.d("process AI called & isAIMode: $isAIMode")
        lastAIProcessTime = System.currentTimeMillis()
        sharedPrefManager.setLastTimeImageAvailableCalled(System.currentTimeMillis())
        val location = userLocation?.let { LatLng(it.latitude, it.longitude) } ?: run { null }
        val allWorkFlowList = sharedPrefManager.getCameraAIWorkFlows()
        val currentEnabledWorkflows = getCurrentEnabledWorkflows(allWorkFlowList, location)
        val workFlowList = when (camType) {
            ExtCamType.Drone -> {
                currentEnabledWorkflows.filter { it.workflow_IsDroneEnabled }
            }

            ExtCamType.DashCamera -> {
                currentEnabledWorkflows.filter { it.workflow_IsDroneEnabled.not() }
            }

            else -> {
                listOf()
            }
        }
        imageProcessor.startProcessing(imageFrame, isAIMode, workFlowList)
    }

    private val objectOfInterestListener = object : ObjectOfInterestListener {
        override fun onObjectDetected(
            bitmap: Bitmap,
            className: String,
            workFlowIndex: Int,
            aiModelIndex: Int
        ) {
            iaiResultsHelper.addResults(
                RecognizedObject(
                    Pair(bitmap, className),
                    aiModelIndex,
                    workFlowIndex
                )
            )
        }

        override fun onStartRecording(
            modelName: String,
            labelName: String,
            confidence: String,
            recordedWorkFlowMetaData: String
        ) {
            recordVideo(
                modelName,
                labelName,
                confidence,
                recordedWorkFlowMetaData = recordedWorkFlowMetaData
            )
        }

        override fun onRunningOutOfRAM(availMem: Float) {}
        override fun isWorkflowAvailable(status: Boolean) {}
        override fun onAIScanning() {
            updateHoverIcon(HoverIconType.OnAIScanning)
        }

        override fun updateCameraISOExposure() {

        }
    }

    private fun recordVideo(
        modelName: String = "",
        labelName: String = "",
        confidence: String = "",
        isManual: Boolean = false,
        recordedWorkFlowMetaData: String = ""
    ) = lifecycleScope.launch {
        val isTapped = labelName.contains("Tap")
        val deviceModel = mNayanCamModuleInteractor.getDeviceModel()
        when {
            (!storageUtil.isMemoryAvailableForRecording(deviceModel)) -> {
                Timber.d("🦀recordVideo() : Memory Not available for recording")
                Firebase.crashlytics.recordException(Exception("Storage running out of space."))
                mNayanCamModuleInteractor.startSettingsActivity(
                    this@ExternalCameraProcessingService,
                    isStorageFull = true
                )
                stopSelf()
                resetConnectionIfRequired()
                return@launch
            }

            (mNayanCamModuleInteractor.isSurveyor() && isRecordingVideo) -> return@launch

            (!shouldStartRecordingOnceBufferIsFilled && isRecordingVideo) -> {
                // Start recording once buffer is filled
                shouldStartRecordingOnceBufferIsFilled = true
                storageUtil.createNewVideoFile(
                    location = userLocation ?: UserLocation(),
                    isManual = isManual,
                    isManualTap = isTapped,
                    prefix = prefix
                )?.also { file ->
                    encoderStreamDecoder.setDelayVideoRecordingData(
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

        userLocation?.let { currentUserLocation ->
            if (camType == ExtCamType.Drone
                && droneLocationLat.isNaN().not()
                && droneLocationLng.isNaN().not()
                && droneLocationLat > 0.0
            ) {
                currentUserLocation.latitude = droneLocationLat
                currentUserLocation.longitude = droneLocationLng
                currentUserLocation.altitude = droneAlt
            }
            when {
                (!isValidLatLng(currentUserLocation.latitude, currentUserLocation.longitude)) -> {
                    Timber.e("######## $userLocation not null error due to latitude ${currentUserLocation.latitude} longitude ${currentUserLocation.longitude}###############")
                    Timber.e("######## start recording ###############")
                    Timber.e("######## location error ###############")
                }

                else -> {
                    storageUtil.createNewVideoFile(
                        prefix = prefix,
                        location = currentUserLocation,
                        isManual = isManual,
                        isManualTap = isTapped
                    ).let {
                        it?.let { file ->
                            encoderStreamDecoder.recordVideo(
                                file,
                                currentUserLocation,
                                modelName,
                                labelName,
                                confidence,
                                isManual,
                                recordedWorkFlowMetaData
                            )
                        }
                    }
                }
            }
        } ?: run {
            Timber.e("######## ${getString(R.string.location_error)} ###############")
            Timber.e("######## start recording ###############")
            Timber.e("######## location error ###############")
        }
    }

    private val recordingStateObserver = FlowCollector<RecordingState?> {
        it?.let { iaiResultsHelper.recordingState(it) }
        when (it?.recordingState) {
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

            ORIENTATION_ERROR -> Timber.e("ORIENTATION_ERROR")
        }
    }

    private fun initFlightController() {
        if (isFlightControllerSupported()) {
            mFlightController = (DJISDKManager.getInstance().product as Aircraft).flightController
            mFlightController?.setStateCallback(this)
        }
    }

    private fun isFlightControllerSupported(): Boolean {
        return DJISDKManager.getInstance().product != null &&
                DJISDKManager.getInstance().product is Aircraft && (DJISDKManager.getInstance().product as Aircraft).flightController != null
    }

    /**Raw Video Data Callback for Drone -- send to decoder for parsing **/
    override fun onReceive(videoData: ByteArray?, size: Int) {
        //sending to only one decoder instance as the Native Interface is a Singleton and will transmit to all VideoStreamDecoder Interfaces
        streamDecoderForAI.parse(videoData, size)
    }

    /** location listener for drone **/
    override fun onUpdate(djiFlightControllerCurrentState: FlightControllerState) {
        try {
            lifecycleScope.launch {
                appHasLocationUpdates = true
                droneLocationLat = djiFlightControllerCurrentState.aircraftLocation.latitude
                droneLocationLng = djiFlightControllerCurrentState.aircraftLocation.longitude
                droneAlt = djiFlightControllerCurrentState.aircraftLocation.altitude.toDouble()
                droneHeading = mFlightController?.compass?.heading ?: 0.0F
                if ((droneLocationLat.isNaN().not() && droneLocationLng.isNaN().not())
                    && (droneLocationLat > 0.0 && droneLocationLng > 0.0)
                ) {
                    val currentTimeMillis = System.currentTimeMillis()
                    if (currentTimeMillis - lastLocationPostTime > TimeUnit.SECONDS.toMillis(2)) {
                        iaiResultsHelper.getDroneLocationData().postValue(getDroneLocationObject())
                        lastLocationPostTime = currentTimeMillis
                    }
                    if (iaiResultsHelper.getDroneLocationState().value != DroneLocation.Available) {
                        withContext(Dispatchers.Main) {
                            updateHoverIcon(HoverIconType.DefaultHoverIcon)
                            iaiResultsHelper.getDroneLocationState()
                                .postValue(DroneLocation.Available)
                            showToast("Drone Location is now available")
                        }
                    }

                    checkLocationWarningChecks(getDroneLocationObject())
                } else {
                    if (iaiResultsHelper.getDroneLocationState().value != DroneLocation.Error)
                        withContext(Dispatchers.Main) {
                            updateHoverIcon(HoverIconType.NoLocation)
                            iaiResultsHelper.getDroneLocationState().postValue(DroneLocation.Error)
                            //  showToast("Drone Location fetching error")
                        }
                }
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            Firebase.crashlytics.log("Error in Drone Location")
            e.printStackTrace()
        }
    }

    private fun getDroneLocationObject(): Location {
        val location = Location("")
        location.latitude = droneLocationLat
        location.longitude = droneLocationLng
        location.bearing = droneHeading
        location.altitude = droneAlt
        return location
    }

    override fun resetConnectionIfRequired() {
        if (camType == ExtCamType.DashCamera) {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val weakConnectivityManager = WeakReference(connectivityManager)
            iaiResultsHelper.unbindProcessFromNetwork(weakConnectivityManager)
        }
        iaiResultsHelper.changeHoverSwitchState(SwitchToActivity(""))
    }

    override fun openNayanRecorder() {
        showToast(getString(R.string.please_wait_starting_recorder))
        startCameraPreviewActivity()
        iaiResultsHelper.changeHoverSwitchState(SwitchToActivity("hover"))
    }

    override fun restartCameraService() {
        iaiResultsHelper.changeHoverSwitchState(SwitchToService("restart"))
        startCameraPreviewActivity()
    }

    private fun startCameraPreviewActivity() {
        when (camType) {
            ExtCamType.Drone -> {
                postDelayed(TimeUnit.SECONDS.toMillis(2)) {
                    Intent(this, DroneStreamingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(IS_FROM_HOVER, true)
                        startActivity(this)
                    }
                }

            }

            ExtCamType.DashCamera -> {
                postDelayed(TimeUnit.SECONDS.toMillis(2)) {
                    Intent(this, DashcamStreamingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(IS_FROM_HOVER, true)
                        startActivity(this)
                    }
                }
            }

            else -> {}
        }
        stopSelf()
    }

    private fun showRecordingAlert(
        recordingState: Int,
        sound: Int,
        message: String = ""
    ) = lifecycleScope.launch(Dispatchers.Main) {
        if (iaiResultsHelper.switchHoverActivityLiveData().value is SwitchToService) {
            CommonUtils.playSound(
                sound,
                this@ExternalCameraProcessingService,
                storageUtil.getVolumeLevel()
            )
            when (recordingState) {
                RECORDING_STARTED -> updateHoverIcon(HoverIconType.HoverRecording)
                RECORDING_SUCCESSFUL -> updateHoverIcon(HoverIconType.HoverRecordingFinished)
                RECORDING_FAILED -> updateHoverIcon(HoverIconType.HoverRecordingFailed)
                RECORDING_CORRUPTED -> updateHoverIcon(HoverIconType.HoverRecordingFailed)
            }
            recordingStatusMessageTxt.text = message
            floatingViewRecordingStatusMessage.visible()
            delay(DELAYED_2_sec)
            floatingViewRecordingStatusMessage.gone()
        }
    }

    private fun updateSurveyorStatus() = lifecycleScope.launch {
        if (!isHoverMode) {
            if (ifUserLocationFallsWithInSurge.not())
                iaiResultsHelper.recordingState(RecordingState(NOT_IN_SURGE, ""))
            else if (hasValidSpeed.not())
                iaiResultsHelper.recordingState(RecordingState(DRIVING_FAST, ""))
        } else onSurveyorWarningStatus(ifUserLocationFallsWithInSurge, hasValidSpeed)
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
}