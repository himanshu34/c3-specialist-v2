package co.nayan.c3v2.core.location

import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState
import com.google.android.gms.location.LocationRequest

interface ILocationManager {

    fun initializeLocationRequest()
    fun checkForLocationRequest()
    fun startReceivingLocationUpdate()
    fun stopReceivingLocationUpdate()
    fun getLocationRequest(): LocationRequest
    fun subscribeLocation(): MutableLiveData<ActivityState>
}