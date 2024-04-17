package com.nayan.nayancamv2.scout

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.IntentSender
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.LocationFailureState
import co.nayan.c3v2.core.models.LocationSuccessState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import co.nayan.nayancamv2.databinding.ActivityDriverScoutBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.BaseActivity
import com.nayan.nayancamv2.adjustConstraints
import com.nayan.nayancamv2.getDefaultUserLocation
import com.nayan.nayancamv2.helper.CameraPreviewScoutListener
import com.nayan.nayancamv2.helper.GlobalParams.scoutDismissStatus
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber

class DriverScoutModeActivity : BaseActivity() {

    private val locationRequestHandler by lazy { Handler(Looper.getMainLooper()) }
    private val locationRequestRunnable = Runnable { nayanCamRepository.checkForLocationRequest() }

    private val binding: ActivityDriverScoutBinding by viewBinding(ActivityDriverScoutBinding::inflate)
    private lateinit var eventsAdapter: EventsAdapter
    private lateinit var cameraPreviewScoutListener: CameraPreviewScoutListener

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        cameraPreviewScoutListener = CameraPreviewScoutListener()
        eventsAdapter = EventsAdapter()
        nayanCamModuleInteractor.getDriverEvents().also { data ->
            val orientation = this.resources.configuration.orientation
            val isLandscapeMode = (orientation == Configuration.ORIENTATION_LANDSCAPE)
            if (data.isNullOrEmpty()) {
                binding.rvEvents.gone()
                if (isLandscapeMode) binding.cameraLayout.adjustConstraints(binding.rootView)
            } else {
                data.sortByDescending { events -> events.score?.toInt() ?: run { 0 } }
                binding.rvEvents.apply {
                    this.layoutManager = layoutManager
                    adapter = eventsAdapter
                    eventsAdapter.addAll(data)
                    eventsAdapter.updateOrientation(isLandscape = isLandscapeMode)
                    visible()
                }
            }
        }

        val fragment = CameraConnectionFragment.newInstance(
            cameraPreviewScoutListener = cameraPreviewScoutListener,
            imageAvailableListener = cameraPreviewScoutListener
        )
        nayanCamComponent.inject(fragment)
        supportFragmentManager.beginTransaction().replace(R.id.cameraContainer, fragment).commit()
        nayanCamRepository.getLocationState().observe(this, locationUpdateObserver)

        if (scoutDismissStatus.value == InitialState) scoutDismissStatus.postValue(ProgressState)
        scoutDismissStatus.observe(this) { changeScoutState(it) }

        binding.scoutBtn.setOnClickListener {
            if (binding.scoutBtn.text == getString(R.string.click)) {
                // Capture scout image
                userLocation?.let {
                    storageUtil.createNewScoutModeFile(this, it)?.also { file ->
                        cameraPreviewScoutListener.captureStillImage(file)

                        val scoutPreviewFragment = ScoutModePreviewFragment.newInstance(file)
                        nayanCamComponent.inject(scoutPreviewFragment)
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.cameraContainer, scoutPreviewFragment)
                            .commit()
                    }
                }
            } else {
                // Submit image to backend with tags
            }
        }
    }

    private val locationUpdateObserver: Observer<ActivityState> = Observer {
        when (it) {
            is LocationSuccessState -> {
                it.location?.let { loc ->
                    Timber.e("Location Received with ${loc.time}")
                    nayanCamModuleInteractor.saveLastLocation(loc.latitude, loc.longitude)
                    locationAvailable(loc)
                }
            }

            is LocationFailureState -> {
                it.exception?.let { exception ->
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
                        .setTitle("Please enable Location!!")
                        .setMessage("Camera mode can not function properly without Location enabled.")
                        .setPositiveButton("Close Camera") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private fun locationAvailable(location: Location) = lifecycleScope.launch {
        userLocation = getDefaultUserLocation(location)
    }

    private fun changeScoutState(state: ActivityState) = lifecycleScope.launch {
        if (state == FinishedState) {
            scoutDismissStatus.postValue(InitialState)
            finishAffinity()
        }
    }

    private val resolutionForResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            if (activityResult.resultCode == RESULT_OK) {
                Timber.i("User agreed to make required location settings changes.")
                nayanCamRepository.startLocationUpdate()
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

    override fun onResume() {
        super.onResume()
        locationRequestHandler.post(locationRequestRunnable)
    }

    override fun onPause() {
        super.onPause()
        nayanCamRepository.stopLocationUpdate()
        locationRequestHandler.removeCallbacksAndMessages(null)
    }
}