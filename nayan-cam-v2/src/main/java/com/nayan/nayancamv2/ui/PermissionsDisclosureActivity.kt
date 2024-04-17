package com.nayan.nayancamv2.ui

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CAMERA
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import co.nayan.c3v2.core.hasPermission
import co.nayan.c3v2.core.showToast
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.PermissionDisclosureActivityBinding
import com.google.android.material.snackbar.Snackbar
import com.nayan.nayancamv2.BaseActivity
import com.nayan.nayancamv2.NayanCamActivity
import com.nayan.nayancamv2.extcam.common.ExtCamConnectionActivity
import com.nayan.nayancamv2.hovermode.HoverPermissionCallback
import com.nayan.nayancamv2.isHoverPermissionGranted
import com.nayan.nayancamv2.launchHoverService
import com.nayan.nayancamv2.requestHoverPermission
import com.nayan.nayancamv2.viewBinding

class PermissionsDisclosureActivity : BaseActivity() {
    private var destination: String = ""
    private val binding by viewBinding(PermissionDisclosureActivityBinding::inflate)
    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) requestCameraPermission()
            else showPermissionError(ACCESS_FINE_LOCATION)
        }

    private val requestBackgroundLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            when {
                granted -> requestCameraPermission()
                hasPermission(ACCESS_BACKGROUND_LOCATION) -> requestCameraPermission()
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        showPermissionError(ACCESS_BACKGROUND_LOCATION)
                }
            }
        }

    private val requestPermissionCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (!isHoverPermissionGranted())
                    requestHoverPermission(
                        nayanCamModuleInteractor.getDeviceModel(),
                        hoverPermissionCallback
                    )
                else launchCameraService()
            } else showPermissionError(CAMERA)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        destination = if (intent.hasExtra("destination")) intent.getStringExtra("destination")
            .toString() else ""
        binding.buttonDeny.setOnClickListener { finish() }
        binding.buttonAllow.setOnClickListener {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permissionApproved = ((hasPermission(ACCESS_FINE_LOCATION) ||
                    hasPermission(ACCESS_COARSE_LOCATION)) &&
                    hasPermission(ACCESS_BACKGROUND_LOCATION))
            if (permissionApproved) requestCameraPermission()
            else {
                requestBackgroundLocationPermission.launch(
                    arrayOf(
                        ACCESS_FINE_LOCATION,
                        ACCESS_COARSE_LOCATION,
                        ACCESS_BACKGROUND_LOCATION
                    )
                )
            }
        } else {
            val permissionApproved =
                (hasPermission(ACCESS_FINE_LOCATION) || hasPermission(ACCESS_COARSE_LOCATION))
            if (permissionApproved) requestCameraPermission()
            else {
                requestLocationPermission.launch(
                    arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
                )
            }
        }
    }

    private fun requestCameraPermission() {
        requestPermissionCamera.launch(CAMERA)
    }

    private fun launchCameraService() {
        when (destination) {
            "drone" -> {
                Intent(this, ExtCamConnectionActivity::class.java).apply {
                    putExtra("selected", "drone")
                    startActivity(this)
                }
                finish()
            }

            "dashcam" -> {
                Intent(this, ExtCamConnectionActivity::class.java).apply {
                    putExtra("selected", "dashcam")
                    startActivity(this)
                }
                finish()
            }

            else -> {
                if (storageUtil.isDefaultHoverMode()) {
                    sharedPrefManager.setLastHoverRestartCalled()
                    launchHoverService()
                } else {
                    startActivity(Intent(this, NayanCamActivity::class.java))
                    finish()
                }
            }
        }
    }

    private val hoverPermissionCallback = object : HoverPermissionCallback {
        override fun onPermissionGranted() {
            launchCameraService()
        }

        override fun onPermissionDenied(intent: Intent) {
            requestOverLayPermissionLauncher.launch(intent)
        }

        override fun onPermissionDeniedAdditional(intent: Intent) {
            AlertDialog.Builder(this@PermissionsDisclosureActivity)
                .setTitle("Please Enable the additional permissions")
                .setMessage("Hover mode can not function in background if you disable these permissions.")
                .setPositiveButton("Enable now!") { _, _ -> startActivity(intent) }
                .setCancelable(false)
                .show()
        }
    }

    private fun showPermissionError(permission: String) {
        val permissionDeniedExplanation =
            when (permission) {
                ACCESS_FINE_LOCATION -> R.string.fine_permission_denied_explanation
                ACCESS_BACKGROUND_LOCATION -> R.string.background_permission_denied_explanation
                else -> R.string.camera_permision_denied_explaination
            }
        Snackbar.make(
            binding.root,
            permissionDeniedExplanation,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.settings) {
            // Build intent that displays the App settings screen.
            val uri = Uri.fromParts("package", nayanCamModuleInteractor.getApplicationId(), null)
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = uri
            }
            startActivity(intent)
        }.show()
    }

    private val requestOverLayPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (isHoverPermissionGranted()) launchCameraService()
            else {
                showToast(getString(R.string.draw_over_other_app_))
                finish()
            }
        }
}