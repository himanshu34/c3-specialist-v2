package com.nayan.nayancamv2.impl

import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.driver_module.AIWorkFlowModel
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.FailureState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.driver_module.LastSyncDetails
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.ai.AIModelManager
import com.nayan.nayancamv2.getDistanceInMeter
import com.nayan.nayancamv2.helper.GlobalParams.syncWorkflowResponse
import com.nayan.nayancamv2.repository.repository_cam.INayanCamRepository
import com.nayan.nayancamv2.repository.repository_graphopper.IGraphHopperRepository
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_10
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_5
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncWorkflowManagerImpl @Inject constructor(
    private val aiModelManager: AIModelManager,
    private val nayanCamRepository: INayanCamRepository,
    private val graphHopperRepository: IGraphHopperRepository,
    private val sharedPrefManager: SharedPrefManager
) : ISyncWorkflowManager {

    private val syncWorkflowManagerJob = SupervisorJob()
    private val syncWorkflowManagerScope = CoroutineScope(Dispatchers.IO + syncWorkflowManagerJob)
    private val fetchSegmentsResponse: MutableLiveData<ActivityState> =
        MutableLiveData(InitialState)
    private val tagString = SyncWorkflowManagerImpl::class.simpleName.toString()
    override suspend fun fetchAllWorkflows(latLng: LatLng) {
        val currentTime = System.currentTimeMillis()
        syncWorkflowManagerScope.launch(exceptionHandler) {
            syncWorkflowResponse.postValue(ProgressState)
            if (latLng.latitude == 0.0 || latLng.longitude == 0.0) return@launch
            val lastSyncDetails = aiModelManager.getLastWorkflowsSyncDetails()
            lastSyncDetails?.let {
                val diffLastUpdateInMillis = currentTime - lastSyncDetails.time
                if (diffLastUpdateInMillis >= DELAYED_10)
                    getAiWorkFlow(currentTime, latLng.latitude, latLng.longitude)
                else {
                    val presentCameraAIWorkFlows = aiModelManager.getCameraAIWorkFlows()
                    val items = aiModelManager.getUnavailableCameraAIModel(presentCameraAIWorkFlows)
                    val state = if (items.isNotEmpty()) aiModelManager.startDownloading(items)
                    else SyncWorkflowFinishedState
                    syncWorkflowResponse.postValue(state)
                }
            } ?: run { getAiWorkFlow(currentTime, latLng.latitude, latLng.longitude) }
        }

        // Fetch Segments from server
        startFetchingSegments(latLng, currentTime)
    }

    override suspend fun onLocationUpdate(latLng: LatLng) {
        val currentTime = System.currentTimeMillis()
        syncWorkflowManagerScope.launch(exceptionHandler) {
            syncWorkflowResponse.postValue(ProgressState)
            if (latLng.latitude == 0.0 || latLng.longitude == 0.0) return@launch
            val lastSyncDetails = aiModelManager.getLastWorkflowsSyncDetails()
            lastSyncDetails?.let {
                val diffLastUpdateInMillis = currentTime - lastSyncDetails.time
                val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffLastUpdateInMillis)
                val diffInHours = TimeUnit.MILLISECONDS.toHours(diffLastUpdateInMillis)
                val message = if (diffInMinutes > 60) "$diffInHours hours ago"
                else "$diffInMinutes minutes ago"
                val distanceTravelled =
                    getDistanceInMeter(latLng, lastSyncDetails.latitude, lastSyncDetails.longitude)
                val distanceTravelledInKm = distanceTravelled / 1000
                // Sync workflow in every 2 km or 10 minutes
                // Distance in meters
                if (distanceTravelled >= 2000f || diffLastUpdateInMillis >= DELAYED_10) {
                    Firebase.crashlytics.log("$tagString --> user travelled distance $distanceTravelledInKm Km and last syncing happened $message")
                    getAiWorkFlow(currentTime, latLng.latitude, latLng.longitude)
                } else {
                    val presentCameraAIWorkFlows = aiModelManager.getCameraAIWorkFlows()
                    val items = aiModelManager.getUnavailableCameraAIModel(presentCameraAIWorkFlows)
                    val state = if (items.isNotEmpty()) aiModelManager.startDownloading(items)
                    else SyncWorkflowFinishedState
                    syncWorkflowResponse.postValue(state)
                }
            } ?: run { getAiWorkFlow(currentTime, latLng.latitude, latLng.longitude) }
        }

        // Fetch Segments from server
        startFetchingSegments(latLng, currentTime)
    }

    override fun subscribeSegmentsData(): MutableLiveData<ActivityState> = fetchSegmentsResponse

    override suspend fun getAiWorkFlow(
        currentTime: Long,
        latitude: Double,
        longitude: Double
    ) = withContext(Dispatchers.IO) {
        nayanCamRepository.getAiWorkFlow(latitude.toString(), longitude.toString())
            .onStart { syncWorkflowResponse.postValue(ProgressState) }
            .catch {
                syncWorkflowResponse.postValue(FailureState)
                Firebase.crashlytics.log("$tagString --> Exception while fetching AI WorkFlow ---->>>>>>>>> ${it.message}")
                Firebase.crashlytics.recordException(it)
            }.collect { response ->
                coroutineScope {
                    response?.let {
                        Firebase.crashlytics.log("$tagString --> Response received")
                        it.cameraAiWorkFlows?.let { allAiWorkflows ->
                            val currentDetails = LastSyncDetails(currentTime, latitude, longitude)
                            Timber.e("cameraAiWorkFlows size %s", allAiWorkflows.size)
                            aiModelManager.saveCameraAIWorkFlows(currentDetails, allAiWorkflows)
                            if (allAiWorkflows.isNotEmpty())
                                syncWorkflowResponse.postValue(SyncWorkflowSuccessState(allAiWorkflows))

                            val items = aiModelManager.getUnavailableCameraAIModel(allAiWorkflows)
                            val state = if (items.isNotEmpty()) aiModelManager.startDownloading(items)
                            else SyncWorkflowFinishedState
                            syncWorkflowResponse.postValue(state)
                        } ?: run { syncWorkflowResponse.postValue(InitialState) }
                    } ?: run {
                        Firebase.crashlytics.log("$tagString --> Response received null")
                        syncWorkflowResponse.postValue(FailureState)
                    }
                }
            }
    }

    private suspend fun startFetchingSegments(
        latLng: LatLng, time: Long
    ) = withContext(Dispatchers.IO) {
        val difference = time - sharedPrefManager.getSegmentLastSyncTime()
        if ((fetchSegmentsResponse.value == ProgressState) || difference < DELAYED_5)
            return@withContext

        nayanCamRepository.getServerSegments(latLng)
            .onStart { fetchSegmentsResponse.postValue(ProgressState) }
            .catch { e ->
                fetchSegmentsResponse.postValue(FailureState)
                Firebase.crashlytics.log(e.toString())
                Firebase.crashlytics.recordException(e)
            }.collect { serverSegments ->
                coroutineScope {
                    if (serverSegments != null) {
                        val localSegments = graphHopperRepository.getAllSegments()
                        val unionSegmentsWaitFor = async {
                            performUnionOnSegments(localSegments, serverSegments.path_list)
                        }
                        val unionSegments = unionSegmentsWaitFor.await()
                        sharedPrefManager.setAllSegments(time, unionSegments)
                        sharedPrefManager.setNearbyDrivers(serverSegments.drivers)
                        fetchSegmentsResponse.postValue(SuccessSegmentState(unionSegments))
                    } else fetchSegmentsResponse.postValue(FinishedState)
                }
            }
    }

    private suspend fun performUnionOnSegments(
        localSegments: MutableList<SegmentTrackData>,
        segmentsDataList: MutableList<SegmentTrackData>
    ): List<SegmentTrackData> = supervisorScope {
        return@supervisorScope (localSegments + segmentsDataList).groupBy { it.coordinates }
            .map { (_, elements) -> elements.maxByOrNull { it.count }!! }
    }

    override fun subscribe(): MutableLiveData<ActivityState> = syncWorkflowResponse

    val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        Timber.tag(tagString).e(throwable)
        Firebase.crashlytics.log(coroutineContext.javaClass.name)
        Firebase.crashlytics.recordException(throwable)
        syncWorkflowResponse.postValue(FailureState)
    }

    object SyncWorkflowFinishedState : ActivityState()

    data class SyncWorkflowSuccessState(
        val workFlowList: MutableList<AIWorkFlowModel>
    ) : ActivityState()

    data class SuccessSegmentState(
        val segmentsList: List<SegmentTrackData>
    ) : ActivityState()
}
