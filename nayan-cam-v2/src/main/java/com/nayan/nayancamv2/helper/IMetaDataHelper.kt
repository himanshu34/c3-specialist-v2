package com.nayan.nayancamv2.helper

import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.model.SensorMeta
import com.nayan.nayancamv2.model.UserLocationMeta
import com.nayan.nayancamv2.model.VideoData
import kotlinx.coroutines.Job
import java.io.File

interface IMetaDataHelper {
    fun setMetaData(): Job
    fun setRecordingStartTime(recordingStartTime: Long)
    suspend fun saveMetaData(
        currentLocationMeta: UserLocationMeta?,
        currentSensorData: SensorMeta?
    ): Job

    suspend fun changeRecordingStatus(
        status: Int, file: File, workFlowMetaData: String, videoData: VideoData
    ): RecordingState?
}