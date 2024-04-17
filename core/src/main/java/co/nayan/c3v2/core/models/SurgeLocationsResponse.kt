package co.nayan.c3v2.core.models

data class SurgeLocationsResponse(
    val surgeLocations: MutableList<SurgeLocation>,
    val cityWards: MutableList<CityWards>?,
    val success: Boolean
)

data class SurgeLocation(
    val id: Int,
    val latitude: String?,
    val longitude: String?,
    val radius: String?, // Distance In Meters
    val description: String?,
    val iconUrl: String?
)

data class SurgeMeta(
    val id: Int,
    val description: String?,
    val distance: Float,
    val radius: Float
)

data class SurgeMetaData(
    val surge: MutableList<String>
)

data class CityWards(
    val cityKml: String?,
    val centerLat: Double,
    val centerLon: Double
)

data class CityKmlRequest(
    val surgeLocationKml: String?
)

data class CityKmlResponse(
    val cityKml: MutableList<MapData>?
)

data class MapData(
    val name: String?,
    val surgeLocationId: Int,
    val geometry: GeometryCoordinates?
)

data class GeometryCoordinates(
    val coordinates: MutableList<Coordinates>?
)

data class Coordinates(
    val lon: Double,
    val lat: Double
)