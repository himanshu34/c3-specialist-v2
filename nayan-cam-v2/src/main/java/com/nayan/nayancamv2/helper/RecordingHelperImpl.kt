package com.nayan.nayancamv2.helper

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.nayancamv2.R
import com.nayan.nayancamv2.helper.GlobalParams.isCameraExternal
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.helper.GlobalParams.isRecordingVideo
import com.nayan.nayancamv2.helper.GlobalParams.shouldStartRecordingOnceBufferIsFilled
import com.nayan.nayancamv2.helper.GlobalParams.userLocation
import com.nayan.nayancamv2.model.RecordingData
import com.nayan.nayancamv2.model.RecordingState
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.model.VideoData
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.temperature.StateManager
import com.nayan.nayancamv2.temperature.TemperatureUtil
import com.nayan.nayancamv2.util.Constants.CONSECUTIVE_RECORDING_DELAY
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_5
import com.nayan.nayancamv2.util.RecordingEventState.AI_CONSECUTIVE
import com.nayan.nayancamv2.util.RecordingEventState.ORIENTATION_ERROR
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_FAILED
import com.nayan.nayancamv2.util.RecordingEventState.RECORDING_STARTED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingHelperImpl @Inject constructor(
    private val sharedPrefManager: SharedPrefManager,
    private val context: Context,
    private val iMetaDataHelper: IMetaDataHelper,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor
) : IRecordingHelper {

    private val helperScope = CoroutineScope(Dispatchers.IO)
    private val TAG = RecordingHelperImpl::class.java.simpleName
    private val _recordingState = MutableStateFlow<RecordingState?>(null)
    private val recordingState: StateFlow<RecordingState?> = _recordingState
    private val _fileSaveInProgress = MutableLiveData<Boolean>()
    private val fileSaveInProgress: LiveData<Boolean> = _fileSaveInProgress
    private var lastRecordedAt = 0L
    private var recordingStartTime = 0L

    override suspend fun recordVideo(
        actualFile: File,
        userLocationValue: UserLocation,
        modelNameValue: String,
        labelName: String,
        confidence: String,
        isManualVideo: Boolean,
        workFlowMetaDataValue: String
    ): RecordingData? {
        val overHeatingThreshold = nayanCamModuleInteractor.getOverheatingRestartTemperature()
        val driverLiteThreshold = nayanCamModuleInteractor.getDriverLiteTemperature()
        val recordingDifference = System.currentTimeMillis() - recordingStartTime
        val temperature = sharedPrefManager.get(StateManager.BATTERY_TEMP_RESULT_KEY, 0.0f)
        val message = StringBuilder()
        message.append(TemperatureUtil.getTempMessage(context, temperature, driverLiteThreshold, overHeatingThreshold))
        if (labelName.isNotEmpty()) message.append(" || $labelName Detected")
        if (confidence.isNotEmpty()) message.append(" || $confidence")
        val lastTimeCameraRunning = sharedPrefManager.getLastTimeImageAvailableCalled()
        val cameraRunningDiff = System.currentTimeMillis() - lastTimeCameraRunning
        return when {
            (recordingDifference < TimeUnit.SECONDS.toMillis(10)) -> {
                if (isManualVideo.not()) _recordingState.emit(RecordingState(AI_CONSECUTIVE, ""))
                else {
                    _recordingState.emit(
                        RecordingState(
                            RECORDING_FAILED,
                            context.getString(R.string.consecutive_recording)
                        )
                    )
                }
                null
            }

            (cameraRunningDiff >= DELAYED_5) -> {
                _recordingState.emit(
                    RecordingState(RECORDING_FAILED, context.getString(R.string.recording_failed))
                )
                null
            }

            (isCameraExternal.not() && isInCorrectScreenOrientation.not()) -> {
                _recordingState.emit(
                    RecordingState(
                        ORIENTATION_ERROR,
                        context.getString(R.string.tilt_warning)
                    )
                )
                null
            }

            else -> {
                Timber.tag(TAG).d("🦀 Recording Video")
                isRecordingVideo = true
                recordingStartTime = System.currentTimeMillis()
                iMetaDataHelper.setRecordingStartTime(recordingStartTime)
                userLocation = userLocationValue
                _recordingState.emit(RecordingState(RECORDING_STARTED, message.toString()))
                RecordingData(
                    actualFile,
                    isManualVideo,
                    labelName,
                    modelNameValue,
                    recordingStartTime,
                    workFlowMetaDataValue
                )
            }
        }
    }

    override fun getFileSaveProgressLD(): LiveData<Boolean> = fileSaveInProgress

    override fun getRecordingStateLD(): StateFlow<RecordingState?> = recordingState

    override suspend fun recordingDelay(
        recordingData: RecordingData?,
        callback: (RecordingData) -> Unit
    ) {
        if (shouldStartRecordingOnceBufferIsFilled) {
            shouldStartRecordingOnceBufferIsFilled = false
            helperScope.launch {
                delay(CONSECUTIVE_RECORDING_DELAY)
                recordingData?.apply {
                    recordVideo(
                        file,
                        userLocation ?: UserLocation(),
                        modelName,
                        labelDetectedName,
                        "",
                        isManual,
                        workFlowMetaData
                    )?.let { callback.invoke(it) }
                }
            }
        }
    }

    override suspend fun setLastRecordedAt(
        lastRecordedAt: Long,
        status: Int,
        file: File,
        workFlowMetaData: String,
        videoData: VideoData
    ) = withContext(Dispatchers.IO) {
        this@RecordingHelperImpl.lastRecordedAt = lastRecordedAt
        val recordingState =
            iMetaDataHelper.changeRecordingStatus(status, file, workFlowMetaData, videoData)
        _recordingState.emit(recordingState)
        isRecordingVideo = false
    }
}