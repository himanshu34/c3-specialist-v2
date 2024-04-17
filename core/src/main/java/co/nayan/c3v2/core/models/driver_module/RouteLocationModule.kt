package co.nayan.c3v2.core.models.driver_module

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Namespace
import org.simpleframework.xml.NamespaceList
import org.simpleframework.xml.Root

@Root(name = "gpx", strict = false)
@NamespaceList(
    Namespace(reference = "1.1", prefix = "version"),
    Namespace(reference = "gpxgenerator.com", prefix = "creator")
)
data class RouteLocationData constructor(
    @field:Element(name = "trk", required = false)
    val track: TrackData
)

@Root(name = "trk", strict = false)
data class TrackData constructor(
    @field:Element(name = "name")
    val name: String,
    @field:Element(name = "trkseg")
    val trackSegment: TrackPointData
)

@Root(name = "trkseg", strict = false)
data class TrackPointData constructor(
    @field:ElementList(name = "trkpt", inline = true)
    val trackPoints: MutableList<Points>
)

@Root(name = "trkpt", strict = false)
data class Points constructor(
    @field:Attribute val lat: Double,
    @field:Attribute val lon: Double,
//    @field:Element(name = "ele")
//    val elevation: Float = 0.0f,
    @field:Element(name = "time")
    val time: String
)


data class ResponseGraphHopper(
    val hints: HintsData?,
    val paths: MutableList<SegmentData>?
)

data class HintsData(
    val message: String?,
    val details: String?,
)

data class SegmentData(
    val points: PointsData
)

data class PointsData(
    val type: String?,
    val coordinates: MutableList<MutableList<Double>>
)

data class AttendanceRequest(
    val attendence: AttendanceData
)

data class AttendanceData(
    val data: List<LocationData>,
    val attendance_in: Long,
    val attendance_out: Long,
    val first_in_time: Long,
    val last_in_time: Long,
    val user_role: String?
)

data class AttendanceResponse(
    var status_code: Int? = 0,
    var message: String? = ""
)