package com.nayan.nayancamv2.repository.repository_graphopper

import co.nayan.c3v2.core.models.driver_module.LocationData
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData

interface IGraphHopperRepository {

    suspend fun getGraphHopperLastLocationHistory(): Pair<Boolean, MutableList<LocationData>>

    suspend fun getGraphHopperSyncTimeStamp(): Long

    suspend fun updateGraphHopperSyncTimeStamp(lastRouteLocationDataTime: Long)

    suspend fun updateGraphHopperLastNode(segmentCoordinates: String)

    suspend fun getGraphHopperLastNode(): String?

    suspend fun ifSegmentAlreadyExists(segmentCoordinates: String): Int

    suspend fun getCurrentWeightage(segmentCoordinates: String): Int

    suspend fun addSegmentCoordinates(segmentCoordinates: String)

    suspend fun updateSegmentCoordinates(segmentCoordinates: String, updatedWeightage: Int)

    suspend fun getAllSegments(): MutableList<SegmentTrackData>

    suspend fun clearOneWeekAgoData()

    suspend fun flushSegmentTrackingTable()
}