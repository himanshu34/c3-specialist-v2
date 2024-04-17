package com.nayan.nayancamv2.repository.repository_cam

import android.content.Context
import androidx.lifecycle.LiveData
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.location.LocationManagerImpl
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.driver_module.AttendanceRequest
import co.nayan.c3v2.core.models.driver_module.AttendanceResponse
import co.nayan.c3v2.core.models.driver_module.CameraAIWorkFlowResponse
import co.nayan.c3v2.core.models.driver_module.RouteLocationData
import co.nayan.c3v2.core.models.driver_module.ServerSegments
import com.google.android.gms.maps.model.LatLng
import com.google.gson.JsonObject
import com.nayan.nayancamv2.model.SensorMeta
import com.nayan.nayancamv2.temperature.TemperatureProvider
import com.nayan.nayancamv2.util.Event
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import org.json.JSONObject

interface INayanCamRepository {

    fun getLocationManager(): LocationManagerImpl
    fun getApiClientFactory(): ApiClientFactory
    fun getLocationState(): LiveData<ActivityState>
    fun checkForLocationRequest()
    fun startLocationUpdate()
    fun stopLocationUpdate()
    fun getSensorLiveData(context: Context): LiveData<SensorMeta>
    fun startTemperatureUpdate()
    fun stopTemperatureUpdate()
    fun getTemperatureLiveData(): LiveData<Event<TemperatureProvider.TempEvent>>

    suspend fun setConfig(
        id: String,
        request: JsonObject
    ): Flow<JSONObject?>

    suspend fun getAiWorkFlow(
        latitude: String?,
        longitude: String?
    ): Flow<CameraAIWorkFlowResponse?>

    suspend fun getRouteData(routeLocationData: RouteLocationData): Flow<ResponseBody>

    suspend fun postAttendance(
        attendanceRequest: AttendanceRequest
    ): Flow<AttendanceResponse>

    suspend fun postSegments(
        segmentList: ServerSegments
    ): Flow<ResponseBody>

    suspend fun getServerSegments(latLng: LatLng): Flow<ServerSegments?>
}