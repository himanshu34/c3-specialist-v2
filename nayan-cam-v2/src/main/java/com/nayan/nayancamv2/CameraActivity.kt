package com.nayan.nayancamv2

import android.Manifest.permission.CALL_PHONE
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import co.nayan.c3v2.core.getDeviceAvailableRAM
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.LocationFailureState
import co.nayan.c3v2.core.models.LocationSuccessState
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.NayanCamActivityBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.ai.ObjectOfInterestListener
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.helper.BaseCameraPreviewListener
import com.nayan.nayancamv2.helper.GlobalParams.appHasLocationUpdates
import com.nayan.nayancamv2.helper.GlobalParams.currentTemperature
import com.nayan.nayancamv2.helper.GlobalParams.ifUserRecordingOnBlackLines
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.helper.GlobalParams.resetGlobalParams
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.hovermode.BackgroundCameraService
import com.nayan.nayancamv2.hovermode.HandlerWithID
import com.nayan.nayancamv2.hovermode.HandlerWithID.Companion.runnableIDTilt
import com.nayan.nayancamv2.hovermode.OverheatingHoverService
import com.nayan.nayancamv2.hovermode.PortraitHoverService
import com.nayan.nayancamv2.modeldownloader.ErrorDialogFragment
import com.nayan.nayancamv2.temperature.TemperatureProvider
import com.nayan.nayancamv2.temperature.TemperatureUtil
import com.nayan.nayancamv2.ui.cam.NayanCamFragment
import com.nayan.nayancamv2.ui.cam.NayanCamViewModel
import com.nayan.nayancamv2.util.CommonUtils
import com.nayan.nayancamv2.util.Constants.IS_FROM_BACKGROUND
import com.nayan.nayancamv2.util.EventObserver
import com.nayan.nayancamv2.util.RotationLiveData
import com.nayan.nayancamv2.util.isServiceRunning
import com.vividsolutions.jts.index.SpatialIndex
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Base class for camera and location
 *
 */
@OptIn(DelicateCoroutinesApi::class)
abstract class CameraActivity : BaseActivity(), OnHoverClickListener {

    protected val viewModel: NayanCamViewModel by viewModels { viewModelFactory }
    private val binding: NayanCamActivityBinding by viewBinding(NayanCamActivityBinding::inflate)
    protected lateinit var spatialIndex: SpatialIndex

    private val overHeatingThreshold by lazy { nayanCamModuleInteractor.getOverheatingRestartTemperature() }
    private val driverLiteThreshold by lazy { nayanCamModuleInteractor.getDriverLiteTemperature() }
    private val locationRequestDelay by lazy { TimeUnit.SECONDS.toMillis(5) }
    private val locationRequestHandler by lazy { Handler(Looper.getMainLooper()) }
    private val locationRequestRunnable = Runnable { viewModel.checkForLocationRequest() }

    private val locationUpdateObserver: Observer<ActivityState> = Observer {
        when (it) {
            is LocationSuccessState -> {
                it.location?.let { loc ->
                    Timber.e("Location Received with ${loc.time}")
                    appHasLocationUpdates = true
                    nayanCamModuleInteractor.saveLastLocation(loc.latitude, loc.longitude)
                    locationAvailable(loc)
                }
            }

            is LocationFailureState -> {
                it.exception?.let { exception ->
                    showToast(it.errorMessage)
                    if (exception is ResolvableApiException) {
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            resolutionForResult.launch(
                                IntentSenderRequest.Builder(exception.resolution).build()
                            )
                        } catch (sendEx: IntentSender.SendIntentException) {
                            Firebase.crashlytics.recordException(sendEx)
                            Timber.e(sendEx)
                        }
                    }
                } ?: run {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.please_enable_location))
                        .setMessage(it.errorMessage)
                        .setPositiveButton(getString(R.string.close)) { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                }
                appHasLocationUpdates = false
                locationRequestHandler.postDelayed(locationRequestRunnable, locationRequestDelay)
            }
        }
    }

    private lateinit var aiResultsAdapter: AIResultsAdapter
    private val rotateAnim by lazy { createRotateAnimation() }
    private lateinit var rotationLiveData: RotationLiveData

    private val tiltHandler by lazy { HandlerWithID() }
    private val tiltRunnable = Runnable {
        binding.ivChangeOrientation.let {
            it.visible()
            if (rotateAnim.hasStarted().not() || rotateAnim.hasEnded())
                it.startAnimation(rotateAnim)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.hasExtra(IS_FROM_BACKGROUND) &&
            intent.getBooleanExtra(IS_FROM_BACKGROUND, false)
        ) {
            //the activity is started from background ,
            // (Starting camera from background service doesn't work, so we launch the activity
            // from background and start the service from the activity)
            startCameraService()
        } else {
            if (isServiceRunning<BackgroundCameraService>())
                stopService(Intent(this, BackgroundCameraService::class.java))

            if (isServiceRunning<PortraitHoverService>())
                stopService(Intent(this, PortraitHoverService::class.java))

            if (isServiceRunning<OverheatingHoverService>())
                stopService(Intent(this, OverheatingHoverService::class.java))

            if (isServiceRunning<ExternalCameraProcessingService>())
                stopService(Intent(this, ExternalCameraProcessingService::class.java))

            setContentView(binding.root)

//            if (nayanCamModuleInteractor.isOnlyPreviewMode()) previewContainer.visible()
//            else previewContainer.gone()
            viewModel.initOpenCvForOpticalFlow(this)
            setUpAndInitialize()
        }
        viewModel.syncOfflineCount()
    }

    override fun onResume() {
        super.onResume()
        locationRequestHandler.post(locationRequestRunnable)
    }

    override fun onPause() {
        super.onPause()
        stopReceivingLocationUpdates()
    }

    private fun stopReceivingLocationUpdates() {
        appHasLocationUpdates = false
        viewModel.stopReceivingLocationUpdates()
        locationRequestHandler.removeCallbacksAndMessages(null)
    }

    private fun setUpAndInitialize() = lifecycleScope.launch {
        val temperature = getBatteryTemperature()
        if (temperature >= overHeatingThreshold) {
            showToast(getString(R.string.temp_state_4, temperature.toString()))
            Firebase.crashlytics.recordException(Exception("Camera shut down due to temperature."))
            finish()
        } else {
            try {
                viewModel.startTempObserving()
                setUpCameraFragment()
                viewModel.locationState.observe(this@CameraActivity, locationUpdateObserver)

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

                if (hasWorkflow && !isDeviceCapableToRunWorkflow)
                    showToast(getString(R.string.error_memory, availMem.toString()))
            } catch (e: Exception) {
                e.printStackTrace()
                Firebase.crashlytics.recordException(e)
            }

            viewModel.getTemperatureLiveData().observe(this@CameraActivity, temperatureObserver)
        }
    }

    private val temperatureObserver = EventObserver<TemperatureProvider.TempEvent> {
        when (it) {
            TemperatureProvider.TempEvent.Error -> {
                Timber.d("Temp Error ...")
            }

            TemperatureProvider.TempEvent.Loading -> {
                Timber.d("Temp loading ...")
            }

            is TemperatureProvider.TempEvent.TemperatureUpdate -> {
                Timber.d("Temp updates ...")
                it.data.let { temp ->
                    Timber.d("Temp data --> $temp")
                    currentTemperature = temp.temperature
                    showTemperatureMessage(
                        temp.temperature,
                        driverLiteThreshold,
                        overHeatingThreshold
                    )
                }
            }
        }
    }

    val aiResultList = ArrayList<HashMap<Int, Pair<Bitmap, String>>>()
    var aiResultMap = HashMap<Int, Pair<Bitmap, String>>()

    private val objectListener = object : ObjectOfInterestListener {

        override fun onObjectDetected(
            bitmap: Bitmap,
            className: String,
            workFlowIndex: Int,
            aiModelIndex: Int
        ) {
            lifecycleScope.launch(Dispatchers.Main) {
                if (aiModelIndex == 0) {
                    aiResultMap = HashMap()
                    aiResultList.add(aiResultMap)
                }
                aiResultList[aiResultList.size - 1][aiModelIndex] = Pair(bitmap, className)
                if (::aiResultsAdapter.isInitialized) {
                    aiResultsAdapter.addAll(aiResultList)
                    binding.aiResults.smoothScrollToPosition(aiResultsAdapter.itemCount)
                }
            }
        }

        override fun onStartRecording(
            modelName: String,
            labelName: String,
            confidence: String,
            recordedWorkFlowMetaData: String
        ) {
            viewModel.recordVideo(
                userLocation = userLocation,
                this@CameraActivity,
                modelName, labelName, confidence,
                recordedWorkFlowMetaData = recordedWorkFlowMetaData,
                onLocationError = {
                    showToast(getString(R.string.location_error))
                }
            )
        }

        override fun onRunningOutOfRAM(availMem: Float) {
            showToast(getString(R.string.error_memory, availMem.toString()))
        }

        override fun isWorkflowAvailable(status: Boolean) {
            if (!status) {
                supportFragmentManager.showDialogFragment(
                    ErrorDialogFragment(
                        "AI WorkFlow unavailable",
                        "No workFlow available for your location"
                    ) {
                        val permissionCheck =
                            ContextCompat.checkSelfPermission(this@CameraActivity, CALL_PHONE)
                        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(CALL_PHONE), 123)
                        } else tapToCall("+91-9958083303", this@CameraActivity)
                    })
            }
        }

        override fun onAIScanning() {
            viewModel.onAIScanning()
        }

        override fun updateCameraISOExposure() {
            viewModel.updateCameraISOExposure()
        }
    }

    fun onDrivingOnBlackLines() = lifecycleScope.launch(Dispatchers.Main) {
        if (ifUserRecordingOnBlackLines.not()) binding.blackLineCard.gone()
        else {
            binding.tvBlackLineText.text = getString(R.string.warning_black_line)
            binding.blackLineCard.visible()
        }
    }

    fun onSurveyorWarningStatus(
        ifUserLocationFallsWithInSurge: Boolean,
        isValidSpeed: Boolean
    ) = lifecycleScope.launch(Dispatchers.Main) {
        if (nayanCamModuleInteractor.isSurveyor() && isInCorrectScreenOrientation) {
            if (ifUserLocationFallsWithInSurge) {
                if (isValidSpeed) binding.motionSurveyorCard.gone()
                else {
                    val warningTextBuilder = StringBuilder()
                    warningTextBuilder.append(getString(R.string.warning_driving_fast))
                    warningTextBuilder.append("\n\n")
                    warningTextBuilder.append(getString(R.string.driving_fast_text))
                    val warningTxt = warningTextBuilder.toString()
                    binding.tvMotionSurveyorText.text = warningTxt
                    binding.motionSurveyorCard.visible()
                    CommonUtils.playSound(
                        R.raw.speed_alert,
                        this@CameraActivity,
                        storageUtil.getVolumeLevel()
                    )
                }
            } else {
                viewModel.onWithInSurgeLocationStatus()
                binding.tvMotionSurveyorText.text = getString(R.string.not_in_surge_text)
                binding.motionSurveyorCard.visible()
            }
        } else binding.motionSurveyorCard.gone()
    }

    private fun initCamera() {
        rotationLiveData = RotationLiveData(this).apply {
            observe(this@CameraActivity) { state ->
                handleOrientation(
                    state,
                    nayanCamModuleInteractor.getDeviceModel(),
                    orientationCallback
                )
            }
            onActive()
            handleOrientation(
                getCurrentScreenOrientation(),
                nayanCamModuleInteractor.getDeviceModel(),
                orientationCallback
            )
        }

        binding.aiResults.apply {
            aiResultsAdapter = AIResultsAdapter()
            layoutManager = LinearLayoutManager(this@CameraActivity)
            adapter = aiResultsAdapter
        }

        try {
            cameraProcessor.initClassifiers(this)
            cameraProcessor.mObjectOfInterestListener = objectListener
            cameraPreviewListener.scheduleSampling()
            cameraPreviewListener.onOpticalFlowMotionDetected =
                object : BaseCameraPreviewListener.OnOpticalFlowMotionDetected {
                    override fun onMotionlessContentDetected() {
                        lifecycleScope.launch(Dispatchers.Main) {
                            binding.tvMotionText.text = getString(R.string.optical_motion_text)
                            binding.motionCard.visible()
                        }
                    }

                    override fun onMotionContentDetected() {
                        lifecycleScope.launch(Dispatchers.Main) { binding.motionCard.gone() }
                    }
                }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
        }
    }

    private val orientationCallback = object : (Boolean, Boolean) -> Unit {

        override fun invoke(orientationStatus: Boolean, isDefault: Boolean) {
            handleTiltAnimation(orientationStatus)
        }
    }

    private fun handleTiltAnimation(orientationStatus: Boolean) = lifecycleScope.launch {
        if (orientationStatus) {
            isInCorrectScreenOrientation = true
            tiltHandler.removeCallbacks(tiltRunnable)
            binding.ivChangeOrientation.apply {
                if (isVisible) {
                    clearAnimation()
                    gone()
                }
            }
        } else {
            isInCorrectScreenOrientation = false
            if (!tiltHandler.hasActiveRunnable(runnableIDTilt))
                tiltHandler.tiltDelayed(runnableIDTilt, tiltRunnable)
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private fun isHardwareLevelSupported(
        characteristics: CameraCharacteristics, requiredLevel: Int
    ): Boolean {
        val deviceLevel =
            characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        return if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            requiredLevel == deviceLevel
        } else requiredLevel <= deviceLevel
        // deviceLevel is not LEGACY, can use numerical sort
    }

    private fun setUpCameraFragment() = lifecycleScope.launch {
        initCamera()
        val fragment = NayanCamFragment.newInstance(
            cameraPreviewListener = cameraPreviewListener,
            imageAvailableListener = cameraPreviewListener
        )
        nayanCamComponent.inject(fragment)
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    abstract fun startCameraService()

    abstract fun locationAvailable(location: Location)

    private fun updateTemperatureWarning(temperature: Float) {
        when {
            (temperature >= overHeatingThreshold) -> {
                Firebase.crashlytics.log("Camera shut down due to temperature above 48.")
                finish()
            }

            (temperature >= driverLiteThreshold) -> {
                if (sharedPrefManager.isForcedLITEMode().not()) {
                    sharedPrefManager.setForcedLITEMode(true)
                    showToast(getString(R.string.driver_lite_activated))
                }
            }

            else -> {
                if (sharedPrefManager.isForcedLITEMode())
                    sharedPrefManager.setForcedLITEMode(false)
            }
        }
    }

    private var lastShownTempMessage = 0L
    private fun showTemperatureMessage(
        temperature: Float,
        driverLiteThreshold: Float,
        overHeatingThreshold: Float
    ) = lifecycleScope.launch(Dispatchers.Main) {
        if (temperature < 10) return@launch
        val currentTime = System.currentTimeMillis()
        val secondsBeforeLastMessage =
            TimeUnit.MILLISECONDS.toSeconds(currentTime - lastShownTempMessage)
        Timber.d("secondsBeforeLastMessage $secondsBeforeLastMessage")
        if (secondsBeforeLastMessage < 25 && temperature < overHeatingThreshold) return@launch
        lastShownTempMessage = currentTime
        val message = TemperatureUtil.getTempMessage(
            this@CameraActivity,
            temperature,
            driverLiteThreshold,
            overHeatingThreshold
        )
        binding.messageTxt.text = message
        binding.messageTxtCard.visible()
        delay(1500)
        binding.messageTxtCard.gone()
        updateTemperatureWarning(temperature)
    }

    override fun onDestroy() {
        resetGlobalParams()
        viewModel.stopTempObserving()
        nayanCamRepository.getTemperatureLiveData().removeObserver(temperatureObserver)
        viewModel.resetNightMode()
        if (::rotationLiveData.isInitialized) rotationLiveData.onInactive()
        tiltHandler.removeCallbacks(tiltRunnable)
        super.onDestroy()
    }

    private val resolutionForResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            if (activityResult.resultCode == RESULT_OK) {
                Timber.i("User agreed to make required location settings changes.")
                viewModel.startReceivingLocationUpdates()
            } else {
                Timber.i("User chose not to make required location settings changes.")
                AlertDialog.Builder(this)
                    .setTitle("Please enable Location!!")
                    .setMessage("Camera mode can not function properly without Location enabled.")
                    .setPositiveButton(
                        "Close Camera"
                    ) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }
}

interface OnHoverClickListener {
    fun onHoverClick()
}