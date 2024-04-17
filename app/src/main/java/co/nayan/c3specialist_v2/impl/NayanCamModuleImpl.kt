package co.nayan.c3specialist_v2.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import co.nayan.appsession.SessionRepositoryInterface
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.config.Extras.COMING_FROM
import co.nayan.c3specialist_v2.config.Extras.IS_STORAGE_FULL
import co.nayan.c3specialist_v2.config.Extras.SHOULD_FORCE_START_HOVER
import co.nayan.c3specialist_v2.config.Extras.SURGE_MODE
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.driversetting.DriverSettingActivity
import co.nayan.c3specialist_v2.launchDashCam
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3specialist_v2.surgeMap.DriverSurgeMapActivity
import co.nayan.c3specialist_v2.surgeMap.DriverSurgeMapActivity.Companion.IS_FORCE_OPENED
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.isGooglePlayServicesAvailable
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.location.LocationManagerImpl
import co.nayan.c3v2.core.models.User
import com.google.android.gms.maps.model.LatLng
import com.nayan.nayancamv2.NayanCamActivity
import com.nayan.nayancamv2.di.SessionInteractor
import com.nayan.nayancamv2.launchHoverService
import com.nayan.nayancamv2.scout.DriverScoutModeActivity
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Impl class for Camera Module Interactor
 *
 * @property sharedStorage
 * @property sessionRepositoryInterface
 * @property userRepository
 * @property locationManagerImpl
 * @property apiClientFactory
 * @property deviceInfoHelperImpl
 */
class NayanCamModuleImpl @Inject constructor(
    private val sharedStorage: SharedStorage,
    private val sessionRepositoryInterface: SessionRepositoryInterface,
    private val userRepository: UserRepository,
    private val locationManagerImpl: LocationManagerImpl,
    private val apiClientFactory: ApiClientFactory,
    private val deviceInfoHelperImpl: DeviceInfoHelperImpl
) : SessionInteractor {

    override fun getSessionRepositoryInterface() = sessionRepositoryInterface
    override fun getUserInfo(): User? = sharedStorage.getUserProfileInfo()

    override fun isSurveyor() = getUserInfo()?.isSurveyor ?: false
    override fun getIsDroneLocationOnly() =
        sharedStorage.getUserProfileInfo()?.isDroneLocationOnly ?: false

    override fun isAIMode() = sharedStorage.isAIMode()

    override fun setAIMode(status: Boolean) {
        sharedStorage.setAIMode(status)
    }

    override fun isCRMode() = sharedStorage.isCRMode()

    override fun setCRModeConfig(status: Boolean, toBase64: String?) {
        sharedStorage.setCRMode(status)
        if (status) sharedStorage.setCRModePassword(toBase64 ?: "")
        else sharedStorage.setCRModePassword("")
    }

    override fun getCRModePassword() = sharedStorage.getCRModePassword()

    private var isPreviewMode = false
    private var currentRole: String = Role.DRIVER

    override fun isLoggedIn() = sharedStorage.isUserLoggedIn()

    /**
     * if recording is not required
     *
     * @return true: recording not required
     */
    override fun isOnlyPreviewMode() = isPreviewMode

    override fun getId() = getUserInfo()?.id ?: 0

    override fun getRoles() = userRepository.getUserRoles()

    override fun setOnlyPreviewMode(isPreviewMode: Boolean) {
        this.isPreviewMode = isPreviewMode
    }

    override fun getEmail() = getUserInfo()?.email

    override fun getDriverEvents() = sharedStorage.getEventList()

    override suspend fun getSurgeLocations() =
        sharedStorage.getSurgeLocationResponse()?.surgeLocations ?: mutableListOf()

    override suspend fun getCityKmlBoundaries() = sharedStorage.getCityKmlBoundaries()

    override fun saveLastLocation(latitude: Double, longitude: Double) {
        sharedStorage.saveLastLocation(LatLng(latitude, longitude))
    }

    override fun getDriverLiteTemperature() = sharedStorage.getDriverLiteTemperature()

    override fun getOverheatingRestartTemperature(): Float {
        return if (getDeviceModel().isKentCam()) 55F
        else sharedStorage.getOverheatingRestartTemperature()
    }

    override fun getRecordingOnBlackLines() = sharedStorage.getRecordingOnBlackLines()

    override fun getDeviceModel() = deviceInfoHelperImpl.getDeviceConfig()?.model ?: ""

    override fun isVideoUploadAllowed() = sharedStorage.isVideoUploadAllowed()

    override suspend fun clearPreferences() {
        userRepository.userLoggedOut()
    }

    override fun getApplicationId() = BuildConfig.APPLICATION_ID

    override fun startDashboardActivity(context: Context, shouldForceStartHover: Boolean) {
        Intent(context, DashboardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(SHOULD_FORCE_START_HOVER, shouldForceStartHover)
            context.startActivity(this)
        }
    }

    override fun startSurgeMapActivity(context: Context, comingFrom: String, mode: String) {
        Intent(context, DriverSurgeMapActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (comingFrom.isNotBlank()) putExtra(COMING_FROM, comingFrom)
            if (mode.isNotBlank()) putExtra(SURGE_MODE, mode)
            putExtra(IS_FORCE_OPENED, true)
            context.startActivity(this)
        }
    }

    override fun startScoutModeActivity(context: Context) {
        Intent(context, DriverScoutModeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(this)
        }
    }

    override fun startSettingsActivity(context: Context, isStorageFull: Boolean) {
        Intent(context, DriverSettingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(IS_STORAGE_FULL, isStorageFull)
            context.startActivity(this)
        }
    }

    override fun moveToDriverApp(
        activity: Activity,
        role: String,
        isDefaultHoverMode: Boolean,
        isDefaultDashCam: Boolean
    ) {
        currentRole = role
        openDriverAppActivity(activity, isDefaultHoverMode, isDefaultDashCam)
    }

    override fun getLocationManager(): LocationManagerImpl = locationManagerImpl
    override fun getApiClientFactory(): ApiClientFactory = apiClientFactory

    private fun openDriverAppActivity(
        activity: Activity,
        isDefaultHoverMode: Boolean,
        isDefaultDashCam: Boolean
    ) {
        val currentTime = System.currentTimeMillis()
        val diff = currentTime - sharedStorage.getLastSurgeDisplayed()
        activity.apply {
            if (isDefaultDashCam) launchDashCam()
            else if (intent.hasExtra(IS_FORCE_OPENED) &&
                intent.getBooleanExtra(IS_FORCE_OPENED, false)
                && activity.isGooglePlayServicesAvailable()
            ) {
                startActivity(Intent(activity, DriverSurgeMapActivity::class.java))
                finishAffinity()
            } else if ((sharedStorage.getLastSurgeDisplayed() == 0L ||
                        diff >= TimeUnit.HOURS.toMillis(3))
                && activity.isGooglePlayServicesAvailable()
            ) {
                startActivity(Intent(activity, DriverSurgeMapActivity::class.java))
                finishAffinity()
            } else {
                if (isDefaultHoverMode) launchHoverService()
                else {
                    startActivity(Intent(activity, NayanCamActivity::class.java))
                    finishAffinity()
                }
            }
        }
    }

    override fun getSpatialProximityThreshold() = sharedStorage.getSpatialProximityThreshold()

    override fun getSpatialStickinessConstant() = sharedStorage.getStickinessConstant()

    override fun getAllowedSurveys() = sharedStorage.getAllowedSurveys()

    override fun getGraphhopperClusteringThreshold() =
        sharedStorage.getGraphhopperClusteringThreshold()

    override fun getCurrentRole() = currentRole
}