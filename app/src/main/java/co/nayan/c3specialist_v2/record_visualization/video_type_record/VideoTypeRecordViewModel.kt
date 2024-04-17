package co.nayan.c3specialist_v2.record_visualization.video_type_record

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.CurrentAnnotation
import co.nayan.c3v2.core.models.DataRecordsCorrupt
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.videoAnnotations
import co.nayan.canvas.utils.FFMPegExtraction
import co.nayan.canvas.utils.OnFFMPegExtractionListener
import co.nayan.canvas.utils.VideoDownloadListener
import co.nayan.canvas.utils.VideoDownloadManager
import co.nayan.canvas.viewmodels.VideoDownloadProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.filter
import kotlin.collections.find
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.isNullOrEmpty
import kotlin.collections.iterator
import kotlin.collections.mutableListOf
import kotlin.collections.set
import kotlin.collections.sortBy

@HiltViewModel
class VideoTypeRecordViewModel @Inject constructor(
    private val videoDownloadProvider: VideoDownloadProvider,
    private val ffmPegExtraction: FFMPegExtraction
) : ViewModel() {

    private var videoDownloadManager: VideoDownloadManager? = null
    private val _record: MutableLiveData<Record> = MutableLiveData()
    val record: LiveData<Record> = _record

    private val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    private val _foregroundVideoDownloading: MutableLiveData<Boolean> = MutableLiveData(true)
    val foregroundVideoDownloading: LiveData<Boolean> = _foregroundVideoDownloading

    private val _downloadingProgress: MutableLiveData<Int> = MutableLiveData(0)
    val downloadingProgress: LiveData<Int> = _downloadingProgress

    private var videoMode: Int = -1
    private var parentAnnotations: MutableList<VideoAnnotationData>? = null
    private val videoAnnotationDataList =
        mutableListOf<VideoAnnotationData>()
    private val videoParentAnnotationDataList =
        mutableListOf<MutableList<VideoAnnotationData>>()
    private var inChildParentAssociationMode: Boolean = false
    var applicationMode: String? = null

    var question: String? = null
    private var currentVideoFrameCount: Int = 0
    private var annotateForChildStepInVideoMode = false
    fun isInterpolationEnabled() = (isInterpolatedMCML() || isInterpolatedMCMT())
    private fun isInterpolatedMCML() = (applicationMode == Mode.INTERPOLATED_MCML)
    private fun isInterpolatedMCMT() = (applicationMode == Mode.INTERPOLATED_MCMT)

    fun initVideoDownloadManager() {
        ffmPegExtraction.setOnFFMPegExtractionListener(onFFMPegExtractionListener)
        videoDownloadManager = videoDownloadProvider.provide(videoDownloadListener)
    }

    fun startVideoProcessing(record: Record) {
        videoDownloadManager?.deleteVideos()
        videoDownloadManager?.downloadRecord(viewModelScope, record)
    }

    private val onFFMPegExtractionListener = object : OnFFMPegExtractionListener {
        override fun onSuccess(record: Record) {
            record.isFramesExtracted = true
            populateRecord(record)
        }

        override fun onCancelled(dataRecordsCorrupt: DataRecordsCorrupt) {
            _state.postValue(FrameExtractionFailedState(dataRecordsCorrupt))
        }

        override fun onFailed(dataRecordsCorrupt: DataRecordsCorrupt) {
            _state.postValue(FrameExtractionFailedState(dataRecordsCorrupt))
        }
    }

    private val videoDownloadListener = object : VideoDownloadListener {
        override fun onDownloadingFailed(
            isSandBox: Boolean?,
            dataRecordsCorrupt: DataRecordsCorrupt
        ) {
            _state.value = DownloadingFailedState(isSandBox, dataRecordsCorrupt)
        }

        override fun onVideoDownloaded(record: Record, isFirst: Boolean?) {
            viewModelScope.launch { ffmPegExtraction.extractFrames(record, isFirst) }
        }

        override fun downloadingProgress(progress: Int) {
            _downloadingProgress.postValue(progress)
        }
    }

    private fun populateRecord(record: Record) {
        if (record.isDownloaded && record.isFramesExtracted)
            _record.postValue(record)
        _foregroundVideoDownloading.postValue(
            record.isDownloaded.not() || record.isFramesExtracted.not()
        )
    }

    fun getVideoAnnotationData(): MutableList<VideoAnnotationData> = videoAnnotationDataList

    fun getVideoParentAnnotationDataForPreview(): MutableList<MutableList<VideoAnnotationData>> {
        videoParentAnnotationDataList.forEach { stepAnnotation ->
            stepAnnotation.forEach {
                if (currentVideoFrameCount == it.frameCount)
                    it.showPreview = true
            }
        }
        return videoParentAnnotationDataList
    }

    fun getVideoAnnotationDataForFrameMonitor(): MutableList<VideoAnnotationData> {
        return videoAnnotationDataList.filter { it.bitmap != null } as MutableList<VideoAnnotationData>
    }

    fun getParentChildAnnotationDataForFrameMonitor(selected: VideoAnnotationData): MutableList<VideoAnnotationData> {
        videoParentAnnotationDataList.forEach { stepAnnotations ->
            stepAnnotations.forEach { videoAnnotation ->
                if (videoAnnotation == selected) {
                    return stepAnnotations.filter { it.bitmap != null } as MutableList<VideoAnnotationData>
                }
            }
        }
        return mutableListOf()
    }

    fun hasVideoAnnotationData(): Boolean {
        return when (videoMode) {
            VideoTypeRecordActivity.PARENT_STEP_VIDEO -> {
                videoAnnotationDataList.isEmpty()
            }
            VideoTypeRecordActivity.CHILD_STEP_VIDEO -> {
                videoParentAnnotationDataList.isEmpty()
            }
            else -> false
        }
    }

    fun selectVideoFrame(videoAnnotationData: VideoAnnotationData) {
        when (videoMode) {
            VideoTypeRecordActivity.PARENT_STEP_VIDEO -> {
                videoAnnotationDataList.forEach {
                    it.selected = (it == videoAnnotationData)
                }
                _state.value = RefreshVideoAnnotationModeState
            }
        }
    }

    fun refreshAnnotations(frameCount: Int?) {
        if (isInterpolationEnabled()) {
            val videoAnnotationData = videoAnnotationDataList.find { it.frameCount == frameCount }
            val annotations = videoAnnotationData?.annotations
            _state.value = if (annotations.isNullOrEmpty()) ClearAnnotationState
            else DrawAnnotationState(annotations, frameCount = frameCount)
        }
    }

    fun isInChildParentAssociationMode() = inChildParentAssociationMode

    fun getLastParentAnnotationHint(): VideoAnnotationData? {
        var parentHint: VideoAnnotationData? = null
        parentAnnotations?.forEach {
            if (it.isParent)
                parentHint = it
        }
        return parentHint
    }

    fun getVideoModeState(): Int = videoMode

    fun monitorFrames(frameCount: Int?) {
        when (videoMode) {
            VideoTypeRecordActivity.PARENT_STEP_VIDEO -> {
                frameCount?.let { fc ->
                    currentVideoFrameCount = fc
                    val highlightFrame = videoAnnotationDataList.find { it.frameCount == fc }
                    if (isInterpolationEnabled()) {
                        _state.value = if (highlightFrame == null) ClearAnnotationState
                        else DrawAnnotationState(
                            highlightFrame.annotations,
                            highlightFrame.frameCount
                        )
                    } else {
                        if (highlightFrame == null) {
                            videoAnnotationDataList.forEach {
                                it.selected = false
                            }
                        } else {
                            if (highlightFrame.selected) return
                            else {
                                videoAnnotationDataList.forEach {
                                    it.selected = (it.frameCount == highlightFrame.frameCount)
                                }
                                _state.value = RefreshVideoAnnotationModeState
                            }
                        }
                    }
                }
            }

            VideoTypeRecordActivity.CHILD_STEP_VIDEO -> {
                frameCount?.let { fc ->
                    videoParentAnnotationDataList.forEach { stepAnnotations ->
                        stepAnnotations.forEach {
                            if (it.bitmap == null && fc == it.frameCount) {
                                currentVideoFrameCount = it.frameCount ?: 0
                                _state.value = RefreshVideoAnnotationModeState
                            }
                        }
                    }
                }
            }
        }
    }

    fun populateVideoAnnotation(record: Record) {
        setupVideoMode(record)
        when (videoMode) {
            VideoTypeRecordActivity.PARENT_STEP_VIDEO -> {
                videoAnnotationDataList.clear()
                val videoAnnotation = record.currentAnnotation
                videoAnnotation?.let {
                    val videoAnnotationMap = HashMap<Int, VideoAnnotationData?>()
                    videoAnnotation.annotations().forEach { annotation ->
                        annotation.frameCount?.let { frameCount ->
                            val videoAnnotationData =
                                if (videoAnnotationMap.containsKey(frameCount))
                                    videoAnnotationMap[frameCount]
                                else VideoAnnotationData(frameCount, bitmap = null)
                            annotation.frameCount = frameCount
                            videoAnnotationData?.annotations?.add(annotation)
                            videoAnnotationMap[frameCount] = videoAnnotationData
                        }
                    }
                    for ((_, value) in videoAnnotationMap) {
                        value?.let { videoAnnotationDataList.add(it) }
                    }
                    videoAnnotationDataList.sortBy { it.frameCount }
                }
            }

            VideoTypeRecordActivity.CHILD_STEP_VIDEO -> {
                videoParentAnnotationDataList.clear()
                val childStep = record.parentAnnotation.videoAnnotations()[0].size + 1
                setupVideoParentAnnotationList(record.currentAnnotation, childStep)
            }
        }
    }

    private fun setupVideoParentAnnotationList(annotations: CurrentAnnotation?, childStep: Int) {
        videoParentAnnotationDataList.clear()
        annotations.videoAnnotations().forEach { stepAnnotations ->
            val stepAnnotationList = mutableListOf<VideoAnnotationData>()
            stepAnnotations.forEachIndexed { index, stepInfo ->
                if (stepInfo.type == DrawType.JUNK) {
                    stepAnnotationList.forEach { it.isJunk = true }
                } else {
                    val stepAnnotationData = VideoAnnotationData(
                        stepInfo.frameCount, bitmap = null,
                        isParent = (index + 1) < childStep,
                        rawAnnotation = stepInfo.rawAnnotation
                    )
                    stepAnnotationData.annotations.add(stepInfo)
                    stepAnnotationList.add(stepAnnotationData)
                }
            }
            videoParentAnnotationDataList.add(stepAnnotationList)
        }
    }

    private fun setupVideoMode(record: Record) {
        annotateForChildStepInVideoMode = record.parentAnnotation != null
        videoMode = if (!annotateForChildStepInVideoMode)
            VideoTypeRecordActivity.PARENT_STEP_VIDEO
        else VideoTypeRecordActivity.CHILD_STEP_VIDEO
    }

    data class DrawAnnotationState(
        val annotations: MutableList<AnnotationData>,
        val frameCount: Int?
    ) : ActivityState()

    object ClearAnnotationState : ActivityState()
    object RefreshVideoAnnotationModeState : ActivityState()
    data class FrameExtractionFailedState(
        val dataRecordsCorrupt: DataRecordsCorrupt
    ) : ActivityState()

    data class DownloadingFailedState(
        val isSandBox: Boolean?,
        val dataRecordsCorrupt: DataRecordsCorrupt
    ) : ActivityState()
}