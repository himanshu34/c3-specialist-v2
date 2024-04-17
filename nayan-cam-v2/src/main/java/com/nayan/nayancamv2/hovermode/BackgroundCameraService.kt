package com.nayan.nayancamv2.hovermode

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.lifecycleScope
import co.nayan.appsession.SessionManager
import co.nayan.c3v2.core.getDeviceAvailableRAM
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.Constants.LocationService.FETCHING_LOCATION_COMPLETE
import co.nayan.c3v2.core.utils.Constants.LocationService.FETCHING_LOCATION_ERROR
import co.nayan.c3v2.core.utils.Constants.LocationService.FETCHING_LOCATION_STARTED
import co.nayan.c3v2.core.utils.Constants.LocationService.LOCATION_UNAVAILABLE
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.NayanCamActivity
import com.nayan.nayancamv2.createRotateAnimation
import com.nayan.nayancamv2.di.SessionInteractor
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.getCurrentScreenOrientation
import com.nayan.nayancamv2.handleOrientation
import com.nayan.nayancamv2.helper.GlobalParams.appHasLocationUpdates
import com.nayan.nayancamv2.helper.GlobalParams.currentTemperature
import com.nayan.nayancamv2.helper.GlobalParams.hasValidSpeed
import com.nayan.nayancamv2.helper.GlobalParams.ifUserLocationFallsWithInSurge
import com.nayan.nayancamv2.helper.GlobalParams.ifUserRecordingOnBlackLines
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.helper.GlobalParams.isRecordingVideo
import com.nayan.nayancamv2.helper.GlobalParams.resetGlobalParams
import com.nayan.nayancamv2.hovermode.HandlerWithID.Companion.runnableIDDropCamera
import com.nayan.nayancamv2.hovermode.HandlerWithID.Companion.runnableIDTilt
import com.nayan.nayancamv2.startVideoUploadRequest
import com.nayan.nayancamv2.temperature.TemperatureUtil.getTempMessage
import com.nayan.nayancamv2.util.CommonUtils
import com.nayan.nayancamv2.util.Constants.IS_FROM_BACKGROUND
import com.nayan.nayancamv2.util.Constants.IS_FROM_HOVER
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_2_sec
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.HOVER_RESTART_THRESHOLD
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.HOVER_RESTART_THRESHOLD_KENT
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.LAST_CAMERA_FRAME_THRESHOLD
import com.nayan.nayancamv2.util.Notifications.AMOUNT_RECEIVED
import com.nayan.nayancamv2.util.Notifications.EVENTS_PAYOUT
import com.nayan.nayancamv2.util.Notifications.EVENT_TYPE
import com.nayan.nayancamv2.util.Notifications.NOTIFICATION_TYPE
import com.nayan.nayancamv2.util.Notifications.POINTS_RECEIVED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_CORRUPTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_FAILED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_STARTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_SUCCESSFUL
import com.nayan.nayancamv2.util.RotationLiveData
import com.nayan.nayancamv2.util.isServiceRunning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

@DelicateCoroutinesApi
@SuppressLint("SetTextI18n")
class BackgroundCameraService : HoverService() {

    private lateinit var sessionManager: SessionManager
    private val rotateAnim by lazy { createRotateAnimation() }

    private lateinit var rotationLiveData: RotationLiveData

    private val releaseCameraHandler by lazy { HandlerWithID() }
    private val dropCameraRunnable = Runnable {
        exit("Orientation changed from landscape to portrait")
        Intent(this, PortraitHoverService::class.java).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(this)
            else startService(this)
        }
    }

    private val tiltHandler by lazy { HandlerWithID() }
    private val tiltRunnable = Runnable { updateHoverIcon(HoverIconType.OrientationError) }

    private val notificationJob = SupervisorJob()
    private val notificationScope = CoroutineScope(Dispatchers.Main + notificationJob)
    private val sharedPrefs: SharedPreferences by lazy {
        getSharedPreferences(SessionManager.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun openNayanRecorder() {
        lifecycleScope.launch(Dispatchers.Main) {
            showToast(getString(R.string.please_wait_starting_recorder))
            postDelayed(DELAYED_2_sec) {
                startActivity(
                    Intent(this@BackgroundCameraService, NayanCamActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(IS_FROM_HOVER, true)
                )
            }
            stopSelf()
        }
    }

    override fun restartCameraService() {
        restartService()
    }

    override fun onCreate() {
        super.onCreate()
        if (isServiceRunning<PortraitHoverService>())
            stopService(Intent(this, PortraitHoverService::class.java))

        if (isServiceRunning<OverheatingHoverService>())
            stopService(Intent(this, OverheatingHoverService::class.java))

        if (isServiceRunning<ExternalCameraProcessingService>())
            stopService(Intent(this, ExternalCameraProcessingService::class.java))

        if (mNayanCamModuleInteractor is SessionInteractor) {
            sessionManager = SessionManager(
                sharedPrefs,
                null,
                this,
                null,
                (mNayanCamModuleInteractor as SessionInteractor).getSessionRepositoryInterface()
            ).apply {
                shouldCheckUserInteraction = false
                setMetaData(null, null, null, mNayanCamModuleInteractor.getCurrentRole())
            }
        }

        checkRAMStatus()
        setupObservers()
    }

    override fun setNotification() {
        try {
            val notificationId = 1234
            val channelId = "co.nayan.android.camera_service"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                createNotificationChannel(getString(R.string.hover_service))
            val notification = NotificationCompat.Builder(this, channelId).apply {
                setContentTitle(getString(R.string.hover_service))
                setContentText(getString(R.string.recording_started))
                setSmallIcon(R.drawable.notification_icon)
                priority = NotificationCompat.PRIORITY_HIGH
            }.build()
            notification.flags =
                NotificationCompat.FLAG_ONGOING_EVENT or NotificationCompat.FLAG_NO_CLEAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val serviceType = FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_LOCATION
                ServiceCompat.startForeground(this, notificationId, notification, serviceType)
            } else startForeground(notificationId, notification)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                // App not in a valid state to start foreground service
                // (e.g. started from bg)
            }
        }
    }

    override fun onDestroy() {
        resetGlobalParams()
        if (notificationJob.isActive) notificationJob.cancel()
        cameraHelper.resetNightMode()
        if (::rotationLiveData.isInitialized) rotationLiveData.onInactive()
        releaseCameraHandler.removeCallbacks(dropCameraRunnable)
        tiltHandler.removeCallbacks(tiltRunnable)
        super.onDestroy()
        startVideoUploadRequest(mNayanCamModuleInteractor.isSurveyor())
    }

    override fun restartService() {
        // Save Last restart time in preference manager
        sharedPrefManager.setLastHoverRestartCalled()
        cameraHelper.resetNightMode()
        restartingCameraService()
    }

    override fun openDashboard() {
        lifecycleScope.launch(Dispatchers.Main) {
            floatingView.gone()
            showToast(getString(R.string.please_wait_open_dashboard))
            Firebase.crashlytics.log("User opened Dashboard from hover service")
            delay(DELAYED_2_sec)
            mNayanCamModuleInteractor.startDashboardActivity(this@BackgroundCameraService)
            stopSelf()
        }
    }

    override fun optimizeFrames() {
        restartService()
    }

    override fun exit(message: String) {
        floatingView.gone()
        Firebase.crashlytics.log("User opened Dashboard from hover service")
        stopSelf()
    }

    override fun updateHoverViewForError() {
        CommonUtils.playSound(
            R.raw.camera_error_alert,
            this@BackgroundCameraService,
            storageUtil.getVolumeLevel()
        )
        restartingCameraService()
    }

    override fun openRecorder() {
        floatingView.gone()
        showToast(getString(R.string.please_wait_starting_recorder))
        postDelayed(DELAYED_2_sec) {
            startActivity(
                Intent(this@BackgroundCameraService, NayanCamActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(IS_FROM_HOVER, true)
            )
        }
        stopSelf()
    }

    override fun onRunningOutOfRAMStatus(availMem: Float) {
        lifecycleScope.launch(Dispatchers.Main) {
            recordingStatusMessageTxt.text =
                getString(R.string.error_memory, availMem.toString())
            floatingViewRecordingStatusMessage.visible()
            delay(5000)
            floatingViewRecordingStatusMessage.gone()
        }
    }

    override fun isWorkflowAvailableStatus(status: Boolean) {
        if (!status) {
            lifecycleScope.launch(Dispatchers.Main) {
                recordingStatusMessageTxt.text = "No AI WorkFlow available at your location."
                floatingViewRecordingStatusMessage.visible()
                delay(5000)
                floatingViewRecordingStatusMessage.gone()
            }
        }
    }

    override fun onAIScanningStatus() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isInCorrectScreenOrientation && ifUserRecordingOnBlackLines.not()) {
                updateHoverIcon(HoverIconType.OnAIScanning)
            }
        }
    }

    override fun onLocationUpdateStatus(locationStatus: Int, isRecordingVideo: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            when (locationStatus) {
                FETCHING_LOCATION_STARTED -> {
                    recordingStatusMessageTxt.text = getString(R.string.fetching_location)
                    floatingViewRecordingStatusMessage.visible()
                    floatingViewLocationMessage.gone()
                }

                FETCHING_LOCATION_COMPLETE -> {
                    floatingViewRecordingStatusMessage.gone()
                    floatingViewLocationMessage.gone()
                }

                LOCATION_UNAVAILABLE -> {
                    floatingViewRecordingStatusMessage.gone()
                    floatingViewLocationMessage.visible()
                }

                FETCHING_LOCATION_ERROR -> {
                    recordingStatusMessageTxt.text = if (isRecordingVideo)
                        getString(R.string.location_error) else getString(R.string.offline_location_error)
                    floatingViewRecordingStatusMessage.visible()
                    floatingViewLocationMessage.gone()
                    delay(DELAYED_2_sec)
                    floatingViewRecordingStatusMessage.gone()
                }
            }
        }
    }

    override fun onMotionlessContentDetectedStatus() {
        updateHoverIcon(HoverIconType.OpticalFlowError)
    }

    override fun onMotionContentDetectedStatus() {
        updateHoverIcon(HoverIconType.OpticalFlowSuccessful)
    }

    override fun onSensorReCalibrationStatus(currentThresholds: Float) {
        lifecycleScope.launch(Dispatchers.Main) {
            temperatureMessageTxt.text = "Sensors recalibrated to: $currentThresholds"
            messageParentLayout.visible()
            delay(DELAYED_2_sec)
            messageParentLayout.gone()
        }
    }

    override fun onDrivingOnBlackSegmentsStatus(drivingStatus: Boolean) {
        if (drivingStatus) updateHoverIcon(HoverIconType.BlackSegment)
    }

    override fun showRecordingAlert(
        recordingState: Int,
        resource: Int,
        message: String
    ) {
        lifecycleScope.launch(Dispatchers.Main) {
            CommonUtils.playSound(
                resource,
                this@BackgroundCameraService,
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelName: String) {
        val description = getString(R.string.recording_started)
        val channel =
            NotificationChannel(
                "co.nayan.android.camera_service",
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
        channel.description = description
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun restartingCameraService() = lifecycleScope.launch(Dispatchers.Main) {
        floatingView.gone()
        // starting the camera activity, as camera can't be started from the background service
        val intent = Intent(this@BackgroundCameraService, NayanCamActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(IS_FROM_BACKGROUND, true)
        postDelayed(DELAYED_2_sec) { startActivity(intent) }
        stopSelf()
    }

    private fun setupObservers() = lifecycleScope.launch {
        // Check for available RAM memory
        rotationLiveData = RotationLiveData(this@BackgroundCameraService).apply {
            observe(this@BackgroundCameraService) { state ->
                handleOrientation(
                    state,
                    mNayanCamModuleInteractor.getDeviceModel(),
                    orientationCallback
                )
            }
            onActive()
            handleOrientation(
                getCurrentScreenOrientation(),
                mNayanCamModuleInteractor.getDeviceModel(),
                orientationCallback,
                true
            )
        }

        // Observe Notifications
        notificationHelper.subscribe().observe(this@BackgroundCameraService) { intent ->
            intent?.let { readNotificationIntent(it) }
        }
    }

    private fun readNotificationIntent(intent: Intent) = notificationScope.launch {
        val angle = if (layoutParamsHoverView.x > (screenWidth() / 2)) 300F else 60F
        val type = intent.getStringExtra(NOTIFICATION_TYPE) ?: ""
        Timber.e("######## NOTIFICATION_TYPE ######## $type")
        if (type.isNotEmpty() && type == EVENTS_PAYOUT) {
            val eventType = intent.getStringExtra(EVENT_TYPE) ?: "0"
            val points = intent.getStringExtra(POINTS_RECEIVED) ?: "0"

            val event = mNayanCamModuleInteractor.getDriverEvents()
                ?.find { it.eventName.equals(eventType, ignoreCase = true) }
            event?.let {
                Timber.e("######## $eventType ######## $points ######## ${event.eventName} ######## ${event.imageUrl}")
                event.imageUrl?.let {
                    notificationHelper.startEventAnimation(floatingView, angle, points, it)
                } ?: run { notificationHelper.pollNotification() }
            } ?: run { notificationHelper.startPointsAnimation(floatingView, angle, points) }
        } else {
            val amount = intent.getStringExtra(AMOUNT_RECEIVED)
            Timber.e("######## Bonus Received ######## $amount")
            amount?.let {
                notificationHelper.startBonusAnimation(
                    floatingView,
                    angle,
                    it.toFloat().toInt().toString(),
                    R.drawable.hover_notifications_rupee
                )
            } ?: run { notificationHelper.pollNotification() }
        }
    }

    private val orientationCallback = object : (Boolean, Boolean) -> Unit {

        override fun invoke(orientationStatus: Boolean, isDefault: Boolean) {
            handleTilt(orientationStatus)
            if (!isDefault && isInCorrectScreenOrientation == orientationStatus) return
            else {
                isInCorrectScreenOrientation = orientationStatus
                if (!isInCorrectScreenOrientation
                    && !releaseCameraHandler.hasActiveRunnable(runnableIDDropCamera)
                ) releaseCameraHandler.dropCameraDelay(runnableIDDropCamera, dropCameraRunnable)
                else releaseCameraHandler.removeCallbacks(dropCameraRunnable)
            }
        }
    }

    private fun handleTilt(orientationStatus: Boolean) {
        if (orientationStatus) tiltHandler.removeCallbacks(tiltRunnable)
        else if (!tiltHandler.hasActiveRunnable(runnableIDTilt))
            tiltHandler.tiltDelayed(runnableIDTilt, tiltRunnable)
    }

    private fun checkRAMStatus() = lifecycleScope.launch(Dispatchers.IO) {
        val availMem = getDeviceAvailableRAM()
        var usedMemory = 0f
        var isDeviceCapableToRunWorkflow = false
        var hasWorkflow = false
        sharedPrefManager.getCameraAIWorkFlows()?.forEach { aiFlow ->
            if (aiFlow.cameraAIModels.isNotEmpty() && aiFlow.workflow_IsEnabled && aiFlow.workflow_IsDroneEnabled.not()) {
                hasWorkflow = true
                aiFlow.cameraAIModels.firstOrNull()?.let {
                    usedMemory += it.ram
                    if (it.ram < availMem) isDeviceCapableToRunWorkflow = true
                }
            }
        }

        if ((availMem - usedMemory) < 0.5) {
            Timber.e("#### Available Ram usage: $availMem and memory used: $usedMemory")
            sharedPrefManager.setLITEMode(true)
            Firebase.crashlytics.log("#### Available Ram usage: $availMem and memory used: $usedMemory")
        }

        withContext(Dispatchers.Main) {
            if (hasWorkflow && !isDeviceCapableToRunWorkflow) {
                recordingStatusMessageTxt.text =
                    getString(R.string.error_memory, availMem.toString())
                floatingViewRecordingStatusMessage.visible()
                delay(DELAYED_2_sec)
                floatingViewRecordingStatusMessage.gone()
            }
        }
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
            val message =
                getTempMessage(this, temperature, driverLiteThreshold, overHeatingThreshold)
            temperatureMessageTxt.text = message
            messageParentLayout.visible()
            Timber.d("temp msg -> $message")
            postDelayed(DELAYED_2_sec) {
                messageParentLayout.gone()
            }
        }

        when {
            (temperature >= overHeatingThreshold) -> {
                CommonUtils.playSound(
                    R.raw.camera_error_alert,
                    this,
                    storageUtil.getVolumeLevel()
                )
                exit("Device temperature reached maximum threshold")
                postDelayed(DELAYED_2_sec) {
                    Intent(this, OverheatingHoverService::class.java).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            startForegroundService(this)
                        else startService(this)
                    }
                }
            }

            (temperature >= driverLiteThreshold) -> {
                if (sharedPrefManager.isForcedLITEMode().not()) {
                    sharedPrefManager.setForcedLITEMode(true)
                    postDelayed(DELAYED_2_sec) {
                        showToast(getString(R.string.driver_lite_activated))
                        restartService()
                    }
                }
            }

            else -> {
                if (sharedPrefManager.isForcedLITEMode()) {
                    sharedPrefManager.setForcedLITEMode(false)
                    postDelayed(DELAYED_2_sec) { restartService() }
                }

                when {
                    hasValidSpeed.not() -> updateHoverIcon(HoverIconType.OnDrivingFast)
                    ifUserLocationFallsWithInSurge.not() -> updateHoverIcon(HoverIconType.NotInSurgeError)
                    ifUserRecordingOnBlackLines -> {}
                    else -> {
                        if (appHasLocationUpdates && isRecordingVideo.not())
                            updateHoverIcon(HoverIconType.OnAIScanning)
                        else updateHoverIcon(HoverIconType.DefaultHoverIcon)
                    }
                }
            }
        }

        if (appHasLocationUpdates && isRecordingVideo.not() && hasValidSpeed && ifUserLocationFallsWithInSurge)
            shouldRestartCameraService(temperature, overHeatingThreshold)
    }

    private fun shouldRestartCameraService(temperature: Float, overHeatingThreshold: Float) {
        val currentTimeMillis = System.currentTimeMillis()
        val lastHoverRestartInSeconds =
            TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis - sharedPrefManager.getLastHoverRestartCalled())
        val lastCamFrameInSeconds =
            TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis - sharedPrefManager.getLastTimeImageAvailableCalled())

        Timber.e("******** HoverService Restart request received at: Hover Restart At - $lastHoverRestartInSeconds && $temperature && Last Cam Frame At - $lastCamFrameInSeconds ***********")
        if ((mNayanCamModuleInteractor.getDeviceModel().isKentCam()
                    && lastHoverRestartInSeconds >= HOVER_RESTART_THRESHOLD_KENT)
            || (lastHoverRestartInSeconds >= HOVER_RESTART_THRESHOLD
                    && (temperature < overHeatingThreshold)
                    && lastCamFrameInSeconds >= LAST_CAMERA_FRAME_THRESHOLD
                    && ifUserRecordingOnBlackLines.not())
        ) {
            Timber.e("******** HoverService Restart service called: $temperature ***********")
            restartService()
            Firebase.crashlytics.log("temperature: $temperature")
            Firebase.crashlytics.log("Last camera frame captured $lastCamFrameInSeconds seconds ago")
            Firebase.crashlytics.log("hover service restart happened $lastHoverRestartInSeconds seconds ago")
            Firebase.crashlytics.recordException(Exception("restartHoverService"))
        }
    }

    override fun showOrientationHint() {
        floatingViewIvLogo.setImageResource(R.drawable.ic_black_rotate)
        if (rotateAnim.hasStarted().not() || rotateAnim.hasEnded()) {
            floatingViewIvLogo.clearAnimation()
            floatingViewIvLogo.startAnimation(rotateAnim)
        }
    }
}