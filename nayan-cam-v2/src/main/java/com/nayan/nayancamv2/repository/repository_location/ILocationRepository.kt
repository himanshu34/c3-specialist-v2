package com.nayan.nayancamv2.repository.repository_location

import co.nayan.c3v2.core.models.driver_module.LocationData

interface ILocationRepository {

    suspend fun addLocationToDatabase(locationData: LocationData)

    suspend fun getLastLocationSyncTimeStamp(): Long

    suspend fun updateLocationSyncTimeStamp(timeStamp: Long)

    suspend fun getCompleteLocationHistory(): MutableList<LocationData>

    suspend fun getNearestSetOfLocations(timeStamp: Long): MutableList<LocationData>?

    suspend fun flushLocationBetweenTimeStamp(startTimeStamp: Long, endTimeStamp: Long)

    suspend fun flushLocationHistoryTable()
}