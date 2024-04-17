package com.nayan.nayancamv2.extcam.drone

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.appsession.SessionManager
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.LayoutExtStreamingBinding
import com.nayan.nayancamv2.AIResultsHelperImpl
import com.nayan.nayancamv2.BaseActivity
import com.nayan.nayancamv2.createFadeInOutAnimation
import com.nayan.nayancamv2.di.SessionInteractor
import com.nayan.nayancamv2.extcam.common.BlackFrameUtil
import com.nayan.nayancamv2.extcam.common.DJIXNayan
import com.nayan.nayancamv2.extcam.common.ExtCamViewModel
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.extcam.common.IAIResultsHelper
import com.nayan.nayancamv2.extcam.media.NativeHelper
import com.nayan.nayancamv2.helper.GlobalParams.hasValidSpeed
import com.nayan.nayancamv2.helper.GlobalParams.ifUserLocationFallsWithInSurge
import com.nayan.nayancamv2.model.DroneLocation
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.util.Constants.IS_FROM_HOVER
import com.nayan.nayancamv2.util.RecordingEventState.AI_SCANNING
import com.nayan.nayancamv2.util.RecordingEventState.DRIVING_FAST
import com.nayan.nayancamv2.util.RecordingEventState.NOT_IN_SURGE
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_CORRUPTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_FAILED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_STARTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_SUCCESSFUL
import com.nayan.nayancamv2.util.isServiceRunning
import com.nayan.nayancamv2.util.onSurveyorWarningStatus
import com.nayan.nayancamv2.util.showRecordingAlert
import com.nayan.nayancamv2.util.updateRecordingEvent
import dji.common.battery.BatteryState
import dji.sdk.camera.VideoFeeder
import dji.sdk.camera.VideoFeeder.VideoDataListener
import dji.sdk.codec.DJICodecManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class DroneStreamingActivity : BaseActivity(), VideoDataListener, BatteryState.Callback {

    private lateinit var sessionManager: SessionManager
    private var codec: DJICodecManager? = null
    private lateinit var binding: LayoutExtStreamingBinding
    private val tag = this.javaClass.simpleName
    private val viewModel: ExtCamViewModel by viewModels { viewModelFactory }
    private val iaiResultsHelper: IAIResultsHelper = AIResultsHelperImpl
    private lateinit var syncingAlertDialog: AlertDialog
    private val fadeInOutAnimation by lazy { createFadeInOutAnimation() }
    private val batteryFlow = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = LayoutExtStreamingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPrefs =
            getSharedPreferences(SessionManager.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        if (nayanCamModuleInteractor is SessionInteractor) {
            sessionManager = SessionManager(
                sharedPrefs,
                this,
                null,
                null,
                (nayanCamModuleInteractor as SessionInteractor).getSessionRepositoryInterface()
            ).apply {
                shouldCheckUserInteraction = false
                setMetaData(null, null, null, nayanCamModuleInteractor.getCurrentRole())
            }
        }

        // Although finish will trigger onDestroy() is called, but it is not called before OnCreate of new activity.
        codec?.let {
            it.cleanSurface()
            it.destroyCodec()
        }
        codec = null
        NativeHelper.getInstance().setCheckForBlackFrames(true)
        initTextureView()
        showSyncingAlert()
        binding.activityMainRvRecognitions.apply {
            adapter = viewModel.aiResultsAdapter
        }
        binding.ivService.setOnClickListener {
            iaiResultsHelper.changeHoverSwitchState(AIResultsHelperImpl.SwitchToService(""))
            finishAffinity()
        }
        iaiResultsHelper.getRecordingLD().observe(this, recordingStateObserver)
        BlackFrameUtil.isBlackFrame.observe(this@DroneStreamingActivity) {
            if (it.not()) {
                syncingAlertDialog.dismiss()
                VideoFeeder.getInstance().primaryVideoFeed.removeVideoDataListener(
                    this@DroneStreamingActivity
                )
                codec?.cleanSurface()
                codec?.destroyCodec()
                codec = null
                binding.dummyTtv.gone()
                openDriverSurgeInPIP()
            }
        }
        DJIXNayan.productInstance?.battery?.setStateCallback(this@DroneStreamingActivity)
        binding.batteryGroup.visible()
        lifecycleScope.launch {
            batteryFlow.collect {
                binding.battery.setImageLevel(it)
                binding.batteryTv.text = it.toString()
            }
        }

        iaiResultsHelper.subscribe().observe(this) { recognizedObject ->
            recognizedObject?.let {
                lifecycleScope.launch {
                    if (it.aiModelIndex == 0) {
                        val aiResultMap: HashMap<Int, Pair<Bitmap, String>> = HashMap()
                        viewModel.aiResultList.add(aiResultMap)
                    }
                    viewModel.aiResultList[viewModel.aiResultList.size - 1][it.aiModelIndex] =
                        it.recognizedObject

                    withContext(Dispatchers.Main) {
                        viewModel.aiResultsAdapter.addAll(viewModel.aiResultList)
                        binding.activityMainRvRecognitions.smoothScrollToPosition(viewModel.aiResultsAdapter.itemCount)
                    }
                }
            }
        }

        if (nayanCamModuleInteractor.getIsDroneLocationOnly())
            iaiResultsHelper.getDroneLocationState().observe(this@DroneStreamingActivity) {
                when (it) {
                    DroneLocation.Available -> {
                        binding.droneLocationCard.gone()
                    }

                    DroneLocation.Error -> {
                        binding.tvDroneLocation.text =
                            resources.getText(R.string.drone_location_warning)
                        binding.droneLocationCard.visible()
                    }

                    DroneLocation.Init -> {}
                    null -> {}
                }
            }


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.hasExtra(IS_FROM_HOVER) &&
                    intent.getBooleanExtra(IS_FROM_HOVER, false)
                ) iaiResultsHelper.changeHoverSwitchState(AIResultsHelperImpl.SwitchToService(""))
                finishAffinity()
            }
        })
    }

    private fun showSyncingAlert() {
        syncingAlertDialog =
            AlertDialog.Builder(this@DroneStreamingActivity).setTitle("Please Wait")
                .setMessage("Syncing Drone Frames")
                .setCancelable(true)
                .show()
    }

    private val hoverToActivityObserver = Observer<AIResultsHelperImpl.HoverSwitchState> {
        when (it) {
            is AIResultsHelperImpl.SwitchToActivity -> {}
            is AIResultsHelperImpl.SwitchToService -> {
                finishAffinity()
            }

        }
    }

    private fun openDriverSurgeInPIP() = lifecycleScope.launch {
        delay(200)
        if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            nayanCamModuleInteractor.startSurgeMapActivity(
                this@DroneStreamingActivity,
                comingFrom = "External",
                mode = "pip"
            )
            iaiResultsHelper.getMutablePIPLD().postValue(AIResultsHelperImpl.SwitchToPIP("drone"))
        }
    }

    private fun initTextureView() {
        binding.livestreamPreviewTtv.surfaceTextureListener = object :
            TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Timber.tag(tag).d("real onSurfaceTextureAvailable")
                //For M300RTK, you need to actively request an I frame.
                startAIProcessingService()
                NativeHelper.getInstance().init()
                viewModel.videoStreamDecoder.shouldGetFrameData = true
                viewModel.videoStreamDecoder.init(applicationContext, Surface(surface))
                viewModel.videoStreamDecoder.resume()
                iaiResultsHelper.changeHoverSwitchState(AIResultsHelperImpl.SwitchToActivity(""))
                iaiResultsHelper.switchHoverActivityLiveData()
                    .observe(this@DroneStreamingActivity, hoverToActivityObserver)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                viewModel.videoStreamDecoder.stop()
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }


        binding.dummyTtv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                codec = DJICodecManager(this@DroneStreamingActivity, surface, width, height)
                codec?.resetKeyFrame()
                VideoFeeder.getInstance().primaryVideoFeed.addVideoDataListener(
                    this@DroneStreamingActivity
                )
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                codec?.cleanSurface()
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
            }

        }
    }


    private val recordingStateObserver = Observer<RecordingState> {
        lifecycleScope.launch {
            val volumeLevel = storageUtil.getVolumeLevel()
            when (it.recordingState) {
                RECORDING_STARTED -> {
                    if (binding.droneLocationCard.isVisible)
                        binding.droneLocationCard.gone()
                    binding.recordingEvent.showRecordingAlert(
                        fadeInOutAnimation,
                        R.raw.recording,
                        it.recordingState,
                        volumeLevel
                    )
                }

                RECORDING_SUCCESSFUL -> binding.recordingEvent.showRecordingAlert(
                    fadeInOutAnimation,
                    R.raw.recorded,
                    it.recordingState,
                    volumeLevel
                )

                RECORDING_CORRUPTED -> binding.recordingEvent.showRecordingAlert(
                    fadeInOutAnimation,
                    R.raw.camera_error_alert,
                    it.recordingState,
                    volumeLevel
                )

                RECORDING_FAILED -> binding.recordingEvent.showRecordingAlert(
                    fadeInOutAnimation,
                    R.raw.camera_error_alert,
                    it.recordingState,
                    volumeLevel
                )

                AI_SCANNING -> {
                    if (binding.droneLocationCard.isVisible)
                        binding.droneLocationCard.gone()
                    binding.recordingEvent.updateRecordingEvent(
                        fadeInOutAnimation,
                        R.drawable.bg_ai_scanning
                    )
                }

                DRIVING_FAST, NOT_IN_SURGE -> onSurveyorWarningStatus(
                    ifUserLocationFallsWithInSurge,
                    hasValidSpeed
                )

                else -> binding.recordingEvent.updateRecordingEvent(fadeInOutAnimation, null)
            }
        }
        if (it.message.isNotBlank()) Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
    }

    private fun startAIProcessingService() {
        Intent(this@DroneStreamingActivity, ExternalCameraProcessingService::class.java).apply {
            if (isServiceRunning<ExternalCameraProcessingService>()) stopService(this)
            this.putExtra("camType", "drone")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(this)
            else startService(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeObservers()
        BlackFrameUtil.reset()
        iaiResultsHelper.getMutablePIPLD().postValue(AIResultsHelperImpl.DismissPIP(""))
    }

    private fun removeObservers() {
        iaiResultsHelper.getRecordingLD().removeObservers(this)
        iaiResultsHelper.subscribe().removeObservers(this)
    }

    override fun onPause() {
        VideoFeeder.getInstance().primaryVideoFeed.removeVideoDataListener(this)
        super.onPause()
    }


    override fun onReceive(videoBuffer: ByteArray?, size: Int) {
        codec?.sendDataToDecoder(videoBuffer, size)
    }

    override fun onUpdate(batteryState: BatteryState?) {
        lifecycleScope.launch {
            batteryFlow.emit(batteryState?.chargeRemainingInPercent ?: 0)
        }
    }

    private fun onSurveyorWarningStatus(
        ifUserLocationFallsWithInSurge: Boolean,
        isValidSpeed: Boolean
    ) = lifecycleScope.launch(Dispatchers.Main) {
        if (nayanCamModuleInteractor.isSurveyor()) {
            binding.tvDroneLocation.onSurveyorWarningStatus(
                ifUserLocationFallsWithInSurge,
                isValidSpeed,
                storageUtil.getVolumeLevel()
            ) {
                if (it) binding.droneLocationCard.visible()
                else binding.droneLocationCard.gone()
            }
        } else binding.droneLocationCard.gone()
    }

}