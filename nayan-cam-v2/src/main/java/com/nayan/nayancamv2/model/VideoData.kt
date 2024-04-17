package com.nayan.nayancamv2.model

import android.util.Size
import android.util.SizeF
import java.io.File

data class VideoData(
    val userLocation: UserLocation?,
    val focalLength: Float?,
    val sensorSize: SizeF?,
    val previewSize: Size
)

data class RecordingData(
    val file: File,
    val isManual: Boolean,
    val labelDetectedName: String,
    val modelName: String,
    val recordingStartTime: Long,
    val workFlowMetaData: String
)
