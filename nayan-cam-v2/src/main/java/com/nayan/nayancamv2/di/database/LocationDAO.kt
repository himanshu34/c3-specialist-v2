package com.nayan.nayancamv2.di.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.nayan.c3v2.core.models.driver_module.LocationData

@Dao
interface LocationDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToDatabase(locationData: LocationData)

    @Query("SELECT * FROM LocationHistory WHERE time_stamp < :videoTimeStamp ORDER BY time_stamp DESC LIMIT 1")
    suspend fun getPreviousRow(videoTimeStamp: Long): LocationData?

    @Query("SELECT * FROM LocationHistory WHERE time_stamp >= :videoTimeStamp ORDER BY time_stamp ASC LIMIT 3")
    suspend fun getNextRows(videoTimeStamp: Long): MutableList<LocationData>?

    // Limit location history data with half hour latLng points maximum at a time
    @Query("SELECT * FROM LocationHistory WHERE time_stamp >= :lastSyncTimeStamp ORDER BY time_stamp ASC")
    suspend fun getCompleteLocationHistory(lastSyncTimeStamp: Long): MutableList<LocationData>

    @Query("DELETE FROM LocationHistory WHERE time_stamp <= :oneWeekAgoTimeStamp")
    suspend fun clearLocationHistoryWeekTable(oneWeekAgoTimeStamp: Long)

    @Query("DELETE FROM LocationHistory WHERE time_stamp >= :startTimeStamp AND time_stamp <= :endTimeStamp")
    suspend fun clearLocationBetweenTimeStamp(startTimeStamp: Long, endTimeStamp: Long)

    @Query("DELETE FROM LocationHistory")
    suspend fun clearLocationHistory()
}