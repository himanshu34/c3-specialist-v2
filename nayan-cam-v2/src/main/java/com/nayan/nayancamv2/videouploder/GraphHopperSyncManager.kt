package com.nayan.nayancamv2.videouploder

import co.nayan.c3v2.core.fromPrettyJson
import co.nayan.c3v2.core.models.driver_module.LocationData
import co.nayan.c3v2.core.models.driver_module.Points
import co.nayan.c3v2.core.models.driver_module.ResponseGraphHopper
import co.nayan.c3v2.core.models.driver_module.RouteLocationData
import co.nayan.c3v2.core.models.driver_module.SegmentData
import co.nayan.c3v2.core.models.driver_module.ServerSegments
import co.nayan.c3v2.core.models.driver_module.TrackData
import co.nayan.c3v2.core.models.driver_module.TrackPointData
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.getCurrentUTCTime
import com.nayan.nayancamv2.repository.repository_cam.INayanCamRepository
import com.nayan.nayancamv2.repository.repository_graphopper.IGraphHopperRepository
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_15
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

class GraphHopperSyncManager @Inject constructor(
    private val nayanCamRepository: INayanCamRepository,
    private val graphHopperRepository: IGraphHopperRepository
) {

    private val tag = GraphHopperSyncManager::class.java.simpleName
    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob)

    suspend fun startSyncingData(isUserLoggingOut: Boolean) = syncScope.launch(exceptionHandler) {
        val lastGraphHopperSyncTimeStamp = graphHopperRepository.getGraphHopperSyncTimeStamp()
        // Limit location history data with next 15 minutes (30) latLng points after
        // last Graphhopper Synced successfully maximum at a time in single API hit
        // assuming that 1 Location is being stored in every 5 second
        val lastLocationHistoryPair = graphHopperRepository.getGraphHopperLastLocationHistory()
        val lastLocationHistory = lastLocationHistoryPair.second
        val diff = System.currentTimeMillis() - lastGraphHopperSyncTimeStamp
        if (lastLocationHistory.isNotEmpty() && diff >= DELAYED_15)
            startGraphHopperDataSync(lastLocationHistoryPair.first, lastLocationHistory)
        else syncSegmentsToServer()
    }

    private suspend fun startGraphHopperDataSync(
        isGapGreaterThanThreshold: Boolean,
        lastLocationHistory: List<LocationData>
    ) = withContext(Dispatchers.IO + exceptionHandler) {
        val routeLocationData = prepareXMLRequestData(lastLocationHistory)
        val lastItemIndex = if (isGapGreaterThanThreshold) lastLocationHistory.lastIndex
        else if (lastLocationHistory.size > 1) lastLocationHistory.lastIndex - 1
        else lastLocationHistory.lastIndex
        val lastRouteLocationDataTime = lastLocationHistory[lastItemIndex].timeStamp
        nayanCamRepository.getRouteData(routeLocationData)
            .catch {
                Firebase.crashlytics.log(it.toString())
                Firebase.crashlytics.recordException(it)
            }.collect { responseBody ->
                graphHopperRepository.updateGraphHopperSyncTimeStamp(lastRouteLocationDataTime)
                val response = responseBody.string().fromPrettyJson<ResponseGraphHopper>()
                coroutineScope {
                    async { setUpSegmentWeightage(response.paths) }.await()
                    syncSegmentsToServer()
                }
            }
    }

    private suspend fun syncSegmentsToServer() = withContext(Dispatchers.IO + exceptionHandler) {
        val localSegments = graphHopperRepository.getAllSegments()
        if (localSegments.isNotEmpty()) {
            val segments = ServerSegments(localSegments, mutableListOf())
            nayanCamRepository.postSegments(segments)
                .catch {
                    Firebase.crashlytics.log("fun::syncSegmentsToServer failed")
                    Firebase.crashlytics.recordException(it)
                }.collect {
                    val response = JSONObject(it.string())
                    if (response.getBoolean("success"))
                        graphHopperRepository.flushSegmentTrackingTable()
                }
        } else Firebase.crashlytics.log("fun::syncSegmentsToServer - No segments to sync")
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        run {
            Timber.e(tag, throwable.message ?: "Segments syncing exception occurred")
            Firebase.crashlytics.log("Segments syncing exception occurred")
            Firebase.crashlytics.recordException(throwable)
        }
    }

    private suspend fun setUpSegmentWeightage(
        segmentsDataList: MutableList<SegmentData>?
    ) = coroutineScope {
        if (segmentsDataList.isNullOrEmpty().not()) {
            segmentsDataList?.first()?.let { segmentData ->
                val coordinatesList = segmentData.points.coordinates
                processCoordinatesList(coordinatesList)
            }
        }
    }

    private suspend fun processCoordinatesList(coordinatesList: List<List<Double>>) {
        val lastGraphHopperNode = graphHopperRepository.getGraphHopperLastNode()

        coordinatesList.forEachIndexed { index, _ ->
            if (index != coordinatesList.lastIndex) {
                val initialSegmentData = coordinatesList[index]
                val finalSegmentData = coordinatesList[index + 1]
                val segmentHashKey = calculateSegmentHashKey(initialSegmentData, finalSegmentData)

                // To store segment over second last index
                if (index == (coordinatesList.lastIndex - 1))
                    graphHopperRepository.updateGraphHopperLastNode(segmentHashKey)
                // First index to be checked whether it is not same as last saved node
                else if (index == 0 && lastGraphHopperNode != null && lastGraphHopperNode == segmentHashKey)
                    return@forEachIndexed

                when (graphHopperRepository.ifSegmentAlreadyExists(segmentHashKey)) {
                    1 -> {
                        val updatedWeightage =
                            (graphHopperRepository.getCurrentWeightage(segmentHashKey) + 1)
                        graphHopperRepository.updateSegmentCoordinates(
                            segmentHashKey,
                            updatedWeightage
                        )
                    }

                    else -> graphHopperRepository.addSegmentCoordinates(segmentHashKey)
                }
            }
        }
    }

    private fun calculateSegmentHashKey(
        initialSegmentData: List<Double>,
        finalSegmentData: List<Double>
    ): String {
        return if (finalSegmentData.last() > initialSegmentData.last()) {
            "${initialSegmentData.last()},${initialSegmentData.first()}," +
                    "${finalSegmentData.last()},${finalSegmentData.first()}"
        } else {
            "${finalSegmentData.last()},${finalSegmentData.first()}," +
                    "${initialSegmentData.last()},${initialSegmentData.first()}"
        }
    }

    private suspend fun prepareXMLRequestData(locationHistoryData: List<LocationData>): RouteLocationData {
        return withContext(Dispatchers.IO + exceptionHandler) {
            val points = mutableListOf<Points>()
            locationHistoryData.forEach {
                points.add(Points(it.latitude, it.longitude, getCurrentUTCTime(it.timeStamp)))
            }

            return@withContext RouteLocationData(
                TrackData(
                    "Driver Segment Tracking ${locationHistoryData.last().timeStamp}",
                    TrackPointData(points)
                )
            )
        }
    }
}