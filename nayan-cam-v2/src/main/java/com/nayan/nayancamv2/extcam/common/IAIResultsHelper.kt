package com.nayan.nayancamv2.extcam.common

import android.location.Location
import android.net.ConnectivityManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState
import com.nayan.nayancamv2.AIResultsHelperImpl
import com.nayan.nayancamv2.model.DroneLocation
import com.nayan.nayancamv2.model.RecognizedObject
import com.nayan.nayancamv2.model.RecordingState
import java.lang.ref.WeakReference

interface IAIResultsHelper {
    fun subscribe(): LiveData<RecognizedObject?>
    fun addResults(recognizedObjects: RecognizedObject)
    fun recordingState(state: RecordingState)
    fun getRecordingLD(): LiveData<RecordingState>
    fun resetAiResults()
    fun switchHoverActivityLiveData(): LiveData<AIResultsHelperImpl.HoverSwitchState>
    fun changeHoverSwitchState(hoverSwitchState: AIResultsHelperImpl.HoverSwitchState)
    fun unbindProcessFromNetwork(connectivityManager: WeakReference<ConnectivityManager>)
    fun getDroneLocationData(): MutableLiveData<Location>
    fun getLocationLiveData(): LiveData<Location>
    fun getMutablePIPLD(): MutableLiveData<ActivityState>
    fun getDroneLocationState():MutableLiveData<DroneLocation>
}