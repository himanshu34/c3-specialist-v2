package com.nayan.nayancamv2.di.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.nayan.c3v2.core.models.driver_module.VideoUploaderData

@Dao
interface VideoUploaderDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToDatabase(videoUploaderData: VideoUploaderData)

    @Query("SELECT COUNT(*) FROM VideoUploader WHERE video_name=:fileName")
    suspend fun entryExists(fileName: String): Int

    @Query("UPDATE VideoUploader SET upload_status=:uploadStatus, uploaded_at_timestamp=:uploadTime, video_id = CASE WHEN :videoId != 0 THEN :videoId ELSE video_id END WHERE video_name=:videoName")
    suspend fun updateUploadStatus(
        uploadStatus: Int,
        videoId: Int,
        videoName: String,
        uploadTime: Long
    )

    @Query("UPDATE VideoUploader SET upload_status=:uploadStatus, uploaded_at_timestamp = CASE WHEN uploaded_at_timestamp = 0 THEN :uploadTime ELSE uploaded_at_timestamp END WHERE video_name=:videoName")
    suspend fun updateDuplicateFileStatus(uploadStatus: Int, videoName: String, uploadTime: Long)

    @Query("SELECT video_name FROM VideoUploader WHERE upload_status IN (1, 2) ORDER BY created_at_timestamp")
    suspend fun getSyncVideosBatch(): MutableList<String>

    @Query("SELECT * FROM VideoUploader WHERE upload_status = 0 ORDER BY created_at_timestamp ASC")
    suspend fun getUnsyncVideosBatch(): MutableList<VideoUploaderData>

    @Query("SELECT video_name FROM VideoUploader WHERE upload_status = 0")
    suspend fun getOfflineVideosBatch(): MutableList<String>

    @Query("SELECT video_name FROM VideoUploader WHERE upload_status = 1")
    suspend fun getNDVVideosBatch(): MutableList<String>

    @Query("UPDATE VideoUploader SET upload_status = 0, uploaded_at_timestamp = 0, video_id = 0 WHERE video_name IN (:videoNames)")
    suspend fun updateGoingToDeleteVideoBatch(videoNames: List<String>)

    @Query("DELETE FROM VideoUploader WHERE video_name IN (:videoNames)")
    suspend fun clearSyncVideoBatch(videoNames: List<String>)

    @Query("DELETE FROM VideoUploader WHERE video_name=:videoName")
    suspend fun clearSpecifiedVideoData(videoName: String)

    @Query("DELETE FROM VideoUploader")
    suspend fun clearUploadedVideosBatch()
}