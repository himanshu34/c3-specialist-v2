package com.nayan.nayancamv2.repository.repository_location

import co.nayan.c3v2.core.api.SafeApiRequest
import co.nayan.c3v2.core.models.driver_module.LocationData
import com.nayan.nayancamv2.di.database.DAOController
import com.nayan.nayancamv2.storage.SharedPrefManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val dao: DAOController,
    private val sharedPrefManager: SharedPrefManager
) : SafeApiRequest(), ILocationRepository {

    override suspend fun addLocationToDatabase(locationData: LocationData) {
        dao.addToDatabase(locationData)
    }

    override suspend fun getLastLocationSyncTimeStamp(): Long {
        return sharedPrefManager.getLocationSyncTimeStamp()
    }

    override suspend fun updateLocationSyncTimeStamp(timeStamp: Long) {
        sharedPrefManager.setLocationSyncTimeStamp(timeStamp)
    }

    override suspend fun getCompleteLocationHistory(): MutableList<LocationData> {
        return dao.getCompleteLocationHistory(getLastLocationSyncTimeStamp())
    }

    override suspend fun getNearestSetOfLocations(timeStamp: Long): MutableList<LocationData> {
        val nearestLocationSet = mutableListOf<LocationData>()
        val previousRow = dao.getPreviousRow(timeStamp)
        val nextRows = dao.getNextRows(timeStamp)
        previousRow?.let { nearestLocationSet.add(it) }
        nextRows?.let { nearestLocationSet.addAll(nextRows) }
        return nearestLocationSet
    }

    override suspend fun flushLocationBetweenTimeStamp(startTimeStamp: Long, endTimeStamp: Long) {
        dao.clearLocationBetweenTimeStamp(startTimeStamp, endTimeStamp)
    }

    override suspend fun flushLocationHistoryTable() {
        dao.clearLocationHistory()
    }
}