package co.nayan.c3v2.core.models.driver_module

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import co.nayan.c3v2.core.utils.Constants.VideoUploadStatus.NOT_UPLOADED
import com.google.gson.annotations.SerializedName

@Entity(tableName = "VideoUploader")
data class VideoUploaderData(
    @ColumnInfo(name = "id")
    @SerializedName("id")
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "video_name")
    @SerializedName("video_name")
    val videoName: String,
    @ColumnInfo(name = "local_video_file_path")
    @SerializedName("local_video_file_path")
    val localVideoFilePath: String,
    @ColumnInfo(name = "video_id")
    @SerializedName("video_id")
    val videoId: Int = 0,
    @ColumnInfo(name = "upload_status")
    @SerializedName("upload_status")
    val uploadStatus: Int = NOT_UPLOADED,
    @ColumnInfo(name = "created_at_timestamp")
    @SerializedName("created_at_timestamp")
    val createdAtTimestamp: Long = 0,
    @ColumnInfo(name = "uploaded_at_timestamp")
    @SerializedName("uploaded_at_timestamp")
    val uploadedAtTimestamp: Long = 0
)
