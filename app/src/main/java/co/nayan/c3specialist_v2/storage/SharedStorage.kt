package co.nayan.c3specialist_v2.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3v2.core.config.Role.ADMIN
import co.nayan.c3v2.core.config.Role.DRIVER
import co.nayan.c3v2.core.config.Role.LEADER
import co.nayan.c3v2.core.config.Role.MANAGER
import co.nayan.c3v2.core.config.Role.SPECIALIST
import co.nayan.c3v2.core.fromPrettyJson
import co.nayan.c3v2.core.fromPrettyJsonList
import co.nayan.c3v2.core.models.CacheTemplate
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.c3v2.core.models.Events
import co.nayan.c3v2.core.models.LearningVideosData
import co.nayan.c3v2.core.models.LearningVideosResult
import co.nayan.c3v2.core.models.MapData
import co.nayan.c3v2.core.models.RecordAnnotation
import co.nayan.c3v2.core.models.RecordJudgment
import co.nayan.c3v2.core.models.RecordReview
import co.nayan.c3v2.core.models.SurgeLocationsResponse
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.WorkFlow
import co.nayan.c3v2.core.models.c3_module.responses.AllowedLocation
import co.nayan.c3v2.core.models.c3_module.responses.FetchAppMinVersionResponse
import co.nayan.c3v2.core.toPrettyJson
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject

open class SharedStorage @Inject constructor(context: Context) {

    protected val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    fun setUserProfileInfo(user: User) {
        sharedPreferences.edit(commit = true) {
            putString(USER_INFO, user.toPrettyJson())
            if (user.isSurveyor == true) {
                putBoolean(KEY_DEFAULT_CR_MODE, true)
                putBoolean(KEY_DEFAULT_AI_MODE, false)
            }
        }
    }

    fun getUserProfileInfo(): User? {
        val userInfoString = sharedPreferences.getString(USER_INFO, null)
        return userInfoString?.fromPrettyJson() ?: run { null }
    }

    fun removeAllRolesExceptDriver() {
        getUserProfileInfo()?.let { user ->
            user.activeRoles?.removeAll { it.equals(DRIVER, ignoreCase = true).not() }
            setUserProfileInfo(user)
        }
    }

    fun isUserLoggedIn() = sharedPreferences.getBoolean(IS_LOGGED_IN, false)

    fun setUserLoggedInStatus(status: Boolean) {
        sharedPreferences.edit(commit = true) { putBoolean(IS_LOGGED_IN, status) }
    }

    fun clearPreferences() {
        sharedPreferences.edit { clear() }
    }

    fun saveContrast(value: Int) {
        sharedPreferences.edit { putInt(IMAGE_CONTRAST, value) }
    }

    fun getContrast() = sharedPreferences.getInt(IMAGE_CONTRAST, 50)

    private fun setHelpVideoPlayed(applicationMode: String) {
        sharedPreferences.edit { putString(ALREADY_PLAYED_HELP_VIDEO, applicationMode) }
    }

    fun shouldPlayHelpVideo(applicationMode: String): Boolean {
        var shouldPlayHelpVideo = true
        val stringValue = sharedPreferences.getString(ALREADY_PLAYED_HELP_VIDEO, null)
        stringValue?.let { value ->
            val modesList = value.split(",").toMutableList()
            shouldPlayHelpVideo = if (modesList.contains(applicationMode)) false
            else {
                modesList.add(applicationMode)
                val builder = modesList.joinToString { "\'${it}\'" }
                setHelpVideoPlayed(builder)
                true
            }
        } ?: run {
            setHelpVideoPlayed(applicationMode)
        }
        return shouldPlayHelpVideo
    }

    fun saveSpanCount(count: Int) {
        sharedPreferences.edit { putInt(SPAN_COUNT, count) }
    }

    fun getSpanCount() = sharedPreferences.getInt(SPAN_COUNT, 2)

    fun saveRecentSearchedTemplate(workStepId: Int?, template: Template) {
        val stringValue = sharedPreferences.getString(RECENTLY_SEARCHED_TEMPLATES, null)
        val recentlySearchedTemplates =
            stringValue?.fromPrettyJsonList() ?: run { mutableListOf<CacheTemplate>() }

        val filteredData = recentlySearchedTemplates.filter { it.wfStepId == workStepId }
        filteredData.find { it.template.id == template.id }?.let { it.count++ }
            ?: run { recentlySearchedTemplates.add(CacheTemplate(workStepId, 0, template)) }

        sharedPreferences.edit {
            putString(RECENTLY_SEARCHED_TEMPLATES, recentlySearchedTemplates.toPrettyJson())
        }
    }

    fun getRecentSearchedTemplate(workStepId: Int?): MutableList<Template> {
        val stringValue = sharedPreferences.getString(RECENTLY_SEARCHED_TEMPLATES, null)
        val recentlySearchedTemplates =
            stringValue?.fromPrettyJsonList() ?: run { mutableListOf<CacheTemplate>() }

        return recentlySearchedTemplates.filter { it.wfStepId == workStepId }
            .sortedByDescending { it.count }.map { it.template }.toMutableList()
    }

    fun getLastSyncLearningVideos() = sharedPreferences.getLong(LAST_SYNC_LEARNING_VIDEOS, 0)

    fun setLearningVideos(videoData: LearningVideosData?) {
        videoData?.let {
            sharedPreferences.edit {
                putString(VIOLATION_VIDEOS_DATA, it.violationVideos.toPrettyJson())
                putString(LEARNING_VIDEOS_DATA, it.learningVideos.toPrettyJson())
                putString(INTRODUCTION_VIDEOS_DATA, it.introductionVideos.toPrettyJson())
                putLong(LAST_SYNC_LEARNING_VIDEOS, System.currentTimeMillis())
            }
        }
    }

    fun getIntroductionVideos(currentRole: String): MutableList<Video>? {
        val stringValue = sharedPreferences.getString(INTRODUCTION_VIDEOS_DATA, null)
        val videoResult: LearningVideosResult? = stringValue?.fromPrettyJson() ?: run { null }

        return videoResult?.let {
            when (currentRole) {
                DRIVER -> it.driver ?: mutableListOf()
                SPECIALIST -> it.specialist ?: mutableListOf()
                MANAGER -> it.manager ?: mutableListOf()
                LEADER -> it.leader ?: mutableListOf()
                ADMIN -> it.admin ?: mutableListOf()
                else -> null
            }
        } ?: run { null }
    }

    fun getLearningVideos(currentRole: String?): MutableList<Video>? {
        val stringValue = sharedPreferences.getString(LEARNING_VIDEOS_DATA, null)
        val videoResult: LearningVideosResult? = stringValue?.fromPrettyJson() ?: run { null }

        return videoResult?.let {
            when (currentRole) {
                DRIVER -> it.driver ?: mutableListOf()
                SPECIALIST -> it.specialist ?: mutableListOf()
                MANAGER -> it.manager ?: mutableListOf()
                LEADER -> it.leader ?: mutableListOf()
                ADMIN -> it.admin ?: mutableListOf()
                else -> null
            }
        } ?: run { null }
    }

    fun getViolationVideos(): MutableList<Video>? {
        val stringValue = sharedPreferences.getString(VIOLATION_VIDEOS_DATA, null)
        return stringValue?.fromPrettyJsonList() ?: run { null }
    }

    open fun addAnnotation(annotation: RecordAnnotation) {}
    open fun clearAnnotations(toRemove: List<RecordAnnotation>) {}
    open fun addAllAnnotations(toSync: List<RecordAnnotation>) {}
    open fun undoAnnotation(): RecordAnnotation? = null
    open fun syncAnnotations(toSync: List<RecordAnnotation>) {}
    open fun getUnsyncedAnnotations(): List<RecordAnnotation> = emptyList()

    open fun addJudgment(judgement: RecordJudgment) {}
    open fun clearJudgments(toRemove: List<RecordJudgment>) {}
    open fun addAllJudgments(toSync: List<RecordJudgment>) {}
    open fun undoJudgment() {}
    open fun syncJudgments(toSync: List<RecordJudgment>) {}
    open fun getUnsyncedJudgments(): List<RecordJudgment> = emptyList()

    open fun addReview(review: RecordReview) {}
    open fun undoReview() {}
    open fun syncReviews(toSync: List<RecordReview>) {}
    open fun getUnsyncedReviews(): List<RecordReview> = emptyList()
    open fun clearReviews(toRemove: List<RecordReview>) {}
    open fun addAllReviews(toSync: List<RecordReview>) {}

    fun setRoleForCanvas(role: String) {
        sharedPreferences.edit { putString(USER_ROLE, role) }
    }

    fun getRoleForCanvas() = sharedPreferences.getString(USER_ROLE, SPECIALIST)

    fun setAppLanguage(toSet: String) {
        sharedPreferences.edit { putString(APP_LANGUAGE, toSet) }
    }

    fun getAppLanguage() = sharedPreferences.getString(APP_LANGUAGE, null)

    fun isOnBoardingDone() = sharedPreferences.getBoolean(IS_ON_BOARDING_DONE, false)

    fun setOnBoardingDone() {
        sharedPreferences.edit { putBoolean(IS_ON_BOARDING_DONE, true) }
    }

    fun saveFCMToken(token: String?) {
        sharedPreferences.edit { putString(FCM_TOKEN, token) }
    }

    fun getCameraAIModels(): MutableList<CameraAIModel> {
        val stringValue = sharedPreferences.getString(CAMERA_AI_ENGINE, null)
        return stringValue?.fromPrettyJsonList() ?: run { mutableListOf() }
    }

    fun syncCameraAIModel(cameraAIModel: CameraAIModel) {
        val unSynced = getCameraAIModels()
        val index = unSynced.indexOfFirst { it.name == cameraAIModel.name }
        if (index != -1) {
            unSynced.removeAt(index)
        }
        unSynced.add(cameraAIModel)
        sharedPreferences.edit { putString(CAMERA_AI_ENGINE, unSynced.toPrettyJson()) }
    }

    fun getAllowedLocation(): MutableList<AllowedLocation>? {
        val stringValue = sharedPreferences.getString(ALLOWED_LOCATION, null)
        return stringValue?.fromPrettyJsonList() ?: run { null }
    }

    fun setAllowedLocation(allowedLocations: MutableList<AllowedLocation>?) {
        sharedPreferences.edit { putString(ALLOWED_LOCATION, allowedLocations?.toPrettyJson()) }
    }

    fun fetchAasmStats(): List<String> {
        val stringValue = sharedPreferences.getString(AASM_STATES, null)
        return stringValue?.fromPrettyJsonList() ?: run { listOf() }
    }

    fun saveAasmStates(toSave: List<String>) {
        sharedPreferences.edit { putString(AASM_STATES, toSave.toPrettyJson()) }
    }

    fun fetchWorkFlows(): List<WorkFlow> {
        val stringValue = sharedPreferences.getString(WORK_FLOWS, null)
        return stringValue?.fromPrettyJsonList() ?: run { listOf() }
    }

    fun saveWorkFlows(toSave: List<WorkFlow>) {
        sharedPreferences.edit { putString(WORK_FLOWS, toSave.toPrettyJson()) }
    }

    fun saveLastLocation(latLng: LatLng) {
        sharedPreferences.edit {
            putFloat(CURRENT_LATITUDE, latLng.latitude.toFloat())
            putFloat(CURRENT_LONGITUDE, latLng.longitude.toFloat())
            putLong(CURRENT_LOCATION_TIME, System.currentTimeMillis())
        }
    }

    fun getLastLocationReceivedTime() = sharedPreferences.getLong(CURRENT_LOCATION_TIME, 0L)

    fun getLastLocation(): LatLng {
        val latitude = sharedPreferences.getFloat(CURRENT_LATITUDE, 28.7041f).toDouble()
        val longitude = sharedPreferences.getFloat(CURRENT_LONGITUDE, 77.1025f).toDouble()
        return LatLng(latitude, longitude)
    }

    fun setUserAllowedToUseApplication(isUserAllowed: Boolean) {
        sharedPreferences.edit {
            getUserProfileInfo()?.let { user ->
                if (isUserAllowed.not() && user.activeRoles?.contains(DRIVER) == true)
                    user.activeRoles?.removeAll { it != DRIVER }
                putBoolean(IS_USER_ALLOWED, isUserAllowed)
                putString(USER_INFO, user.toPrettyJson())
            } ?: run {
                putBoolean(IS_USER_ALLOWED, isUserAllowed)
            }
        }
    }

    fun isUserAllowedToUseApplication() = sharedPreferences.getBoolean(IS_USER_ALLOWED, false)

    fun getSurgeLocationResponse(): SurgeLocationsResponse? {
        val responseString = sharedPreferences.getString(LAST_KNOWN_SURGE_LOCATIONS, null)
        return responseString?.fromPrettyJson() ?: run { null }
    }

    fun saveSurgeLocationResponse(surgeLocationResponse: SurgeLocationsResponse) {
        sharedPreferences.edit {
            putLong(LAST_SURGE_LOCATIONS_SYNC, System.currentTimeMillis())
            putString(LAST_KNOWN_SURGE_LOCATIONS, surgeLocationResponse.toPrettyJson())
        }
    }

    fun getLastSyncSurgeLocations() = sharedPreferences.getLong(LAST_SURGE_LOCATIONS_SYNC, 0L)

    fun getLastSyncCityKmlBoundaries() = sharedPreferences.getLong(LAST_KML_LOCATIONS_SYNC, 0L)

    fun getCityKmlBoundaries(): MutableList<MapData> {
        val kmlBoundaryString = sharedPreferences.getString(LAST_KNOWN_KML_LOCATIONS, null)
        return kmlBoundaryString?.fromPrettyJsonList() ?: run { mutableListOf() }
    }

    fun saveCityKmlBoundaries(mapDataList: MutableList<MapData>) {
        sharedPreferences.edit {
            putLong(LAST_KML_LOCATIONS_SYNC, System.currentTimeMillis())
            putString(LAST_KNOWN_KML_LOCATIONS, mapDataList.toPrettyJson())
        }
    }

    fun getEventList(): MutableList<Events>? {
        val eventsString = sharedPreferences.getString(LAST_KNOWN_EVENTS, null)
        return eventsString?.fromPrettyJsonList() ?: run { null }
    }

    fun saveEventList(surgeList: MutableList<Events>) {
        sharedPreferences.edit {
            putLong(LAST_KNOWN_EVENTS_SYNC, System.currentTimeMillis())
            putString(LAST_KNOWN_EVENTS, surgeList.toPrettyJson())
        }
    }

    fun getLastEventListSync() = sharedPreferences.getLong(LAST_KNOWN_EVENTS_SYNC, 0L)

    fun getLastSurgeDisplayed() = sharedPreferences.getLong(LAST_SURGE_DISPLAYED, 0L)

    fun updateLastSurgeDisplayed() {
        sharedPreferences.edit { putLong(LAST_SURGE_DISPLAYED, System.currentTimeMillis()) }
    }

    fun saveAppConfigData(response: FetchAppMinVersionResponse) {
        val driverLiteTemperature = response.driverLiteTemperature ?: 42F
        val overheatingRestartTemperature = response.overheatingRestartTemperature ?: 45F
        val graphhopperBaseUrl = response.graphhopperBaseUrl ?: BuildConfig.BASE_GRAPH_HOPPER_URL
        val recordingOnBlackLines = response.recordingOnBlackLines ?: false
        val spatialProximityThreshold = response.spatialProximityThreshold ?: 0.0
        val spatialStickinessConstant = response.spatialStickinessConstant ?: 0.0
        val kmlOfInterestRadius = response.kmlOfInterestRadius ?: 10F
        val allowedSurveys = response.surveysAllowedPerRoad ?: 1
        val graphhopperClusteringThreshold = response.graphhopperClusteringThreshold ?: 500F
        val videoUploadStatus = response.videoUpload ?: true
        sharedPreferences.edit {
            putFloat(DRIVER_LITE_TEMPERATURE, driverLiteTemperature)
            putFloat(OVERHEATING_RESTART_TEMPERATURE, overheatingRestartTemperature)
            putString(GRAPHHOPPER_BASE_URL, graphhopperBaseUrl)
            putBoolean(RECORDING_ON_BLACK_LINES, recordingOnBlackLines)
            putLong(SPATIAL_PROXIMITY_THRESHOLD, spatialProximityThreshold.toRawBits())
            putLong(SPATIAL_STICKINESS_CONSTANT, spatialStickinessConstant.toRawBits())
            putFloat(KML_OF_INTEREST_RADIUS, kmlOfInterestRadius)
            putInt(ALLOWED_SURVEYS, allowedSurveys)
            putFloat(GRAPHHOPPER_CLUSTERING_THRESHOLD, graphhopperClusteringThreshold)
            putBoolean(VIDEO_UPLOAD_STATUS, videoUploadStatus)
        }
    }

    fun isVideoUploadAllowed() = sharedPreferences.getBoolean(VIDEO_UPLOAD_STATUS, true)

    fun getDriverLiteTemperature() = sharedPreferences.getFloat(DRIVER_LITE_TEMPERATURE, 42F)

    fun getOverheatingRestartTemperature() =
        sharedPreferences.getFloat(OVERHEATING_RESTART_TEMPERATURE, 45F)

    fun getRecordingOnBlackLines() = sharedPreferences.getBoolean(RECORDING_ON_BLACK_LINES, false)

    fun getGraphhopperBaseUrl() =
        sharedPreferences.getString(GRAPHHOPPER_BASE_URL, BuildConfig.BASE_GRAPH_HOPPER_URL)

    fun getSpatialProximityThreshold() =
        Double.fromBits(sharedPreferences.getLong(SPATIAL_PROXIMITY_THRESHOLD, 0))

    fun getStickinessConstant() =
        Double.fromBits(sharedPreferences.getLong(SPATIAL_STICKINESS_CONSTANT, 0))

    fun getKmlOfInterestRadius() = sharedPreferences.getFloat(KML_OF_INTEREST_RADIUS, 10F)

    fun getAllowedSurveys() = sharedPreferences.getInt(ALLOWED_SURVEYS, 1)

    fun getGraphhopperClusteringThreshold() =
        sharedPreferences.getFloat(GRAPHHOPPER_CLUSTERING_THRESHOLD, 500F)

    fun isAIMode() = sharedPreferences.getBoolean(KEY_DEFAULT_AI_MODE, true)

    fun setAIMode(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_DEFAULT_AI_MODE, isEnabled) }
    }

    fun isCRMode() = sharedPreferences.getBoolean(KEY_DEFAULT_CR_MODE, false)

    fun setCRMode(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_DEFAULT_CR_MODE, isEnabled) }
    }

    fun getCRModePassword(): String {
        return sharedPreferences.getString(KEY_DEFAULT_CR_MODE_PASS, "") ?: ""
    }

    fun setCRModePassword(password: String) {
        sharedPreferences.edit { putString(KEY_DEFAULT_CR_MODE_PASS, password) }
    }

    companion object {
        const val SHARED_PREFS_NAME = "C3SpecialistPrefs"
        const val IS_LOGGED_IN = "IsLoggedIn"
        const val USER_INFO = "UserInfo"
        const val IMAGE_CONTRAST = "ImageContrast"
        const val SPAN_COUNT = "SpanCount"
        const val USER_ROLE = "UserRole"
        const val APP_LANGUAGE = "AppLanguage"
        const val IS_ON_BOARDING_DONE = "IsOnBoardingDone"
        const val FCM_TOKEN = "FCMToken"
        const val CAMERA_AI_ENGINE = "CameraAIEngines"
        const val ALLOWED_LOCATION = "allowed_location"
        const val AASM_STATES = "aasm_states"
        const val WORK_FLOWS = "work_flows"
        const val IS_USER_ALLOWED = "is_user_allowed"
        const val CURRENT_LATITUDE = "current_latitude"
        const val CURRENT_LONGITUDE = "current_longitude"
        const val CURRENT_LOCATION_TIME = "current_location_time"
        const val RECENTLY_SEARCHED_TEMPLATES = "recently_searched_templates"
        const val LAST_KNOWN_SURGE_LOCATIONS = "LAST_KNOWN_SURGE_LOCATIONS"
        const val LAST_SURGE_LOCATIONS_SYNC = "LAST_SURGE_LOCATIONS_SYNC"
        const val LAST_KNOWN_KML_LOCATIONS = "LAST_KNOWN_KML_LOCATIONS"
        const val LAST_KML_LOCATIONS_SYNC = "LAST_KML_LOCATIONS_SYNC"
        const val LAST_KNOWN_EVENTS_SYNC = "LAST_KNOWN_EVENT_SYNC"
        const val LAST_KNOWN_EVENTS = "LAST_KNOWN_EVENT"
        const val ALREADY_PLAYED_HELP_VIDEO = "ALREADY_PLAYED_HELP_VIDEO"
        const val LAST_SURGE_DISPLAYED = "LAST_SURGE_DISPLAYED"
        const val LAST_SYNC_LEARNING_VIDEOS = "LAST_SYNC_LEARNING_VIDEOS"
        const val LEARNING_VIDEOS_DATA = "LEARNING_VIDEOS_DATA"
        const val INTRODUCTION_VIDEOS_DATA = "INTRODUCTION_VIDEOS_DATA"
        const val VIOLATION_VIDEOS_DATA = "VIOLATION_VIDEOS_DATA"
        const val DRIVER_LITE_TEMPERATURE = "DRIVER_LITE_TEMPERATURE"
        const val OVERHEATING_RESTART_TEMPERATURE = "OVERHEATING_RESTART_TEMPERATURE"
        const val GRAPHHOPPER_BASE_URL = "GRAPHHOPPER_BASE_URL"
        const val RECORDING_ON_BLACK_LINES = "RECORDING_ON_BLACK_LINES"
        const val SPATIAL_PROXIMITY_THRESHOLD = "SPATIAL_PROXIMITY_THRESHOLD"
        const val SPATIAL_STICKINESS_CONSTANT = "SPATIAL_STICKINESS_CONSTANT"
        const val KML_OF_INTEREST_RADIUS = "KML_OF_INTEREST_RADIUS"
        const val ALLOWED_SURVEYS = "ALLOWED_SURVEYS"
        const val GRAPHHOPPER_CLUSTERING_THRESHOLD = "GRAPHHOPPER_CLUSTERING_THRESHOLD"
        const val VIDEO_UPLOAD_STATUS = "VIDEO_UPLOAD_STATUS"
        const val KEY_DEFAULT_AI_MODE = "KEY_DEFAULT_AI_MODE"
        const val KEY_DEFAULT_CR_MODE = "KEY_DEFAULT_CR_MODE"
        const val KEY_DEFAULT_CR_MODE_PASS = "KEY_DEFAULT_CR_MODE_PASS"
    }
}