package com.nayan.nayancamv2.helper

import androidx.lifecycle.LiveData
import com.nayan.nayancamv2.model.RecordingData
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.model.VideoData
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface IRecordingHelper {
    suspend fun recordVideo(
        actualFile: File,
        userLocationValue: UserLocation,
        modelNameValue: String = "",
        labelName: String = "",
        confidence: String = "",
        isManualVideo: Boolean = false,
        workFlowMetaDataValue: String = "",
    ): RecordingData?

    fun getRecordingStateLD(): StateFlow<RecordingState?>

    fun getFileSaveProgressLD(): LiveData<Boolean>
    suspend fun recordingDelay(recordingData: RecordingData?, callback: (RecordingData) -> Unit)
    suspend fun setLastRecordedAt(
        lastRecordedAt: Long,
        status: Int,
        file: File,
        workFlowMetaData: String,
        videoData: VideoData
    )
}