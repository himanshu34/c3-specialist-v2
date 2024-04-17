package co.nayan.c3v2.core.interactors

import android.app.Activity
import android.content.Context
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.location.LocationManagerImpl
import co.nayan.c3v2.core.models.Events
import co.nayan.c3v2.core.models.MapData
import co.nayan.c3v2.core.models.SurgeLocation
import co.nayan.c3v2.core.models.User

/**
 * Interactor for camera module
 *
 */
interface NayanCamModuleInteractor {
    fun getUserInfo(): User?
    fun isSurveyor(): Boolean
    fun isAIMode(): Boolean
    fun setAIMode(status: Boolean)
    fun isCRMode(): Boolean
    fun setCRModeConfig(status: Boolean, toBase64: String? = "")
    fun getCRModePassword(): String
    fun isLoggedIn(): Boolean
    fun isOnlyPreviewMode(): Boolean
    fun getId(): Int
    fun getRoles(): List<String>
    fun getCurrentRole(): String
    fun getEmail(): String?
    fun getDriverEvents(): MutableList<Events>?
    suspend fun getSurgeLocations(): MutableList<SurgeLocation>?
    suspend fun getCityKmlBoundaries(): List<MapData>
    fun saveLastLocation(latitude: Double, longitude: Double)
    fun getDriverLiteTemperature(): Float
    fun getOverheatingRestartTemperature(): Float
    fun getRecordingOnBlackLines(): Boolean
    fun getDeviceModel(): String
    fun isVideoUploadAllowed(): Boolean
    suspend fun clearPreferences()

    /**
     * Preview mode is for delhi police
     *
     * @param isPreviewMode
     */
    fun setOnlyPreviewMode(isPreviewMode: Boolean)

    fun getApplicationId(): String

    fun startDashboardActivity(context: Context, shouldForceStartHover: Boolean = true)

    fun startSurgeMapActivity(context: Context, comingFrom: String = "", mode: String = "")

    fun startScoutModeActivity(context: Context)

    fun startSettingsActivity(context: Context, isStorageFull: Boolean = false)

    fun moveToDriverApp(
        activity: Activity,
        role: String,
        isDefaultHoverMode: Boolean = true,
        isDefaultDashCam: Boolean = false
    )

    fun getLocationManager(): LocationManagerImpl
    fun getApiClientFactory(): ApiClientFactory
    fun getSpatialProximityThreshold(): Double
    fun getSpatialStickinessConstant(): Double
    fun getAllowedSurveys(): Int
    fun getGraphhopperClusteringThreshold(): Float
    fun getIsDroneLocationOnly(): Boolean
}