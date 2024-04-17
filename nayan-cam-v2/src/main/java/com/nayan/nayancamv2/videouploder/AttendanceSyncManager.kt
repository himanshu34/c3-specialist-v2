package com.nayan.nayancamv2.videouploder

import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.AttendanceLockedException
import co.nayan.c3v2.core.DuplicateException
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.FailureState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.driver_module.AttendanceRequest
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.getAttendanceTimeForCityKmlBoundaries
import com.nayan.nayancamv2.repository.repository_cam.INayanCamRepository
import com.nayan.nayancamv2.repository.repository_location.ILocationRepository
import com.nayan.nayancamv2.util.API_RESULT_SUCCESS
import com.nayan.nayancamv2.util.Constants.LOCATION_ACCURACY_THRESHOLD
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class AttendanceSyncManager @Inject constructor(
    private val nayanCamRepository: INayanCamRepository,
    private val iLocationRepository: ILocationRepository,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor
) {

    private val tag = AttendanceSyncManager::class.java.simpleName
    val locationSyncLiveData: MutableLiveData<ActivityState> = MutableLiveData(InitialState)

    suspend fun pushDriverAttendanceData(
        isUserLoggingOut: Boolean
    ): Boolean = withContext(Dispatchers.IO + exceptionHandler) {
        return@withContext try {
            locationSyncLiveData.postValue(ProgressState)
            val allLocationHistory = iLocationRepository.getCompleteLocationHistory()
            if (allLocationHistory.isNotEmpty() && (isUserLoggingOut.not() || allLocationHistory.size >= 5)) {
                val lastSyncTimeStamp = iLocationRepository.getLastLocationSyncTimeStamp()
                if (lastSyncTimeStamp != 0L && allLocationHistory.first().timeStamp == lastSyncTimeStamp)
                    allLocationHistory.removeFirst()

                val pairAttendanceTime = async {
                    getAttendanceTimeForCityKmlBoundaries(
                        allLocationHistory.filter { it.accuracy < LOCATION_ACCURACY_THRESHOLD },
                        nayanCamModuleInteractor.getCityKmlBoundaries(),
                        nayanCamModuleInteractor.getSurgeLocations(),
                        nayanCamModuleInteractor.getCurrentRole()
                    )
                }.await()

                nayanCamRepository.postAttendance(AttendanceRequest(pairAttendanceTime))
                    .onStart { locationSyncLiveData.postValue(ProgressState) }
                    .catch { ex ->
                        when (ex) {
                            is DuplicateException -> {
                                iLocationRepository.updateLocationSyncTimeStamp(pairAttendanceTime.data.last().timeStamp)
                            }

                            is AttendanceLockedException -> {
                                val startTimeStamp = pairAttendanceTime.data.first().timeStamp
                                val endTimeStamp = pairAttendanceTime.data.last().timeStamp
                                iLocationRepository.flushLocationBetweenTimeStamp(
                                    startTimeStamp,
                                    endTimeStamp
                                )
                            }
                        }
                        locationSyncLiveData.postValue(FailureState)
                        Firebase.crashlytics.recordException(ex)
                    }.collect {
                        if (it.status_code == API_RESULT_SUCCESS) {
                            iLocationRepository.updateLocationSyncTimeStamp(pairAttendanceTime.data.last().timeStamp)
                            if (isUserLoggingOut) iLocationRepository.flushLocationHistoryTable()
                            locationSyncLiveData.postValue(FinishedState)
                        } else locationSyncLiveData.postValue(FailureState)
                    }

                true // Attendance Upload successful
            } else {
                locationSyncLiveData.postValue(InitialState)
                false // No attendance to upload
            }
        } catch (e: Exception) {
            locationSyncLiveData.postValue(FailureState)
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
            false // Attendance Upload failed
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        run {
            Timber.e(tag, throwable.message ?: "Database syncing exception occurred")
            locationSyncLiveData.postValue(FailureState)
            Firebase.crashlytics.log("Database syncing exception occurred")
            Firebase.crashlytics.recordException(throwable)
        }
    }
}