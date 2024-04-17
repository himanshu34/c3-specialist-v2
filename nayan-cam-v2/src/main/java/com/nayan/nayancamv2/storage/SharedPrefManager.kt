package com.nayan.nayancamv2.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import co.nayan.c3v2.core.fromPrettyJson
import co.nayan.c3v2.core.fromPrettyJsonList
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.models.driver_module.AIWorkFlowModel
import co.nayan.c3v2.core.models.driver_module.Drivers
import co.nayan.c3v2.core.models.driver_module.LastSyncDetails
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData
import co.nayan.c3v2.core.toPrettyJson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nayan.nayancamv2.helper.GlobalParams._segments
import com.nayan.nayancamv2.model.CurrentDataUsage
import com.nayan.nayancamv2.util.Constants.ALLOCATED_PHONE_STORAGE_KEY
import com.nayan.nayancamv2.util.Constants.KEY_DEFAULT_HOVER_MODE
import com.nayan.nayancamv2.util.Constants.KEY_DEFAULT_LITE_MODE
import com.nayan.nayancamv2.util.Constants.KEY_FORCED_LITE_MODE
import com.nayan.nayancamv2.util.Constants.KEY_LAST_TIME_IMAGE_AVAILABLE_CALLED
import com.nayan.nayancamv2.util.Constants.LAST_RESTART
import com.nayan.nayancamv2.util.Constants.MOBILE_DATA
import com.nayan.nayancamv2.util.Constants.SHOW_AI_PREVIEW
import com.nayan.nayancamv2.util.Constants.UPLOAD_NETWORK_TYPE
import com.nayan.nayancamv2.util.Constants.VOLUME_LEVEL
import javax.inject.Inject

/**
 * Manager class to handle all the shared preference related operations
 *
 * @property context
 */
class SharedPrefManager @Inject constructor(
    private val context: Context
) {
    val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        const val SHARED_PREFS_NAME = "NayanDriverPrefs"
        const val CURRENT_DATA_USAGE = "current_data_usage"
        const val DATA_LIMIT = "current_data_limit"
        const val IS_DATA_USAGE_LIMIT_ENABLED = "data_usage_limit_enabled"
        const val DEFAULT_DATA_USAGE_LIMIT = 25F
        const val CAMERA_PARAMS = "CAMERA_PARAMS"

        const val KEY_LOCATION_HISTORY_LAST_SYNC_DATA = "_location_history_last_sync_data"
        const val KEY_GRAPH_HOPPER_LAST_SYNC_DATA = "_graph_hopper_last_sync_data"
        const val KEY_GRAPH_HOPPER_LAST_NODE_DATA = "_graph_hopper_last_node_data"

        // in MB
        const val DEFAULT_MAX_DATA_USAGE_LIMIT = 2048

        const val AI_WORKFLOW_ENGINE = "AIWorkFlowEngines"
        const val AI_WORKFLOW_ENGINE_LAST_SYNC_DATA = "AIWorkFlowEnginesLastSyncData"

        const val VERSION = "VERSION"

        const val ALL_SEGMENTS = "ALL_SEGMENTS"
        const val SEGMENT_SYNC = "SEGMENT_SYNC"
        const val NEARBY_DRIVERS = "NEARBY_DRIVERS"
        const val DEFAULT_DASHCAM = "DEFAULT_DASHCAM"
        const val KEY_OFFLINE_VIDEOS = "KEY_OFFLINE_VIDEOS"
        const val KEY_NDV_VIDEOS = "KEY_NDV_VIDEOS"
    }

    fun setCurrentDataUsage(data: Double) {
        val currentDataUsage = CurrentDataUsage(data = data, System.currentTimeMillis())
        sharedPreferences.edit { putString(CURRENT_DATA_USAGE, currentDataUsage.toPrettyJson()) }
    }

    fun setDataLimitForTheDay(data: Float) {
        sharedPreferences.edit { putFloat(DATA_LIMIT, data) }
    }

    fun getDataLimitForTheDay() = sharedPreferences.getFloat(DATA_LIMIT, DEFAULT_DATA_USAGE_LIMIT)

    fun getCurrentDataUsage(): CurrentDataUsage {
        val defaultDataUsageString = sharedPreferences.getString(CURRENT_DATA_USAGE, null)
        return defaultDataUsageString?.fromPrettyJson()
            ?: run { CurrentDataUsage(data = 0.0, System.currentTimeMillis()) }
    }

    /**
     * function to set the last restart of hover recording service
     */
    fun setLastHoverRestartCalled() {
        sharedPreferences.edit(commit = true) { putLong(LAST_RESTART, System.currentTimeMillis()) }
    }

    fun getLastHoverRestartCalled() = sharedPreferences.getLong(LAST_RESTART, 0L)


    /**
     * function to set the time when last camera buffer processed
     *
     * @param time
     */
    fun setLastTimeImageAvailableCalled(time: Long) {
        sharedPreferences.edit { putLong(KEY_LAST_TIME_IMAGE_AVAILABLE_CALLED, time) }
    }

    fun getLastTimeImageAvailableCalled() =
        sharedPreferences.getLong(KEY_LAST_TIME_IMAGE_AVAILABLE_CALLED, System.currentTimeMillis())

    fun getCameraAIWorkFlows(): MutableList<AIWorkFlowModel>? {
        return sharedPreferences.getString(AI_WORKFLOW_ENGINE, null)?.fromPrettyJsonList()
    }

    fun saveCameraAIWorkFlows(
        lastSyncDetails: LastSyncDetails,
        workflowList: MutableList<AIWorkFlowModel>?
    ) {
        setLastWorkflowsSyncDetails(lastSyncDetails)
        sharedPreferences.edit {
            putString(AI_WORKFLOW_ENGINE, workflowList?.toPrettyJson())
        }
    }

    fun getLastWorkflowsSyncDetails(): LastSyncDetails? {
        return sharedPreferences.getString(AI_WORKFLOW_ENGINE_LAST_SYNC_DATA, null)
            ?.fromPrettyJson()
    }

    private fun setLastWorkflowsSyncDetails(lastSyncDetails: LastSyncDetails) {
        sharedPreferences.edit {
            putString(AI_WORKFLOW_ENGINE_LAST_SYNC_DATA, lastSyncDetails.toPrettyJson())
        }
    }

    fun getAllocatedPhoneStorage(deviceModel: String) =
        if (deviceModel.isKentCam())
            sharedPreferences.getFloat(ALLOCATED_PHONE_STORAGE_KEY, 100F)
        else sharedPreferences.getFloat(ALLOCATED_PHONE_STORAGE_KEY, 50F)

    fun setAllocatedPhoneStorage(percent: Float) {
        return sharedPreferences.edit { putFloat(ALLOCATED_PHONE_STORAGE_KEY, percent) }
    }

    fun setUploadNetworkType(type: Int) {
        sharedPreferences.edit { putInt(UPLOAD_NETWORK_TYPE, type) }
    }

    fun getUploadNetworkType() = sharedPreferences.getInt(UPLOAD_NETWORK_TYPE, MOBILE_DATA)

    fun setDefaultHoverMode(type: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_DEFAULT_HOVER_MODE, type) }
    }

    fun setDefaultDataUsageLimitEnabled(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(IS_DATA_USAGE_LIMIT_ENABLED, isEnabled) }
    }

    fun isDataUsageLimitEnabled() = sharedPreferences.getBoolean(IS_DATA_USAGE_LIMIT_ENABLED, false)

    fun isDefaultHoverMode() = sharedPreferences.getBoolean(KEY_DEFAULT_HOVER_MODE, true)

    fun setVolumeLevel(vol: Int) {
        sharedPreferences.edit { putInt(VOLUME_LEVEL, vol) }
    }

    fun getVolumeLevel() = sharedPreferences.getInt(VOLUME_LEVEL, 3)

    fun isLITEMode() = sharedPreferences.getBoolean(KEY_DEFAULT_LITE_MODE, false)

    fun setLITEMode(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_DEFAULT_LITE_MODE, isEnabled) }
    }

    fun isForcedLITEMode() = sharedPreferences.getBoolean(KEY_FORCED_LITE_MODE, false)

    fun setForcedLITEMode(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_FORCED_LITE_MODE, isEnabled) }
    }

    fun getCameraParams() = sharedPreferences.getString(CAMERA_PARAMS, "") ?: ""

    fun setCameraParams(params: String) {
        sharedPreferences.edit { putString(CAMERA_PARAMS, params) }
    }

    /**
     * Verify if passed [key] is already saved in [SharedPreferences]
     */
    fun contains(key: String): Boolean = sharedPreferences.contains(key)

    fun getEditor(): SharedPreferences.Editor = sharedPreferences.edit()

    /**
     * Insert passed [value] with specific [key] into [SharedPreferences]. If [value] isn't a known
     * type it will be parsed into JSON.
     */
    fun insert(key: String, value: Any) {
        when (value) {
            is Int -> sharedPreferences.edit { putInt(key, value) }
            is Float -> sharedPreferences.edit { putFloat(key, value) }
            is String -> sharedPreferences.edit { putString(key, value) }
            is Boolean -> sharedPreferences.edit { putBoolean(key, value) }
            is Long -> sharedPreferences.edit { putLong(key, value) }
            else -> sharedPreferences.edit { putString(key, value.toPrettyJson()) }
        }
    }

    /**
     * Get value with specific [key] stored in [SharedPreferences] and cast it into [T] type from
     * default value.
     */

    inline fun <reified T> get(key: String, default: T): T {
        return when (default) {
            is Int -> sharedPreferences.getInt(key, default) as T
            is Float -> sharedPreferences.getFloat(key, default) as T
            is String -> sharedPreferences.getString(key, default) as T
            is Boolean -> sharedPreferences.getBoolean(key, default) as T
            is Long -> sharedPreferences.getLong(key, default) as T
            else -> {
                val value = sharedPreferences.getString(key, "")
                if (!value.isNullOrEmpty()) {
                    val type = object : TypeToken<T>() {}.type
                    return Gson().fromJson(value, type) as T
                }
                return default


            }
        }
    }


    /**
     * Remove [key] from [SharedPreferences]
     */
    fun remove(key: String) {
        sharedPreferences.edit { remove(key) }
    }

    fun clearPreferences() {
        sharedPreferences.edit { clear() }
    }

    fun setAIPreview(showAIPreview: Boolean) {
        sharedPreferences.edit { putBoolean(SHOW_AI_PREVIEW, showAIPreview) }
    }

    fun shouldShowAIPreview() = sharedPreferences.getBoolean(SHOW_AI_PREVIEW, false)

    fun setCurrentVersion(version: String) {
        sharedPreferences.edit { putString(VERSION, version) }
    }

    fun getCurrentVersion() = sharedPreferences.getString(VERSION, "") ?: ""

    fun getLocationSyncTimeStamp() =
        sharedPreferences.getLong(KEY_LOCATION_HISTORY_LAST_SYNC_DATA, 0L)

    fun setLocationSyncTimeStamp(timeStamp: Long) {
        sharedPreferences.edit {
            putLong(KEY_LOCATION_HISTORY_LAST_SYNC_DATA, timeStamp)
        }
    }

    // Driver Location Tracking Segments
    fun getGraphHopperSyncTimeStamp() =
        sharedPreferences.getLong(KEY_GRAPH_HOPPER_LAST_SYNC_DATA, 0L)

    fun setGraphHopperSyncTimeStamp(lastRouteLocationDataTime: Long) {
        sharedPreferences.edit {
            putLong(KEY_GRAPH_HOPPER_LAST_SYNC_DATA, lastRouteLocationDataTime)
        }
    }

    fun getGraphHopperLastNode() =
        sharedPreferences.getString(KEY_GRAPH_HOPPER_LAST_NODE_DATA, null)

    fun setGraphHopperLastNode(segmentCoordinates: String) {
        sharedPreferences.edit {
            putString(KEY_GRAPH_HOPPER_LAST_NODE_DATA, segmentCoordinates)
        }
    }

    fun setAllSegments(segmentSyncTime: Long, segmentTrackDataList: List<SegmentTrackData>) {
        sharedPreferences.edit {
            putLong(SEGMENT_SYNC, segmentSyncTime)
            putString(ALL_SEGMENTS, segmentTrackDataList.toPrettyJson())
        }
        _segments.postValue(segmentTrackDataList)
    }

    fun getSegmentLastSyncTime() = sharedPreferences.getLong(SEGMENT_SYNC, 0)

    fun getAllSegments(): MutableList<SegmentTrackData> {
        val segmentsString = sharedPreferences.getString(ALL_SEGMENTS, null)
        return segmentsString?.fromPrettyJsonList() ?: run { mutableListOf() }
    }

    fun setNearbyDrivers(nearbyDrivers: MutableList<Drivers>) {
        sharedPreferences.edit { putString(NEARBY_DRIVERS, nearbyDrivers.toPrettyJson()) }
    }

    fun getNearbyDrivers(): MutableList<Drivers> {
        val driversList = sharedPreferences.getString(NEARBY_DRIVERS, null)
        return driversList?.fromPrettyJsonList() ?: run { mutableListOf() }
    }

    fun setDefaultDashcam(isDashcamDefault: Boolean) {
        sharedPreferences.edit { putBoolean(DEFAULT_DASHCAM, isDashcamDefault) }
    }

    fun isDefaultDashCam(): Boolean = sharedPreferences.getBoolean(DEFAULT_DASHCAM, false)

    fun saveOfflineVideoBatch(offlineVideoBatch: MutableList<String>) {
        sharedPreferences.edit { putString(KEY_OFFLINE_VIDEOS, offlineVideoBatch.toPrettyJson()) }
    }

    fun getOfflineVideoBatch(): MutableList<String>? {
        val offlineVideoString = sharedPreferences.getString(KEY_OFFLINE_VIDEOS, null)
        return offlineVideoString?.fromPrettyJsonList() ?: run { null }
    }

    fun saveNDVVideoBatch(ndvVideoBatch: MutableList<String>) {
        sharedPreferences.edit { putString(KEY_NDV_VIDEOS, ndvVideoBatch.toPrettyJson()) }
    }

    fun getNDVVideoBatch(): MutableList<String>? {
        val ndvVideoString = sharedPreferences.getString(KEY_NDV_VIDEOS, null)
        return ndvVideoString?.fromPrettyJsonList() ?: run { null }
    }
}