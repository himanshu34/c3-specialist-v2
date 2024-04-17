package com.nayan.nayancamv2

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import co.nayan.appsession.SessionManager
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.showToast
import co.nayan.nayancamv2.R
import com.google.android.gms.maps.model.LatLng
import com.nayan.nayancamv2.di.SessionInteractor
import com.nayan.nayancamv2.helper.GlobalParams.SPATIAL_PROXIMITY_THRESHOLD
import com.nayan.nayancamv2.helper.GlobalParams.SPATIAL_STICKINESS_CONSTANT
import com.nayan.nayancamv2.helper.GlobalParams.appHasLocationUpdates
import com.nayan.nayancamv2.helper.GlobalParams.hasValidSpeed
import com.nayan.nayancamv2.helper.GlobalParams.ifUserLocationFallsWithInSurge
import com.nayan.nayancamv2.helper.GlobalParams.ifUserRecordingOnBlackLines
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.hovermode.HoverPermissionCallback
import com.nayan.nayancamv2.impl.SyncWorkflowManagerImpl
import com.nayan.nayancamv2.model.UserLocationMeta
import com.nayan.nayancamv2.util.Constants
import com.nayan.nayancamv2.util.Constants.IS_FROM_HOVER
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_10
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.SURVEYOR_SPEED_MAX_THRESHOLD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber

class NayanCamActivity : CameraActivity() {

    private lateinit var sessionManager: SessionManager
    private var activityState: ActivityState? = null
    private var syncActivityState: ActivityState? = null
    private var mAllowedSurveys = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        viewModel.getSensorLiveData(this).observe(this) {
            cameraHelper.currentSensorMeta = it
        }

        lifecycleScope.launch(Dispatchers.Default) {
            spatialIndex = prepareSpatialTreeForAllSegments(storageUtil.getAllSegments())
        }

        syncWorkflowManagerImpl.subscribeSegmentsData().observe(this) {
            when (it) {
                is SyncWorkflowManagerImpl.SuccessSegmentState -> {
                    lifecycleScope.launch(Dispatchers.Default) {
                        spatialIndex = prepareSpatialTreeForAllSegments(it.segmentsList)
                    }
                }
            }
        }

        attendanceSyncManager.locationSyncLiveData.observe(this) { activityState = it }

        syncWorkflowManagerImpl.subscribe().observe(this) {
            syncActivityState = it
            when (it) {
                is SyncWorkflowManagerImpl.SyncWorkflowSuccessState -> {
                    lifecycleScope.launch(Dispatchers.Default) {
                        cameraProcessor.updateWorkFlowList(it.workFlowList)
                    }
                }
            }
        }
        if (nayanCamModuleInteractor.getSpatialProximityThreshold() > 0.0) SPATIAL_PROXIMITY_THRESHOLD =
            nayanCamModuleInteractor.getSpatialProximityThreshold() / Constants.RADIAN_TO_METER_DIVISOR_EARTH.toDouble()
        if (nayanCamModuleInteractor.getSpatialStickinessConstant() > 0.0) SPATIAL_STICKINESS_CONSTANT =
            nayanCamModuleInteractor.getSpatialStickinessConstant() / Constants.RADIAN_TO_METER_DIVISOR_EARTH.toDouble()
        mAllowedSurveys = nayanCamModuleInteractor.getAllowedSurveys()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.hasExtra(IS_FROM_HOVER) &&
                    intent.getBooleanExtra(IS_FROM_HOVER, false)
                ) nayanCamModuleInteractor.startDashboardActivity(this@NayanCamActivity, false)
                finish()
            }
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        if (appHasLocationUpdates && action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    Timber.d("dispatchKeyEvent :KEYCODE_VOLUME_UP Start manual recording ")
                    viewModel.recordVideo(
                        userLocation = userLocation,
                        this,
                        labelName = "Manual/Volume",
                        isManual = true,
                        onLocationError = { showToast(getString(R.string.location_error)) }
                    )
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    Timber.d("dispatchKeyEvent Start manual recording ")
                    viewModel.recordVideo(
                        userLocation = userLocation,
                        this,
                        labelName = "Manual/Volume",
                        isManual = true,
                        onLocationError = { showToast(getString(R.string.location_error)) }
                    )
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun startCameraService() {
        launchHoverService()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onHoverClick() {
        if (!isHoverPermissionGranted())
            requestHoverPermission(
                nayanCamModuleInteractor.getDeviceModel(),
                hoverPermissionCallback
            )
        else startCameraService()
    }

    private val hoverPermissionCallback = object : HoverPermissionCallback {
        override fun onPermissionGranted() {
            startCameraService()
        }

        override fun onPermissionDenied(intent: Intent) {
            requestOverLayPermissionLauncher.launch(intent)
        }

        override fun onPermissionDeniedAdditional(intent: Intent) {
            AlertDialog.Builder(this@NayanCamActivity)
                .setTitle("Please Enable the additional permissions")
                .setMessage("Hover mode can not function in background if you disable these permissions.")
                .setPositiveButton("Enable now!") { _, _ -> startActivity(intent) }
                .setCancelable(false)
                .show()
        }
    }

    private val requestOverLayPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (isHoverPermissionGranted()) startCameraService()
            else {
                showToast(getString(R.string.draw_over_other_app_))
                finish()
            }
        }

    override fun locationAvailable(location: Location) {
        lifecycleScope.launch {
            userLocation = getDefaultUserLocation(location)

            ifUserLocationFallsWithInSurge = if (nayanCamModuleInteractor.isSurveyor()) {
                val surgeLocations = nayanCamModuleInteractor.getSurgeLocations()
                val cityKmlBoundaries = nayanCamModuleInteractor.getCityKmlBoundaries()
                val point = LatLng(location.latitude, location.longitude)
                (funIfPointFallsInKmlBoundaries(point, cityKmlBoundaries) ||
                        funIfUserLocationFallsWithInSurge(point, surgeLocations))
            } else true

            hasValidSpeed =
                if (nayanCamModuleInteractor.isSurveyor() && ifUserLocationFallsWithInSurge)
                    location.speed <= SURVEYOR_SPEED_MAX_THRESHOLD
                else true

            onSurveyorWarningStatus(ifUserLocationFallsWithInSurge, hasValidSpeed)

            ifUserRecordingOnBlackLines = if (nayanCamModuleInteractor.isSurveyor() &&
                nayanCamModuleInteractor.getRecordingOnBlackLines().not()
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

            onDrivingOnBlackLines()

            val hasValidConditionsForSurveyor =
                (nayanCamModuleInteractor.isSurveyor() && ifUserLocationFallsWithInSurge)
            if (ifUserRecordingOnBlackLines.not() && hasValidSpeed
                && (hasValidConditionsForSurveyor || nayanCamModuleInteractor.isAIMode())
            ) {
                cameraHelper.currentLocationMeta = UserLocationMeta(
                    location.latitude,
                    location.longitude,
                    location.altitude,
                    location.speed.toString(),
                    "",
                    System.currentTimeMillis()
                )

                viewModel.addLocationToDatabase(location) { lastSyncTimeStamp ->
                    val diff = System.currentTimeMillis() - lastSyncTimeStamp
                    if ((activityState != ProgressState) && diff >= DELAYED_10) {
                        attendanceSyncManager.locationSyncLiveData.postValue(ProgressState)
                        startAttendanceSyncingRequest(false)
                    }
                }
            }

            startSyncWorkflow()
        }
    }


    private suspend fun startSyncWorkflow() = withContext(Dispatchers.IO) {
        userLocation?.let {
            Timber.d("location: ${it.latitude} -- ${it.longitude}, ${it.address}")
            val latLng = LatLng(it.latitude, it.longitude)
            if (syncActivityState != ProgressState) syncWorkflowManagerImpl.onLocationUpdate(latLng)
        }
    }

    override fun onStop() {
        super.onStop()
        startVideoUploadRequest(nayanCamModuleInteractor.isSurveyor())
    }
}