package com.nayan.nayancamv2.di.database

import androidx.room.Dao
import androidx.room.Query
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData

@Dao
interface SegmentTrackDAO {

    @Query("INSERT into SegmentTracking (segment_coordinates, last_updated) VALUES (:segmentCoordinates, :lastUpdatedTimeStamp)")
    suspend fun addSegmentCoordinatesToDatabase(
        segmentCoordinates: String,
        lastUpdatedTimeStamp: Long
    )

    @Query("UPDATE SegmentTracking SET count=:count, last_updated=:lastUpdatedTimeStamp WHERE segment_coordinates=:segmentCoordinates")
    suspend fun updateSegmentCoordinatesToDatabase(
        segmentCoordinates: String,
        count: Int,
        lastUpdatedTimeStamp: Long
    )

    @Query("SELECT * FROM SegmentTracking")
    suspend fun getAllSegments(): MutableList<SegmentTrackData>

    @Query("SELECT EXISTS (SELECT 1 FROM SegmentTracking WHERE segment_coordinates=:segmentCoordinates)")
    suspend fun ifSegmentAlreadyExists(segmentCoordinates: String): Int

    @Query("SELECT count FROM SegmentTracking WHERE segment_coordinates=:segmentCoordinates")
    suspend fun getCurrentWeightage(segmentCoordinates: String): Int

    @Query("DELETE FROM SegmentTracking WHERE last_updated <= :oneWeekAgoTimeStamp")
    suspend fun clearSegmentHistoryWeekTable(oneWeekAgoTimeStamp: Long)

    @Query("DELETE FROM SegmentTracking")
    suspend fun clearSegmentTracking()
}