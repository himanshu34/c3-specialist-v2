package co.nayan.c3v2.core.models.driver_module

import androidx.annotation.Keep
import co.nayan.c3v2.core.models.CameraAIModel
import com.google.gson.annotations.SerializedName

@Keep
data class CameraAIWorkFlowResponse(
    @SerializedName("camera_ai_flows")
    val cameraAiWorkFlows: MutableList<AIWorkFlowModel>?
)

@Keep
data class LastSyncDetails(
    val time: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Keep
data class AIWorkFlowModel(
    @SerializedName("id")
    val workflowId: Int,
    @SerializedName("name")
    val workflowName: String?,
    @SerializedName("enabled")
    val workflow_IsEnabled: Boolean,
    @SerializedName("position")
    val workflowPosition: Int,
    @SerializedName("drone_enabled")
    val workflow_IsDroneEnabled: Boolean,
    @SerializedName("restrict_geo_fence")
    val workflow_RestrictGeoFence: Boolean,
    @SerializedName("camera_ai_models")
    val cameraAIModels: MutableList<CameraAIModel>,
    @SerializedName("geo_fences")
    val workflow_GeoFences: MutableList<WorkFlowGeoFences>?
)

@Keep
data class WorkFlowGeoFences(
    @SerializedName("id")
    val geoFenceId: Int,
    @SerializedName("latitude")
    val geoFenceLatitude: String,
    @SerializedName("longitude")
    val geoFenceLongitude: String,
    @SerializedName("radius")
    val geoFenceRadius: String // Distance In Km
)