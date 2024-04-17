package com.nayan.nayancamv2.helper

import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.driver_module.Segment
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.util.NotNullDelegate
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber

object GlobalParams {

    var isRecordingVideo = false
    var shouldStartRecordingOnceBufferIsFilled = false
    var appHasLocationUpdates = false
    var hasValidSpeed = true
    var ifUserLocationFallsWithInSurge = true
    var ifUserRecordingOnBlackLines = false
    var isInCorrectScreenOrientation = false
    var isNightModeActive = false
    var currentTemperature = 0F
    var videoUploadingStatus = MutableLiveData<ActivityState>(InitialState)
    var userLocation: UserLocation? by NotNullDelegate()
    var isProcessingFrame = false
    var isCameraExternal = false
    var _segments = MutableLiveData<List<SegmentTrackData>>()
    var scoutDismissStatus = MutableLiveData<ActivityState>(InitialState)
    var syncWorkflowResponse = MutableLiveData<ActivityState>(InitialState)
    var currentSegment: Segment? = null
    var SPATIAL_PROXIMITY_THRESHOLD = 0.000027000027 //latitude equivalent of 3 metres approx
    var SPATIAL_STICKINESS_CONSTANT = 0.000027000027

    fun resetGlobalParams() {
        isRecordingVideo = false
        appHasLocationUpdates = false
        hasValidSpeed = true
        ifUserLocationFallsWithInSurge = true
        ifUserRecordingOnBlackLines = false
        isInCorrectScreenOrientation = false
        isNightModeActive = false
        currentTemperature = 0F
        userLocation = null
        isCameraExternal = false
        isProcessingFrame = false
    }

    val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        Timber.tag("exceptionHandler").e(throwable)
        Firebase.crashlytics.log(coroutineContext.javaClass.name)
        Firebase.crashlytics.recordException(throwable)
    }
}