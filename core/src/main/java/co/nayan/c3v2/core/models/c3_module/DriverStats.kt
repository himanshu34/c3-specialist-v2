package co.nayan.c3v2.core.models.c3_module

import com.google.gson.annotations.SerializedName

class DriverStats(
    @SerializedName("recorded_videos")
    val recordedVideos: Int?,
    @SerializedName("detected_objects")
    val detectedObjects: Int?,
    @SerializedName("amount_earned")
    val amountEarned: Int?,
    @SerializedName("work_duration")
    val workDuration: String?,
    @SerializedName("driver_ai")
    val totalAIVideos: Int?,
    @SerializedName("driver_tap")
    val totalTapVideos: Int?,
    @SerializedName("driver_manual")
    val totalVolVideos: Int?,
    @SerializedName("driver_temp")
    val totalTempVideos: Int?,
    @SerializedName("surge_attendance_in")
    val attendanceIn: String?
)