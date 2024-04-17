package com.nayan.nayancamv2.extcam.dashcam

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
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
import com.nayan.nayancamv2.extcam.common.ExtCamViewModel
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.extcam.common.IAIResultsHelper
import com.nayan.nayancamv2.extcam.media.NativeHelper
import com.nayan.nayancamv2.helper.GlobalParams.hasValidSpeed
import com.nayan.nayancamv2.helper.GlobalParams.ifUserLocationFallsWithInSurge
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.util.Constants.IS_FROM_HOVER
import com.nayan.nayancamv2.util.Constants.SHOULD_HOVER
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashcamStreamingActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var binding: LayoutExtStreamingBinding
    private val viewModel: ExtCamViewModel by viewModels { viewModelFactory }
    private val videoStreamDecoder by lazy { viewModel.videoStreamDecoder }
    private val iaiResultsHelper: IAIResultsHelper = AIResultsHelperImpl
    private var shouldLoadFromObservers = true
    private var shouldHover = false
    private val fadeInOutAnimation by lazy { createFadeInOutAnimation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        shouldHover = if (intent.hasExtra(SHOULD_HOVER))
            intent.getBooleanExtra(SHOULD_HOVER, false) else false
        shouldLoadFromObservers = false
        initTextureView()

        binding.activityMainRvRecognitions.apply {
            adapter = viewModel.aiResultsAdapter
        }

        binding.ivService.setOnClickListener {
            iaiResultsHelper.changeHoverSwitchState(AIResultsHelperImpl.SwitchToService(""))
            finishAffinity()
        }

        iaiResultsHelper.getRecordingLD().observe(this, recordingStateObserver)
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.hasExtra(IS_FROM_HOVER) &&
                    intent.getBooleanExtra(IS_FROM_HOVER, false)
                ) iaiResultsHelper.changeHoverSwitchState(AIResultsHelperImpl.SwitchToService(""))
                finishAffinity()
            }
        })
    }

    private val hoverToActivityObserver = Observer<AIResultsHelperImpl.HoverSwitchState> {
        when (it) {
            is AIResultsHelperImpl.SwitchToActivity -> {}
            is AIResultsHelperImpl.SwitchToService -> {
                finishAffinity()
            }
        }
    }

    private fun initTextureView() {
        binding.livestreamPreviewTtv.apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surfaceTexture: SurfaceTexture,
                    p1: Int,
                    p2: Int
                ) {
                    startAIProcessingService()
                    NativeHelper.getInstance().init()
                    videoStreamDecoder.init(this@DashcamStreamingActivity, Surface(surfaceTexture))
                    videoStreamDecoder.resume()
                    val switchStatus = if (shouldHover) AIResultsHelperImpl.SwitchToService("")
                    else AIResultsHelperImpl.SwitchToActivity("")
                    iaiResultsHelper.changeHoverSwitchState(switchStatus)
                    iaiResultsHelper.switchHoverActivityLiveData()
                        .observe(this@DashcamStreamingActivity, hoverToActivityObserver)
                }

                override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

                }

                override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                    videoStreamDecoder.stop()
                    return false
                }

                override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

                }
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

        if (it.message.isNotBlank()) Toast.makeText(
            this@DashcamStreamingActivity,
            it.message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startAIProcessingService() {
        Intent(this@DashcamStreamingActivity, ExternalCameraProcessingService::class.java).apply {
            if (isServiceRunning<ExternalCameraProcessingService>()) stopService(this)
            this.putExtra("camType", "dashcam")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(this)
            else startService(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeObservers()
    }

    private fun removeObservers() {
        iaiResultsHelper.getRecordingLD().removeObservers(this)
        iaiResultsHelper.subscribe().removeObservers(this)
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