package com.nayan.nayancamv2

import android.location.Location
import android.net.ConnectivityManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState
import com.nayan.nayancamv2.extcam.common.IAIResultsHelper
import com.nayan.nayancamv2.extcam.dashcam.SocketServer
import com.nayan.nayancamv2.model.DroneLocation
import com.nayan.nayancamv2.model.RecognizedObject
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.util.RecordingEventState
import java.lang.ref.WeakReference

object AIResultsHelperImpl : IAIResultsHelper {

    private val _resultLiveData = MutableLiveData<RecognizedObject?>()
    private val resultLiveData: LiveData<RecognizedObject?> = _resultLiveData
    private val _processingStateLiveData = MutableLiveData<RecordingState>()
    private val processingState: LiveData<RecordingState> = _processingStateLiveData
    private val _hoverSwitchState = MutableLiveData<HoverSwitchState>()
    private val hoverSwitchState: LiveData<HoverSwitchState> = _hoverSwitchState
    private val _locationLiveData: MutableLiveData<Location> = MutableLiveData()
    private val locationLiveData: LiveData<Location> = _locationLiveData
    private val _pipLiveData: MutableLiveData<ActivityState> = MutableLiveData(InitState(""))
    private val droneLocationState = MutableLiveData(DroneLocation.Init)

    override fun subscribe(): LiveData<RecognizedObject?> {
        return resultLiveData
    }

    override fun addResults(recognizedObjects: RecognizedObject) {
        _resultLiveData.postValue(recognizedObjects)
    }

    override fun recordingState(state: RecordingState) {
        _processingStateLiveData.postValue(state)
    }

    override fun getRecordingLD(): LiveData<RecordingState> {
        return processingState
    }

    override fun resetAiResults() {
        _processingStateLiveData.postValue(RecordingState(RecordingEventState.AI_SCANNING, ""))
        _resultLiveData.postValue(null)
        _pipLiveData.postValue(InitState(""))

    }

//    override fun addLog(log: String) {
//        logs += log
//    }

//    override fun getLogs(): String {
//        return logs
//    }

    override fun switchHoverActivityLiveData(): LiveData<HoverSwitchState> {
        return hoverSwitchState
    }

    override fun changeHoverSwitchState(hoverSwitchState: HoverSwitchState) {
        _hoverSwitchState.postValue(hoverSwitchState)
    }

    override fun unbindProcessFromNetwork(connectivityManager: WeakReference<ConnectivityManager>) {
        connectivityManager.get()?.bindProcessToNetwork(null)
        SocketServer.stopServer()
    }

    override fun getDroneLocationData(): MutableLiveData<Location> {
        return _locationLiveData
    }

    override fun getLocationLiveData(): LiveData<Location> {
        return locationLiveData
    }

    override fun getMutablePIPLD(): MutableLiveData<ActivityState> {
        return _pipLiveData
    }

    override fun getDroneLocationState(): MutableLiveData<DroneLocation> {
        return droneLocationState
    }

    open class HoverSwitchState
    data class SwitchToActivity(val message: String) : HoverSwitchState()
    data class SwitchToService(val message: String) : HoverSwitchState()

    data class InitState(val message: String) : ActivityState()
    data class SwitchToPIP(val message: String) : ActivityState()
    data class DismissPIP(val message: String) : ActivityState()
}