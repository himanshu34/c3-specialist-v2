package co.nayan.c3specialist_v2.splash

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras.SHOULD_FORCE_START_HOVER
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.ActivitySplashBinding
import co.nayan.c3specialist_v2.introscreen.IntroScreenActivity
import co.nayan.c3specialist_v2.phoneverification.PhoneVerificationActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.getDeviceTotalRAM
import co.nayan.c3v2.core.hasPermission
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.location.LocationManagerImpl
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.LocationFailureState
import co.nayan.c3v2.core.models.LocationSuccessState
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.login.LoginActivity
import co.nayan.c3v2.login.LoginActivity.Companion.KEY_USER
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.NayanCamActivity
import com.nayan.nayancamv2.ai.AIModelManager
import com.nayan.nayancamv2.hovermode.HoverPermissionCallback
import com.nayan.nayancamv2.isHoverPermissionGranted
import com.nayan.nayancamv2.levitate
import com.nayan.nayancamv2.requestHoverPermission
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.ui.PermissionsDisclosureActivity
import com.nayan.nayancamv2.util.Constants.LOCATION_ACCURACY_THRESHOLD
import com.nayan.nayancamv2.util.TrackingUtility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : BaseActivity() {

    private val splashViewModel: SplashViewModel by viewModels()
    private val binding: ActivitySplashBinding by viewBinding(ActivitySplashBinding::inflate)

    @Inject
    lateinit var errorUtils: ErrorUtils

    @Inject
    lateinit var locationManagerImpl: LocationManagerImpl

    @Inject
    lateinit var deviceInfoHelperImpl: DeviceInfoHelperImpl

    private lateinit var sharedPrefManager: SharedPrefManager

    private var locationErrorSnackBar: Snackbar? = null
    private val locationRequestHandler by lazy { Handler(Looper.getMainLooper()) }
    private val locationRequestRunnable = Runnable {
        if (::locationManagerImpl.isInitialized)
            locationManagerImpl.checkForLocationRequest()
    }
    private var isLocationFailedDialogShown = false
    private val locationUpdateObserver = Observer<ActivityState> {
        when (it) {
            is LocationSuccessState -> {
                it.location?.let { loc ->
                    if (loc.accuracy < LOCATION_ACCURACY_THRESHOLD) {
                        val latLng = LatLng(loc.latitude, loc.longitude)
                        splashViewModel.saveLastLocation(latLng)
                        if (isUserLoggedIn()) splashViewModel.fetchAllowedLocations(latLng)
                        else moveToNextScreen()
                    }
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
                            if (!isLocationFailedDialogShown) {
                                resolutionForResult.launch(
                                    IntentSenderRequest.Builder(exception.resolution).build()
                                )
                                isLocationFailedDialogShown = true
                            } else return@Observer
                        } catch (sendEx: IntentSender.SendIntentException) {
                            Firebase.crashlytics.recordException(sendEx)
                            Timber.e(sendEx)
                        }
                    } else {
                        val latLng = LatLng(28.7041, 77.1025)
                        splashViewModel.saveLastLocation(latLng)
                        if (isUserLoggedIn()) splashViewModel.fetchAllowedLocations(latLng)
                        else moveToNextScreen()
                    }
                } ?: run {
                    if (it.errorMessage == getString(co.nayan.c3v2.core.R.string.no_gps_provider))
                        showPermissionError(it.errorMessage, true)
                    else requestLocationPermission.launch(ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor

    companion object {
        const val INVALID_LOCATION = "invalid location"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        setupUserIdForCrashlytics()
        setupUserDetails()
        initDependencies()

        setContentView(binding.root)
        binding.locationIv.levitate(50f, true)

        if (::locationManagerImpl.isInitialized)
            locationManagerImpl.subscribeLocation().observe(this, locationUpdateObserver)
        splashViewModel.state.observe(this, stateObserver)
    }

    private fun initDependencies() = lifecycleScope.launch {
        sharedPrefManager = SharedPrefManager(this@SplashActivity)
        sharedPrefManager.setCurrentVersion(BuildConfig.VERSION_CODE.toString() + " (" + BuildConfig.VERSION_NAME + ")")
        if (isUserLoggedIn()) {
            splashViewModel.registerFCMToken()
            val lastLocationReceivedTime = splashViewModel.getLastLocationReceivedTime()
            val diffTimeGap = System.currentTimeMillis() - lastLocationReceivedTime
            val roles = userRepository.getUserRoles()
            sharedPrefManager.setAIPreview(BuildConfig.FLAVOR == "qa" || roles.contains(Role.ADMIN))
            when {
                roles.contains(Role.DRIVER) -> continueTransition(roles)
                else -> if (diffTimeGap in 1..TimeUnit.MINUTES.toMillis(30))
                    splashViewModel.isUserAllowedToUseApplication()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReceivingLocationUpdates()
    }

    private fun stopReceivingLocationUpdates() {
        if (::locationManagerImpl.isInitialized)
            locationManagerImpl.stopReceivingLocationUpdate()
        locationRequestHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        if (!userRepository.getUserRoles().contains(Role.DRIVER)) {
            locationRequestHandler.post(locationRequestRunnable)
        }
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            is SplashViewModel.AllowedLocationState -> {
                stopReceivingLocationUpdates()
                if (it.isAllowed.not() && !userRepository.getUserRoles().contains(Role.DRIVER))
                    setUpInvalidLocationDialog()
                else moveToNextScreen()
            }

            is ErrorState -> {
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }

            is SplashViewModel.ReferralSuccessState -> {
                Timber.tag("Splash").e("Referral Applied successfully")
            }
        }
    }

    private fun setUpInvalidLocationDialog() {
        val message = getString(R.string.not_allowed_on_this_location)
        val title = getString(R.string.location_not_supported)
        val positiveText = getString(R.string.ok)
        showAlert(
            message = message,
            title = title,
            showPositiveBtn = true,
            positiveText = positiveText,
            tag = INVALID_LOCATION,
            isCancelable = false,
            shouldFinish = true
        )
    }

    override fun alertDialogPositiveClick(shouldFinishActivity: Boolean, tag: String?) {
        when (tag) {
            INVALID_LOCATION -> {
            }
        }
        if (shouldFinishActivity) this@SplashActivity.finish()
    }

    override fun showMessage(message: String) {}

    private fun moveToNextScreen() = lifecycleScope.launch {
        if (locationErrorSnackBar != null && locationErrorSnackBar?.isShown == true)
            locationErrorSnackBar?.dismiss()

        if (isUserLoggedIn()) {
            val roles = userRepository.getUserRoles()
            if (userRepository.isUserAllowed()) {
                continueTransition(roles)
            } else if (roles.contains(Role.DRIVER)) {
                userRepository.removeAllRolesExceptDriver()
                continueTransition(roles)
            }
        } else moveToLoginScreen()
    }

    private fun continueTransition(roles: List<String>) = lifecycleScope.launch {
        val activity = this@SplashActivity
        if (locationErrorSnackBar != null && locationErrorSnackBar?.isShown == true)
            locationErrorSnackBar?.dismiss()

        when {
            (nayanCamModuleInteractor.getDeviceModel().isKentCam()) -> moveToNayanDriver()
            userRepository.isPhoneVerified().not() -> moveToPhoneVerificationScreen()
            userRepository.isOnBoardingDone().not() -> moveToIntroScreen()
            else -> {
                if (roles.contains(Role.DRIVER)) {
                    val aiModelManager = AIModelManager(activity, sharedPrefManager)
                    val aiWorkFlowsIsPresent = sharedPrefManager.getCameraAIWorkFlows()
                        ?.flatMap { it.cameraAIModels }?.none { aiModel ->
                            // Check whether element is already present in device
                            aiModelManager.isCameraAIModelAlreadyPresent(aiModel).not()
                        } ?: false
                    Timber.d(aiWorkFlowsIsPresent.toString())
                    when {
                        (sharedPrefManager.isDefaultHoverMode() && aiWorkFlowsIsPresent &&
                                intent.getBooleanExtra(SHOULD_FORCE_START_HOVER, true)) -> {
                            moveToNayanDriver()
                        }

                        else -> moveToDashboard()
                    }
                } else moveToDashboard()
            }
        }
    }

    private fun setupUserIdForCrashlytics() = lifecycleScope.launch {
        val userId = userRepository.getUID()
        if (BuildConfig.DEBUG.not() && userId.isNotEmpty())
            FirebaseCrashlytics.getInstance().setUserId(userId)
    }

    private fun setupUserDetails() = lifecycleScope.launch {
        val buildVersion =
            String.format("v %s.%d", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        deviceInfoHelperImpl.saveDeviceConfig(buildVersion, getDeviceTotalRAM())

        if (intent.hasExtra(KEY_USER)) {
            intent.parcelable<User>(KEY_USER)?.let {
                userRepository.setUserInfo(it)
            }
        }
    }

    private fun isUserLoggedIn() = userRepository.isUserLoggedIn()

    private fun moveToPhoneVerificationScreen() {
        startActivity(Intent(this@SplashActivity, PhoneVerificationActivity::class.java))
        finish()
    }

    private fun moveToLoginScreen() {
        stopReceivingLocationUpdates()
        Intent(this, LoginActivity::class.java).apply {
            putExtra(LoginActivity.VERSION_NAME, BuildConfig.VERSION_NAME)
            putExtra(LoginActivity.VERSION_CODE, BuildConfig.VERSION_CODE)
            startActivity(this)
        }
        finish()
    }

    private fun moveToIntroScreen() {
        startActivity(Intent(this@SplashActivity, IntroScreenActivity::class.java))
        finish()
    }

    private fun moveToNayanDriver() {
        if (TrackingUtility.hasRequiredPermissions(this)) {
            if (sharedPrefManager.isDefaultHoverMode()) {
                if (!isHoverPermissionGranted())
                    requestHoverPermission(
                        nayanCamModuleInteractor.getDeviceModel(),
                        hoverPermissionCallback
                    )
                else launchCameraService()
            } else openDriverCamera()
        } else showPermissionsDisclosure()
    }

    private val hoverPermissionCallback = object : HoverPermissionCallback {
        override fun onPermissionGranted() {
            launchCameraService()
        }

        override fun onPermissionDenied(intent: Intent) {
            requestOverLayPermissionLauncher.launch(intent)
        }

        override fun onPermissionDeniedAdditional(intent: Intent) {
            AlertDialog.Builder(this@SplashActivity)
                .setTitle("Please Enable the additional permissions")
                .setMessage("Hover mode can not function in background if you disable these permissions.")
                .setPositiveButton("Enable now!") { _, _ -> startActivity(intent) }
                .setCancelable(false)
                .show()
        }
    }

    private val requestOverLayPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (isHoverPermissionGranted()) launchCameraService()
            else {
                showToast(getString(co.nayan.nayancamv2.R.string.draw_over_other_app_))
                finish()
            }
        }

    private fun showPermissionsDisclosure() {
        startActivity(Intent(this, PermissionsDisclosureActivity::class.java))
    }

    private fun openDriverCamera() {
        startActivity(Intent(this, NayanCamActivity::class.java))
        finish()
    }

    private fun launchCameraService() {
        sharedPrefManager.setLastHoverRestartCalled()
        nayanCamModuleInteractor.moveToDriverApp(
            this,
            Role.DRIVER,
            sharedPrefManager.isDefaultHoverMode(),
            sharedPrefManager.isDefaultDashCam()
        )
    }

    private fun moveToDashboard() {
        startActivity(Intent(this@SplashActivity, DashboardActivity::class.java))
        finish()
    }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) showPermissionError(getString(co.nayan.nayancamv2.R.string.fine_permission_denied_explanation))
            else if (locationErrorSnackBar != null && locationErrorSnackBar?.isShown == true)
                locationErrorSnackBar?.dismiss()
        }

    private val requestSettings = registerForActivityResult(SettingsResultContract()) {
        if (!hasPermission(ACCESS_FINE_LOCATION))
            requestLocationPermission.launch(ACCESS_FINE_LOCATION)
    }

    private fun showPermissionError(message: String, noGPSProvider: Boolean = false) {
        locationErrorSnackBar =
            Snackbar.make(binding.container, message, Snackbar.LENGTH_INDEFINITE).apply {
                if (noGPSProvider.not()) {
                    setAction(co.nayan.nayancamv2.R.string.settings) {
                        // Build intent that displays the App settings screen.
                        requestSettings.launch("Settings")
                    }
                }
            }
        locationErrorSnackBar?.show()
    }

    private val resolutionForResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            if (activityResult.resultCode == RESULT_OK) {
                Timber.i("User agreed to make required location settings changes.")
                if (::locationManagerImpl.isInitialized)
                    locationManagerImpl.startReceivingLocationUpdate()
            } else Timber.i("User chose not to make required location settings changes.")
        }
}

class SettingsResultContract : ActivityResultContract<String?, Boolean?>() {
    override fun createIntent(context: Context, input: String?): Intent {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts(
            "package",
            BuildConfig.APPLICATION_ID,
            null
        )
        intent.data = uri
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        Timber.d("parseResult:$resultCode")
        return resultCode == -1
    }
}
