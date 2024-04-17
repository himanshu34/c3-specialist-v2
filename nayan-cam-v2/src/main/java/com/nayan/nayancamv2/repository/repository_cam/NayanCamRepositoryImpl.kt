package com.nayan.nayancamv2.repository.repository_cam

import android.content.Context
import androidx.lifecycle.LiveData
import co.nayan.c3v2.core.api.SafeApiRequest
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.driver_module.AttendanceRequest
import co.nayan.c3v2.core.models.driver_module.AttendanceResponse
import co.nayan.c3v2.core.models.driver_module.CameraAIWorkFlowResponse
import co.nayan.c3v2.core.models.driver_module.RouteLocationData
import co.nayan.c3v2.core.models.driver_module.ServerSegments
import com.google.android.gms.maps.model.LatLng
import com.google.gson.JsonObject
import com.nayan.nayancamv2.model.SensorMeta
import com.nayan.nayancamv2.sensor.SensorLiveData
import com.nayan.nayancamv2.temperature.TemperatureProvider
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NayanCamRepositoryImpl @Inject constructor(
    private val nayanCamModuleInteractor: NayanCamModuleInteractor,
    private val temperatureProvider: TemperatureProvider
) : SafeApiRequest(), INayanCamRepository {

    override fun getLocationManager() = nayanCamModuleInteractor.getLocationManager()

    override fun getApiClientFactory() = nayanCamModuleInteractor.getApiClientFactory()

    override fun getLocationState() = getLocationManager().subscribeLocation()

    override fun checkForLocationRequest() {
        getLocationManager().checkForLocationRequest()
    }

    override fun startLocationUpdate() {
        getLocationManager().startReceivingLocationUpdate()
    }

    override fun stopLocationUpdate() {
        getLocationManager().stopReceivingLocationUpdate()
    }

    override fun getSensorLiveData(context: Context): LiveData<SensorMeta> = SensorLiveData(context)

    override fun startTemperatureUpdate() {
        temperatureProvider.startObservingTemp()
    }

    override fun stopTemperatureUpdate() {
        temperatureProvider.stopTemperatureRefreshing()
    }

    override fun getTemperatureLiveData() = temperatureProvider.temp

    override suspend fun setConfig(id: String, request: JsonObject): Flow<JSONObject> =
        makeSafeRequestForFlow { getApiClientFactory().apiClientNayanCam.setConfig(id, request) }

    override suspend fun getAiWorkFlow(
        latitude: String?,
        longitude: String?
    ): Flow<CameraAIWorkFlowResponse> = makeSafeRequestForFlow {
        getApiClientFactory().apiClientNayanCam.getAiWorkFlow(latitude, longitude)
    }

    override suspend fun getRouteData(
        routeLocationData: RouteLocationData
    ): Flow<ResponseBody> = makeSafeRequestForFlowToGraphHopper {
        getApiClientFactory().apiClientGraphHopper.getRouteData(routeLocationData = routeLocationData)
    }

    override suspend fun postAttendance(
        attendanceRequest: AttendanceRequest
    ): Flow<AttendanceResponse> = makeSafeRequestForFlow {
        getApiClientFactory().apiClientNayanCam.postAttendance(attendanceRequest)
    }

    override suspend fun postSegments(
        segmentList: ServerSegments
    ): Flow<ResponseBody> = makeSafeRequestForFlow {
        getApiClientFactory().apiClientNayanCam.postSegments(segmentList)
    }

    override suspend fun getServerSegments(
        latLng: LatLng
    ): Flow<ServerSegments> = makeSafeRequestForFlow {
        getApiClientFactory().apiClientNayanCam.getSegments(
            latLng.latitude.toString(),
            latLng.longitude.toString()
        )
    }
}