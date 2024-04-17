package co.nayan.c3v2.core.models.c3_module.responses

data class FetchAppMinVersionResponse(
    val minAppVersion: Int?,
    val driverLiteTemperature: Float?,
    val overheatingRestartTemperature: Float?,
    val graphhopperBaseUrl: String?,
    val recordingOnBlackLines: Boolean?,
    val spatialProximityThreshold: Double?,
    val spatialStickinessConstant: Double?,
    val kmlOfInterestRadius: Float?,
    val surveysAllowedPerRoad: Int?,
    val graphhopperClusteringThreshold: Float?,
    val videoUpload: Boolean?
)