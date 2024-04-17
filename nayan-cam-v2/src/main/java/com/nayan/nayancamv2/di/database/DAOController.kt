package com.nayan.nayancamv2.di.database

import co.nayan.c3v2.core.models.driver_module.LocationData
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DAOController @Inject constructor(
    private val locationDao: LocationDAO,
    private val segmentTrackDao: SegmentTrackDAO
) : LocationDAO, SegmentTrackDAO {

    override suspend fun addToDatabase(locationData: LocationData) = withContext(Dispatchers.IO) {
        locationDao.addToDatabase(locationData)
    }

    override suspend fun getPreviousRow(videoTimeStamp: Long): LocationData? =
        withContext(Dispatchers.IO) {
            locationDao.getPreviousRow(videoTimeStamp)
        }

    override suspend fun getNextRows(videoTimeStamp: Long): MutableList<LocationData>? =
        withContext(Dispatchers.IO) {
            locationDao.getNextRows(videoTimeStamp)
        }

    override suspend fun getCompleteLocationHistory(lastSyncTimeStamp: Long): MutableList<LocationData> =
        withContext(Dispatchers.IO) {
            locationDao.getCompleteLocationHistory(lastSyncTimeStamp)
        }

    override suspend fun clearLocationHistoryWeekTable(oneWeekAgoTimeStamp: Long) =
        withContext(Dispatchers.IO) {
            locationDao.clearLocationHistoryWeekTable(oneWeekAgoTimeStamp)
        }

    override suspend fun clearLocationBetweenTimeStamp(
        startTimeStamp: Long,
        endTimeStamp: Long
    ) = withContext(Dispatchers.IO) {
        locationDao.clearLocationBetweenTimeStamp(startTimeStamp, endTimeStamp)
    }

    override suspend fun clearLocationHistory() = withContext(Dispatchers.IO) {
        locationDao.clearLocationHistory()
    }

    override suspend fun addSegmentCoordinatesToDatabase(
        segmentCoordinates: String,
        lastUpdatedTimeStamp: Long
    ) = withContext(Dispatchers.IO) {
        segmentTrackDao.addSegmentCoordinatesToDatabase(
            segmentCoordinates,
            lastUpdatedTimeStamp
        )
    }

    override suspend fun updateSegmentCoordinatesToDatabase(
        segmentCoordinates: String,
        count: Int,
        lastUpdatedTimeStamp: Long
    ) = withContext(Dispatchers.IO) {
        segmentTrackDao.updateSegmentCoordinatesToDatabase(
            segmentCoordinates,
            count,
            lastUpdatedTimeStamp
        )
    }

    override suspend fun getAllSegments(): MutableList<SegmentTrackData> =
        withContext(Dispatchers.IO) {
            segmentTrackDao.getAllSegments()
        }

    override suspend fun ifSegmentAlreadyExists(segmentCoordinates: String): Int =
        withContext(Dispatchers.IO) {
            segmentTrackDao.ifSegmentAlreadyExists(segmentCoordinates)
        }

    override suspend fun getCurrentWeightage(segmentCoordinates: String): Int =
        withContext(Dispatchers.IO) {
            segmentTrackDao.getCurrentWeightage(segmentCoordinates)
        }

    override suspend fun clearSegmentHistoryWeekTable(oneWeekAgoTimeStamp: Long) =
        withContext(Dispatchers.IO) {
            segmentTrackDao.clearSegmentHistoryWeekTable(oneWeekAgoTimeStamp)
        }

    override suspend fun clearSegmentTracking() = withContext(Dispatchers.IO) {
        segmentTrackDao.clearSegmentTracking()
    }
}