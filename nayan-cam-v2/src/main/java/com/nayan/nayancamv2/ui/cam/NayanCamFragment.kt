package com.nayan.nayancamv2.ui.cam

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.CameraConfig
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.NayanCamFragmentBinding
import com.nayan.nayancamv2.*
import com.nayan.nayancamv2.helper.CameraPreviewListener
import com.nayan.nayancamv2.helper.GlobalParams.appHasLocationUpdates
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.hovermode.BackgroundCameraService
import com.nayan.nayancamv2.hovermode.OverheatingHoverService
import com.nayan.nayancamv2.hovermode.PortraitHoverService
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.ui.DoubleClickListener
import com.nayan.nayancamv2.util.RecordingEventState.AI_SCANNING
import com.nayan.nayancamv2.util.RecordingEventState.ORIENTATION_ERROR
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_CORRUPTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_FAILED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_STARTED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_SUCCESSFUL
import com.nayan.nayancamv2.util.isServiceRunning
import com.nayan.nayancamv2.util.showRecordingAlert
import com.nayan.nayancamv2.util.updateRecordingEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class NayanCamFragment : BaseFragment(R.layout.nayan_cam_fragment) {

    @Inject
    lateinit var mNayanCamModuleInteractor: NayanCamModuleInteractor

    @Inject
    lateinit var cameraConfig: CameraConfig

    private val binding by viewBinding(NayanCamFragmentBinding::bind)
    private val fadeInOutAnimation by lazy { requireContext().createFadeInOutAnimation() }
    private lateinit var onHoverClickListener: OnHoverClickListener
    private lateinit var cameraPreviewListener: CameraPreviewListener
    private lateinit var imageAvailableListener: ImageReader.OnImageAvailableListener

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment NayanCam2Fragment.
         */
        @JvmStatic
        fun newInstance(
            cameraPreviewListener: CameraPreviewListener,
            imageAvailableListener: ImageReader.OnImageAvailableListener
        ) = NayanCamFragment().apply {
            this.cameraPreviewListener = cameraPreviewListener
            this.imageAvailableListener = imageAvailableListener
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is CameraActivity) onHoverClickListener = context
    }

    private val camViewModel: NayanCamViewModel by activityViewModels {
        val baseActivity = requireActivity() as BaseActivity
        baseActivity.viewModelFactory
    }

    override fun onResume() {
        super.onResume()
        requireActivity().apply {
            if (isServiceRunning<BackgroundCameraService>())
                stopService(Intent(this, BackgroundCameraService::class.java))
            if (isServiceRunning<PortraitHoverService>())
                stopService(Intent(this, PortraitHoverService::class.java))
            if (isServiceRunning<OverheatingHoverService>())
                stopService(Intent(this, OverheatingHoverService::class.java))
        }
        camViewModel.resetNightMode()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycle.addObserver(camViewModel)
        switchCameraUI()

        if (::cameraConfig.isInitialized && cameraConfig.shouldShowSettingsOnPreview == true)
            binding.ivSettings.visible()
        else binding.ivSettings.gone()

        if (::mNayanCamModuleInteractor.isInitialized && mNayanCamModuleInteractor.isOnlyPreviewMode())
            setUpUIForPreviewMode()

        setOnClickListeners()
        setUpObservers()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setOnClickListeners() = lifecycleScope.launch {
        binding.ivService.setOnClickListener {
            onHoverClickListener.onHoverClick()
        }

        binding.ivSettings.setOnClickListener {
            requireActivity().finish()
            if (::mNayanCamModuleInteractor.isInitialized)
                mNayanCamModuleInteractor.startSettingsActivity(requireContext())
        }

        binding.bluetoothClicker.setOnClickListener {
            Timber.e("bluetoothClicker")
            Handler(Looper.getMainLooper()).post {
                if (context is CameraActivity) {
                    camViewModel.recordVideo(
                        userLocation = userLocation,
                        requireContext(),
                        labelName = "Manual/Tap",
                        isManual = true,
                        onLocationError = { requireActivity().showToast(getString(R.string.location_error)) }
                    )
                } else {
                    camViewModel.recordVideo(
                        null,
                        requireContext(),
                        labelName = "Manual/Tap",
                        isManual = true,
                        onLocationError = { requireActivity().showToast(getString(R.string.location_error)) }
                    )
                }
            }
        }

        binding.trackingOverlay.setOnTouchListener(object : DoubleClickListener() {
            override fun onSingleClick(v: View?) {
                Timber.e("forceRecord onSingleClick")
            }

            override fun onDoubleClick(v: View?) {
                Timber.e("forceRecord onDoubleClick receivingLocationUpdates: $appHasLocationUpdates")
                lifecycleScope.launch {
                    if (appHasLocationUpdates) {
                        camViewModel.recordVideo(
                            userLocation = userLocation,
                            requireContext(),
                            labelName = "Manual/Tap",
                            isManual = true,
                            onLocationError = { requireContext().showToast(getString(R.string.location_error)) }
                        )
                    }
                }
            }
        })
    }

    private fun setUpObservers() = lifecycleScope.launch {
        camViewModel.setConfig()
        camViewModel.recordingState.collect(recordingStateObserver)
    }

    private fun setUpUIForPreviewMode() = lifecycleScope.launch {
        binding.ivSettings.gone()
        binding.ivRotateCamera.gone()
        binding.ivService.gone()
    }

    private fun switchCameraUI() = lifecycleScope.launch {
        if (camViewModel.getCameraConfig().isSwitchCameraEnabled == true)
            binding.ivRotateCamera.visible()
        else binding.ivRotateCamera.gone()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onStart() {
        super.onStart()
        Timber.d("onStart()")
        camViewModel.attachCameraPreview(
            cameraPreview = binding.texture,
            baseCameraPreviewListener = cameraPreviewListener,
            imageAvailableListener = imageAvailableListener
        )
    }

    private val recordingStateObserver = FlowCollector<RecordingState?> { recordingState ->
        recordingState?.let {
            val volumeLevel = camViewModel.getStorageUtil().getVolumeLevel()
            when (it.recordingState) {
                RECORDING_STARTED -> binding.recordingEvent.showRecordingAlert(
                    fadeInOutAnimation,
                    R.raw.recording,
                    it.recordingState,
                    volumeLevel
                )

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

                AI_SCANNING -> binding.recordingEvent.updateRecordingEvent(
                    fadeInOutAnimation,
                    R.drawable.bg_ai_scanning
                )

                else -> binding.recordingEvent.updateRecordingEvent(
                    fadeInOutAnimation,
                    null
                )
            }

            if (it.message.isNotEmpty() && it.recordingState != ORIENTATION_ERROR)
                requireActivity().showToast(it.message)
        }
    }

    override fun onDestroyView() {
        lifecycle.removeObserver(camViewModel)
        super.onDestroyView()
    }
}