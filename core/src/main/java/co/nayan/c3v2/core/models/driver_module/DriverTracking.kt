package co.nayan.c3v2.core.models.driver_module

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.vividsolutions.jts.geom.Coordinate

@Entity(tableName = "LocationHistory")
data class LocationData(
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    @ColumnInfo(name = "time_stamp")
    @PrimaryKey
    val timeStamp: Long,
    @ColumnInfo(name = "accuracy", defaultValue = "0.0")
    val accuracy: Double = 0.0 // // Default value 0.0
)

@Entity(tableName = "SegmentTracking")
data class SegmentTrackData(
    @ColumnInfo(name = "segment_coordinates")
    @SerializedName("coordinates")
    @PrimaryKey
    val coordinates: String,
    @ColumnInfo(name = "count", defaultValue = "1")
    @SerializedName("count")
    val count: Int,
    @ColumnInfo(name = "last_updated", defaultValue = "0")
    @SerializedName("timestamp")
    val timestamp: Long
)

data class Segment(
    val start: Coordinate,
    val end: Coordinate,
    val count: Int
)

data class ServerSegments(
    @SerializedName("path_list")
    var path_list: MutableList<SegmentTrackData>,
    @SerializedName("drivers")
    val drivers: MutableList<Drivers>
)

data class Drivers(
    @SerializedName("location")
    val location: String = "",
    @SerializedName("color")
    val color: String = ""
)