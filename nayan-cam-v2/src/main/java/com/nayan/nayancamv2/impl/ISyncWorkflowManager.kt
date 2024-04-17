package com.nayan.nayancamv2.impl

import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState
import com.google.android.gms.maps.model.LatLng

interface ISyncWorkflowManager {

    suspend fun fetchAllWorkflows(latLng: LatLng)
    suspend fun onLocationUpdate(latLng: LatLng)
    suspend fun getAiWorkFlow(currentTime: Long, latitude: Double, longitude: Double)
    fun subscribe(): MutableLiveData<ActivityState>
    fun subscribeSegmentsData():MutableLiveData<ActivityState>
}