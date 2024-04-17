package com.nayan.nayancamv2

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.location.Location
import android.net.wifi.ScanResult
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import co.nayan.c3v2.core.models.driver_module.AIWorkFlowModel
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.modeldownloader.AIModelsSyncWorker
import com.nayan.nayancamv2.util.Constants.AI_MODELS_SYNC_TAG
import com.nayan.nayancamv2.util.Constants.ATTENDANCE_SYNC_TAG
import com.nayan.nayancamv2.util.Constants.VIDEO_FILES_SYNC_TAG
import com.nayan.nayancamv2.videouploder.worker.AttendanceSyncWorker
import com.nayan.nayancamv2.videouploder.worker.VideoFilesSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

fun ScanResult.requiresPassword(): Boolean {
    return (this.capabilities.contains("WPA") || this.capabilities.contains("WEP"))
}

fun getDistanceInMeter(latLng: LatLng, latitude: Double, longitude: Double): Float {
    return try {
        val startPoint = Location("Location Start").apply {
            this.latitude = latLng.latitude
            this.longitude = latLng.longitude
        }

        val endPoint = Location("Location End").apply {
            this.latitude = latitude
            this.longitude = longitude
        }

        startPoint.distanceTo(endPoint)
    } catch (e: Exception) {
        Firebase.crashlytics.recordException(e)
        e.printStackTrace()
        0f
    }
}

fun View.adjustConstraints(rootView: ConstraintLayout) {
    this.apply {
        val set = ConstraintSet()
        set.clone(rootView)
        set.connect(id, ConstraintSet.END, rootView.id, ConstraintSet.END, 0)
        set.applyTo(rootView)
    }
}

fun Context.saveBitmap(
    bitmap: Bitmap,
    fileName: String? = ""
) = CoroutineScope(Dispatchers.IO).launch {
    try {
        val cw = ContextWrapper(this@saveBitmap)
        val directory = cw.getDir("imageDir", Context.MODE_PRIVATE)
        val name = fileName + "_" + System.currentTimeMillis().toString()
        val file = File(directory, "bitmap_$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Firebase.crashlytics.recordException(e)
    }
}

fun getCurrentEnabledWorkflows(
    cameraAIWorkFlows: MutableList<AIWorkFlowModel>?,
    userLocation: LatLng?
): MutableList<AIWorkFlowModel> {
    val filteredCameraAIWorkFlows = mutableListOf<AIWorkFlowModel>()
    cameraAIWorkFlows?.let { allAIWorkflows ->
        val geoFencingEnabledWorkflows = allAIWorkflows.filter { it.workflow_RestrictGeoFence }
        if (geoFencingEnabledWorkflows.isNotEmpty()) {
            val currentGeoFencingEnabledWorkflows =
                getGeoFenceEnabledWorkflows(geoFencingEnabledWorkflows, userLocation)
            if (currentGeoFencingEnabledWorkflows.isNotEmpty())
                filteredCameraAIWorkFlows.addAll(currentGeoFencingEnabledWorkflows)
            else filteredCameraAIWorkFlows.addAll(allAIWorkflows.filter { it.workflow_RestrictGeoFence.not() })
        } else filteredCameraAIWorkFlows.addAll(allAIWorkflows.filter { it.workflow_RestrictGeoFence.not() })
    }

    return filteredCameraAIWorkFlows
}

private fun getGeoFenceEnabledWorkflows(
    cameraAIWorkFlows: List<AIWorkFlowModel>?,
    userLocation: LatLng?
): MutableList<AIWorkFlowModel> {
    val filteredCameraAIWorkFlows = mutableListOf<AIWorkFlowModel>()
    cameraAIWorkFlows?.let { allAIWorkflows ->
        userLocation?.let { location ->
            allAIWorkflows.forEach loop@{ workFlow ->
                workFlow.workflow_GeoFences?.forEach { geoFence ->
                    val radiusInMeters = geoFence.geoFenceRadius.toFloat() * 1000
                    val distance = FloatArray(2)
                    Location.distanceBetween(
                        location.latitude,
                        location.longitude,
                        geoFence.geoFenceLatitude.toDouble(),
                        geoFence.geoFenceLongitude.toDouble(),
                        distance
                    )

                    if (distance[0] <= radiusInMeters) {
                        filteredCameraAIWorkFlows.add(workFlow)
                        return@loop
                    }
                }
            }
        }
    }

    return filteredCameraAIWorkFlows
}

// Region getting user Location using GeoCoder
suspend fun getDefaultUserLocation(
    location: Location
): UserLocation = withContext(Dispatchers.Default) {
    return@withContext UserLocation(
        location.latitude,
        location.longitude,
        location.speed,
        (location.speed * 3.6F),
        "", "", "", "", "", "",
        false,
        location.altitude,
        location.time
    )
}

fun Context.startSyncingAIModels(location: Location): UUID {
    // Start downloading AI Models
    val mConstraints = Constraints.Builder().apply {
        setRequiredNetworkType(NetworkType.CONNECTED)
    }.build()

    val inputData = Data.Builder()
        .putDouble("latitude", location.latitude)
        .putDouble("longitude", location.longitude)
        .build()

    val oneTimeWorkRequest = OneTimeWorkRequestBuilder<AIModelsSyncWorker>()
        .also {
            it.setConstraints(mConstraints)
            it.setInputData(inputData)
            it.addTag(AI_MODELS_SYNC_TAG)
        }.build()

    WorkManager.getInstance(this).enqueue(oneTimeWorkRequest)
    return oneTimeWorkRequest.id
}

fun Context.startAttendanceSyncingRequest(isUserLoggingOut: Boolean): UUID {
    // Driver attendance syncing
    val mConstraints = Constraints.Builder().apply {
        setRequiredNetworkType(NetworkType.CONNECTED)
    }.build()

    val inputData = Data.Builder()
        .putBoolean("isUserLoggingOut", isUserLoggingOut)
        .build()

    val oneTimeWorkRequest = OneTimeWorkRequestBuilder<AttendanceSyncWorker>()
        .also {
            it.setConstraints(mConstraints)
            it.setInputData(inputData)
            it.addTag(ATTENDANCE_SYNC_TAG)
        }.build()

    WorkManager.getInstance(this).enqueue(oneTimeWorkRequest)
    return oneTimeWorkRequest.id
}

fun Context.startSyncingVideoFiles(): UUID {
    val oneTimeWorkRequest = OneTimeWorkRequestBuilder<VideoFilesSyncWorker>()
        .also {
            it.addTag(VIDEO_FILES_SYNC_TAG)
        }.build()

    WorkManager.getInstance(this).enqueue(oneTimeWorkRequest)
    return oneTimeWorkRequest.id
}