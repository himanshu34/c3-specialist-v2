package com.nayan.nayancamv2.repository.repository_graphopper

import co.nayan.c3v2.core.api.SafeApiRequest
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.driver_module.LocationData
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.di.database.DAOController
import com.nayan.nayancamv2.getDistanceInMeter
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.util.Constants.LOCATION_ACCURACY_THRESHOLD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphHopperRepositoryImpl @Inject constructor(
    private val dao: DAOController,
    private val sharedPrefManager: SharedPrefManager,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor
) : SafeApiRequest(), IGraphHopperRepository {

    override suspend fun getGraphHopperLastLocationHistory(): Pair<Boolean, MutableList<LocationData>> {
        val completeLocationHistory = dao.getCompleteLocationHistory(getGraphHopperSyncTimeStamp())
        val filteredLocationHistory = completeLocationHistory.filter { it.accuracy < LOCATION_ACCURACY_THRESHOLD }
        // Picking next 15 minutes location data points on an average by comparing timeStamp
        // assuming that 1 Location is being stored in every 5 second
        // In case of Dash-cam / Car-cam we're breaking cluster with timeThreshold of 30 minutes
        return getClusterLocationData(filteredLocationHistory)
    }

    private suspend fun getClusterLocationData(
        completeLocationHistory: List<LocationData>
    ): Pair<Boolean, MutableList<LocationData>> = withContext(Dispatchers.IO) {
        val clusteredLocationData = mutableListOf<LocationData>()
        val distanceThreshold = nayanCamModuleInteractor.getGraphhopperClusteringThreshold()
        val timeThreshold = TimeUnit.MINUTES.toMillis(30) // 30 minutes in milliseconds
        var isGapGreaterThanThreshold = false
        try {
            for (index in 0 until completeLocationHistory.lastIndex) {
                val currentLocation = completeLocationHistory[index]
                val nextLocation = completeLocationHistory[index + 1]
                val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                val distanceInMeters = getDistanceInMeter(
                    currentLatLng,
                    nextLocation.latitude,
                    nextLocation.longitude
                )

                // Check difference between firstLocationTimeStamp with currentLocationTimeStamp
                val timeDifference = (currentLocation.timeStamp - completeLocationHistory[0].timeStamp)

                if (distanceInMeters < distanceThreshold) {
                    clusteredLocationData.add(currentLocation)
                    // Check whether it's last index of location history
                    if (index == (completeLocationHistory.lastIndex - 1))
                        clusteredLocationData.add(nextLocation)
                } else if (index != 0 || timeDifference >= timeThreshold) {
                    clusteredLocationData.add(currentLocation)
                    isGapGreaterThanThreshold = true
                    break
                }
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
        }

        return@withContext Pair(isGapGreaterThanThreshold, clusteredLocationData)
    }

    override suspend fun getGraphHopperSyncTimeStamp() =
        sharedPrefManager.getGraphHopperSyncTimeStamp()

    override suspend fun updateGraphHopperSyncTimeStamp(lastRouteLocationDataTime: Long) {
        sharedPrefManager.setGraphHopperSyncTimeStamp(lastRouteLocationDataTime)
    }

    override suspend fun updateGraphHopperLastNode(segmentCoordinates: String) {
        sharedPrefManager.setGraphHopperLastNode(segmentCoordinates)
    }

    override suspend fun getGraphHopperLastNode() = sharedPrefManager.getGraphHopperLastNode()

    override suspend fun ifSegmentAlreadyExists(segmentCoordinates: String) =
        dao.ifSegmentAlreadyExists(segmentCoordinates)

    override suspend fun getCurrentWeightage(segmentCoordinates: String) =
        dao.getCurrentWeightage(segmentCoordinates)

    override suspend fun addSegmentCoordinates(segmentCoordinates: String) {
        dao.addSegmentCoordinatesToDatabase(segmentCoordinates, System.currentTimeMillis())
    }

    override suspend fun updateSegmentCoordinates(
        segmentCoordinates: String,
        updatedWeightage: Int
    ) {
        dao.updateSegmentCoordinatesToDatabase(
            segmentCoordinates,
            updatedWeightage,
            System.currentTimeMillis()
        )
    }

    override suspend fun getAllSegments() = dao.getAllSegments()

    override suspend fun clearOneWeekAgoData() {
        val oneWeekAgoTimeStamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        dao.clearLocationHistoryWeekTable(oneWeekAgoTimeStamp)
        dao.clearSegmentHistoryWeekTable(oneWeekAgoTimeStamp)
    }

    override suspend fun flushSegmentTrackingTable() {
        dao.clearSegmentTracking()
    }
}