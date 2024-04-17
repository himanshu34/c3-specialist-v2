package com.nayan.nayancamv2.scout

import android.annotation.SuppressLint
import android.media.ImageReader
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.CameraConnectionFragmentBinding
import com.nayan.nayancamv2.BaseActivity
import com.nayan.nayancamv2.BaseFragment
import com.nayan.nayancamv2.helper.CameraPreviewScoutListener
import com.nayan.nayancamv2.ui.cam.NayanCamViewModel
import com.nayan.nayancamv2.viewBinding
import timber.log.Timber

class CameraConnectionFragment : BaseFragment(R.layout.camera_connection_fragment) {

    private val binding by viewBinding(CameraConnectionFragmentBinding::bind)
    private lateinit var cameraPreviewScoutListener: CameraPreviewScoutListener
    private lateinit var imageAvailableListener: ImageReader.OnImageAvailableListener

    private val camViewModel: NayanCamViewModel by activityViewModels {
        val baseActivity = requireActivity() as BaseActivity
        baseActivity.viewModelFactory
    }

    companion object {
        @JvmStatic
        fun newInstance(
            cameraPreviewScoutListener: CameraPreviewScoutListener,
            imageAvailableListener: ImageReader.OnImageAvailableListener
        ) = CameraConnectionFragment().apply {
            this.cameraPreviewScoutListener = cameraPreviewScoutListener
            this.imageAvailableListener = imageAvailableListener
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onStart() {
        super.onStart()
        Timber.d("onStart()")
        camViewModel.attachCameraPreview(
            cameraPreview = binding.textureView,
            baseCameraPreviewListener = cameraPreviewScoutListener,
            imageAvailableListener = imageAvailableListener
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycle.addObserver(camViewModel)
    }

    override fun onDestroyView() {
        lifecycle.removeObserver(camViewModel)
        super.onDestroyView()
    }
}