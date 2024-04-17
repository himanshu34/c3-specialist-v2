package co.nayan.c3specialist_v2.home.roles.driver

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.config.LearningVideosCategory.CURRENT_ROLE
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.DriverHomeFragmentBinding
import co.nayan.c3specialist_v2.driverworksummary.DriverWorkSummaryActivity
import co.nayan.c3specialist_v2.getTimeAgo
import co.nayan.c3specialist_v2.profile.ProfileFragment
import co.nayan.c3specialist_v2.videogallery.LearningVideosPlaylistActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.Role.DRIVER
import co.nayan.c3v2.core.hasNetwork
import co.nayan.c3v2.core.hasPermission
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.location.LocationManagerImpl
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.LocationFailureState
import co.nayan.c3v2.core.models.LocationSuccessState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.c3_module.responses.DriverStatsResponse
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import co.nayan.tutorial.utils.LearningVideosContractInput
import co.nayan.tutorial.utils.LearningVideosResultCallback
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.ai.AIModelManager
import com.nayan.nayancamv2.extcam.common.ExtCamConnectionActivity
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.getBatteryLevel
import com.nayan.nayancamv2.getBatteryTemperature
import com.nayan.nayancamv2.helper.GlobalParams.syncWorkflowResponse
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.hovermode.BackgroundCameraService
import com.nayan.nayancamv2.impl.SyncWorkflowManagerImpl
import com.nayan.nayancamv2.isHoverPermissionGranted
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.modeldownloader.DownloadErrorDialogFragment
import com.nayan.nayancamv2.startSyncingAIModels
import com.nayan.nayancamv2.startVideoUploadRequest
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.tapToCall
import com.nayan.nayancamv2.ui.PermissionsDisclosureActivity
import com.nayan.nayancamv2.util.TrackingUtility
import com.nayan.nayancamv2.util.isServiceRunning
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DriverHomeFragment : BaseFragment(R.layout.driver_home_fragment) {

    @Inject
    lateinit var errorUtils: ErrorUtils

    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var storageUtil: StorageUtil

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor

    private val driverHomeViewModel: DriverHomeViewModel by viewModels()
    private val binding by viewBinding(DriverHomeFragmentBinding::bind)

    @Inject
    lateinit var locationManagerImpl: LocationManagerImpl

    private var syncActivityState: ActivityState? = null
    private lateinit var wifiErrorSnackbar: Snackbar

    private val requestNearbyWifiPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) checkAndLaunchDashcam()
            else showWifiError()
        }

    private fun showWifiError() {
        if (::wifiErrorSnackbar.isInitialized.not()) {
            wifiErrorSnackbar =
                Snackbar.make(binding.root, R.string.scan_wifi_denied, Snackbar.LENGTH_INDEFINITE)
                    .apply {
                        setAction(co.nayan.nayancamv2.R.string.settings) {
                            // Build intent that displays the App settings screen.
                            val intent = Intent().apply {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                data = Uri.fromParts(
                                    "package",
                                    nayanCamModuleInteractor.getApplicationId(),
                                    null
                                )
                            }
                            startActivity(intent)
                        }
                    }
        }
        wifiErrorSnackbar.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPrefManager = SharedPrefManager(requireContext())
        storageUtil = StorageUtil(requireContext(), sharedPrefManager, nayanCamModuleInteractor)
        binding.isSurveyor = nayanCamModuleInteractor.isSurveyor()
        binding.userEmailTxt.text = driverHomeViewModel.getUserEmail()
        binding.homeMessageTxt.text = String.format(
            getString(R.string.driver_home_screen_message), driverHomeViewModel.getUserName()
        )

        val timeInMilliseconds = sharedPrefManager.getGraphHopperSyncTimeStamp()
        val timeAgo = if (timeInMilliseconds > 0) timeInMilliseconds.getTimeAgo() else null
        binding.segmentsLastUpdatedAtTxt.text =
            getString(R.string.last_segments_updated_at).format(timeAgo ?: "--")

        val locationSyncTimeInMilliseconds = sharedPrefManager.getLocationSyncTimeStamp()
        val locationSyncTimeAgo = if (locationSyncTimeInMilliseconds > 0)
            locationSyncTimeInMilliseconds.getTimeAgo() else null
        binding.locationLastUpdatedAtTxt.text =
            getString(R.string.last_location_updated_at).format(locationSyncTimeAgo ?: "--")

        driverHomeViewModel.state.observe(viewLifecycleOwner, stateObserver)
        driverHomeViewModel.stats.observe(viewLifecycleOwner, userStatsObserver)
        setUpViews()
        setUpClicks()

        binding.pullToRefresh.setOnRefreshListener {
            updateOfflineFileCount()
            driverHomeViewModel.fetchUserStats()
        }

        if (activity is DashboardActivity) {
            (activity as DashboardActivity).updateHomeBackground(CurrentRole.DRIVER)
        }

        syncWorkflowResponse.observe(viewLifecycleOwner) {
            syncActivityState = it
            when (it) {
                ProgressState -> {
                    binding.aiProgressContainer.visible()
                }

                is AIModelManager.FailState -> {
                    binding.aiProgressContainer.gone()
                    binding.startRecordingContainer.disabled()
                    Firebase.crashlytics.log(it.exception.message.toString())
//                    if (it.exception.message?.contains("Model checksum value mismatched error") == true)
                    showCallSupportDialog()
                }

                is AIModelManager.DownloadFinishedState, is SyncWorkflowManagerImpl.SyncWorkflowFinishedState -> {
                    binding.aiProgressContainer.gone()
                    binding.startRecordingContainer.enabled()
                }
            }
        }

        driverHomeViewModel.getEvents()
        if (::locationManagerImpl.isInitialized) {
            locationManagerImpl.checkForLocationRequest()
            locationManagerImpl.subscribeLocation()
                .observe(viewLifecycleOwner, locationUpdateObserver)
        }
    }

    private fun setUpViews() {
        when (Build.VERSION.SDK_INT) {
            in Build.VERSION_CODES.BASE..Build.VERSION_CODES.O -> {
                binding.dashcamContainer.gone()
                binding.droneContainer.gone()
            }

            in Build.VERSION_CODES.O..Build.VERSION_CODES.P -> {
                binding.droneContainer.gone()
            }

            Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                binding.droneContainer.gone()
            }
        }
    }

    private var isLocationFailedDialogShown = false
    private val locationUpdateObserver = Observer<ActivityState> {
        if (view != null) {
            when (it) {
                is LocationSuccessState -> {
                    it.location?.let { loc ->
                        userLocation = UserLocation(loc.latitude, loc.longitude)
                        val latLng = LatLng(loc.latitude, loc.longitude)
                        driverHomeViewModel.saveLastLocation(latLng)
                        val roles = userRepository.getUserRoles()
                        if (roles.contains(DRIVER) && (syncActivityState != ProgressState))
                            requireContext().startSyncingAIModels(loc)
                        stopReceivingLocationUpdates()
                    }
                }

                is LocationFailureState -> {
                    it.exception?.let { exception ->
                        if (exception is ResolvableApiException) {
                            try {
                                if (!isLocationFailedDialogShown) {
                                    resolutionForResult.launch(
                                        IntentSenderRequest.Builder(exception.resolution).build()
                                    )
                                    isLocationFailedDialogShown = true
                                } else {
                                    Snackbar.make(
                                        binding.root,
                                        getString(co.nayan.nayancamv2.R.string.please_enable_location),
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (sendEx: IntentSender.SendIntentException) {
                                Firebase.crashlytics.recordException(sendEx)
                                Timber.e(sendEx)
                            }
                        }
                    } ?: run {
                        val permissions = arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)
                        requestPermissionLauncher.launch(permissions)
                    }
                }
            }
        }
    }

    private fun stopReceivingLocationUpdates() {
        if (::locationManagerImpl.isInitialized)
            locationManagerImpl.stopReceivingLocationUpdate()
    }

    private val userStatsObserver: Observer<DriverStatsResponse?> = Observer {
        val stats = it?.stats
        //amountEarnedTxt.text = (stats?.amountEarned ?: 0).toString()
        binding.driverHomeStatsLayout.hoursWorkedTxt.text = stats?.workDuration ?: "00:00"
        binding.driverHomeStatsLayout.attendanceInTxt.text = stats?.attendanceIn ?: "00:00"
        binding.recordedVideosTxt.text = (stats?.recordedVideos ?: 0).toString()
        binding.detectedObjectTxt.text = (stats?.detectedObjects ?: 0).toString()

        binding.driverHomeStatsLayout.totalAIVideos.text = (stats?.totalAIVideos ?: 0).toString()
        binding.driverHomeStatsLayout.totalTapVideos.text = (stats?.totalTapVideos ?: 0).toString()
        binding.driverHomeStatsLayout.totalVolVideos.text = (stats?.totalVolVideos ?: 0).toString()
        binding.driverHomeStatsLayout.totalTempVideos.text =
            (stats?.totalTempVideos ?: 0).toString()

        updateOfflineFileCount()
    }

    private fun showCallSupportDialog() = lifecycleScope.launch {
        childFragmentManager.showDialogFragment(DownloadErrorDialogFragment {
            val permissionCheck = ContextCompat.checkSelfPermission(requireContext(), CALL_PHONE)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(CALL_PHONE))
            } else tapToCall("+91-9958083303", requireContext())
        })
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                ifNotPullToRefreshing {
                    binding.progressBar.visible()
                }
            }

            FinishedState -> {
                ifNotPullToRefreshing {
                    binding.progressBar.invisible()
                }
                binding.pullToRefresh.isRefreshing = false
                updateBatteryWarning()
            }

            is DriverHomeViewModel.DriverLearningVideoState -> {
                moveToLearningVideoScreen(it.video)
            }

            is ErrorState -> {
                updateBatteryWarning()
                ifNotPullToRefreshing {
                    binding.progressBar.invisible()
                }
                binding.pullToRefresh.isRefreshing = false
                binding.startRecordingContainer.enabled()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun updateBatteryWarning() {
        when {
            (driverHomeViewModel.isSurveyor()
                    || nayanCamModuleInteractor.getDeviceModel().isKentCam()) -> {
                binding.warningBatteryLow.gone()
            }

            (requireContext().getBatteryLevel() >= 15f) -> binding.warningBatteryLow.gone()
            else -> binding.warningBatteryLow.visible()
        }
    }

    private fun setUpClicks() {
        binding.startRecordingContainer.setOnClickListener {
            val userImage = userRepository.getUserInfo()?.userImage
            if (nayanCamModuleInteractor.isSurveyor() && userImage.isNullOrEmpty()) {
                requireContext().showToast(getString(R.string.mandate_profile_photo))
                (requireActivity() as DashboardActivity).replaceFragment(ProfileFragment())
            } else if (requireContext().hasNetwork()
                && (driverHomeViewModel.isLearningVideosEnabled() && !driverHomeViewModel.isDriverTutorialDone())
            ) driverHomeViewModel.setupLearningVideo()
            else {
                val temperature = requireContext().getBatteryTemperature()
                when {
                    (temperature >= nayanCamModuleInteractor.getOverheatingRestartTemperature()) -> {
                        val tempMessage = getString(
                            co.nayan.nayancamv2.R.string.temp_state_4,
                            temperature.toString()
                        )
                        requireContext().showToast(tempMessage)
                    }

                    else -> moveToNayanDriver()
                }
            }
        }

        binding.offlineVideoContainer.setOnClickListener {
            requireContext().apply {
                if (isServiceRunning<BackgroundCameraService>().not() &&
                    isServiceRunning<ExternalCameraProcessingService>().not()
                ) {
                    val offlineVideosCount = sharedPrefManager.getOfflineVideoBatch()?.size ?: 0
                    if (offlineVideosCount > 0 && hasNetwork()) {
                        showMessage(getString(R.string.video_started_uploading))
                        startVideoUploadRequest(nayanCamModuleInteractor.isSurveyor(), true)
                    } else {
                        showMessage(
                            getString(R.string.offline_video_upload_message).format(
                                offlineVideosCount
                            )
                        )
                    }
                } else showToast(getString(R.string.please_wait_hover_mode_running))
            }
        }

        binding.imageExpandIcon.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.baseView, AutoTransition())
            if (it.isSelected) {
                it.unSelected()
                binding.driverHomeStatsLayout.driverHomeStatsContainer.gone()
                it.animate().rotation(0f).setDuration(500).start()
            } else {
                it.selected()
                binding.driverHomeStatsLayout.driverHomeStatsContainer.visible()
                it.animate().rotation(180f).setDuration(500).start()
            }
        }

        binding.workSummaryContainer.setOnClickListener {
            Intent(activity, DriverWorkSummaryActivity::class.java).apply {
                startActivity(this)
            }
        }

        binding.videoGalleryContainer.setOnClickListener {
            Intent(activity, LearningVideosPlaylistActivity::class.java).apply {
                putExtra(CURRENT_ROLE, DRIVER)
                startActivity(this)
            }
        }

        binding.droneContainer.setOnClickListener {
            if (TrackingUtility.hasRequiredPermissions(requireActivity())) {
                Intent(activity, ExtCamConnectionActivity::class.java).apply {
                    putExtra("selected", "drone")
                    startActivity(this)
                }
            } else showPermissionsDisclosure("drone")
        }

        binding.dashcamContainer.setOnClickListener {
            checkAndLaunchDashcam()
        }
    }

    private fun checkAndLaunchDashcam() {
        if (TrackingUtility.hasRequiredPermissions(requireActivity())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && requireActivity().hasPermission(NEARBY_WIFI_DEVICES).not()
            ) requestNearbyWifiPermission.launch(NEARBY_WIFI_DEVICES)
            else {
                if (::wifiErrorSnackbar.isInitialized && wifiErrorSnackbar.isShown)
                    wifiErrorSnackbar.dismiss()
                Intent(activity, ExtCamConnectionActivity::class.java).apply {
                    putExtra("selected", "dashcam")
                    startActivity(this)
                }
            }
        } else showPermissionsDisclosure("dashcam")
    }

    private fun moveToNayanDriver() = lifecycleScope.launch {
        requireContext().apply {
            if (TrackingUtility.hasRequiredPermissions(this) && isHoverPermissionGranted())
                nayanCamModuleInteractor.moveToDriverApp(
                    requireActivity(),
                    DRIVER,
                    sharedPrefManager.isDefaultHoverMode()
                )
            else showPermissionsDisclosure()
        }
    }

    private fun showPermissionsDisclosure(destination: String = "") {
        Intent(requireActivity(), PermissionsDisclosureActivity::class.java).apply {
            this.putExtra("destination", destination)
            startActivity(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateOfflineFileCount()
        driverHomeViewModel.fetchUserStats()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun ifNotPullToRefreshing(action: () -> Unit) {
        if (binding.pullToRefresh.isRefreshing.not()) action()
    }

    private val launchLearningVideoActivity =
        registerForActivityResult(LearningVideosResultCallback()) {
            driverHomeViewModel.setDriverLearningVideoCompleted()
            moveToNayanDriver()
        }

    private fun moveToLearningVideoScreen(video: Video?) = lifecycleScope.launch {
        if (video == null) moveToNayanDriver()
        else {
            launchLearningVideoActivity.launch(
                LearningVideosContractInput(
                    showDoneButton = true,
                    video = video,
                    workAssignment = null
                )
            )
        }
    }

    companion object {
        fun newInstance() = DriverHomeFragment()
    }

    private fun updateOfflineFileCount() = lifecycleScope.launch {
        if (::sharedPrefManager.isInitialized) {
            val files = sharedPrefManager.getOfflineVideoBatch()
            binding.offlineVideoTxt.text = (files?.size ?: 0).toString()
            binding.countAI.text = (files?.filter {
                it.contains("pol") || it.contains("dsh") || it.contains("dro")
            }?.size ?: 0).toString()
            binding.countTap.text = (files?.filter { it.contains("man_tap") }?.size ?: 0).toString()
            binding.countVol.text = (files?.filter { it.contains("man_vol") }?.size ?: 0).toString()
            val tempUploadedFiles = sharedPrefManager.getNDVVideoBatch()
            binding.countNDV.text = (tempUploadedFiles?.size ?: 0).toString()
        }
    }

    private val resolutionForResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            if (activityResult.resultCode == RESULT_OK) {
                Timber.i("User agreed to make required location settings changes.")
                if (::locationManagerImpl.isInitialized)
                    locationManagerImpl.startReceivingLocationUpdate()
            } else Timber.i("User chose not to make required location settings changes.")
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.map {
                Timber.d("Permissions requested --> ${it.key} = ${it.value}")
                when (it.key) {
                    CALL_PHONE -> {
                        if (it.value) tapToCall("+91-9958083303", requireContext())
                    }

                    ACCESS_FINE_LOCATION -> {
                        if (it.value && ::locationManagerImpl.isInitialized)
                            locationManagerImpl.checkForLocationRequest()
                    }
                }
            }
        }
}