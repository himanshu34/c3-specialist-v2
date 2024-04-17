package co.nayan.canvas.viewmodels

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.config.Mode.INTERPOLATED_MCML
import co.nayan.c3v2.core.config.Mode.INTERPOLATED_MCMT
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationState
import co.nayan.c3v2.core.models.AnnotationValue
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.c3v2.core.models.CurrentAnnotation
import co.nayan.c3v2.core.models.DataRecordsCorrupt
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.models.c3_module.requests.SandboxRefreshDataRequest
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.answer
import co.nayan.c3views.utils.drawType
import co.nayan.c3views.utils.videoAnnotations
import co.nayan.canvas.config.Thresholds.CROP_ERROR_IGNORANCE_THRESHOLD
import co.nayan.canvas.config.Timer.START_TIME_IN_MILLIS
import co.nayan.canvas.modes.crop.LabelsAdapter
import co.nayan.canvas.sandbox.utils.CropMatcher
import co.nayan.canvas.sandbox.utils.DragSplitMatcher
import co.nayan.canvas.sandbox.utils.LaneMatcher
import co.nayan.canvas.sandbox.utils.PathMatcher
import co.nayan.canvas.utils.VideoDownloadListener
import co.nayan.canvas.utils.VideoDownloadManager
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_SANDBOX
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_VIDEO_VALIDATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_SANDBOX
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_VIDEO_VALIDATION
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

abstract class BaseCanvasViewModel : ViewModel() {

    protected val _state: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val state: LiveData<ActivityState> = _state

    protected val _records: MutableLiveData<MutableList<Record>> = MutableLiveData(mutableListOf())
    val records: LiveData<MutableList<Record>> = _records

    protected val _record: MutableLiveData<Record> = MutableLiveData()
    val record: LiveData<Record> = _record

    protected val _templateState: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    val templateState: LiveData<ActivityState> = _templateState

    protected val _template: MutableLiveData<Template> = MutableLiveData()
    val template: LiveData<Template> = _template

    protected val _canUndo: MutableLiveData<Boolean> = MutableLiveData()
    val canUndo: LiveData<Boolean> = _canUndo

    protected val _learningVideoMode: MutableLiveData<String?> = MutableLiveData()
    val learningVideoMode: LiveData<String?> = _learningVideoMode

    private val _isOpenCvInitialized: MutableLiveData<Boolean> = MutableLiveData()
    val isOpenCvInitialized: LiveData<Boolean> = _isOpenCvInitialized

    protected val _sandboxCurrentStreak: MutableLiveData<Int> = MutableLiveData(0)
    val sandboxCurrentStreak: LiveData<Int> = _sandboxCurrentStreak

    protected val _foregroundVideoDownloading: MutableLiveData<Boolean> = MutableLiveData(true)
    val foregroundVideoDownloading: LiveData<Boolean> = _foregroundVideoDownloading

    protected val _downloadingProgress: MutableLiveData<Int> = MutableLiveData(0)
    val downloadingProgress: LiveData<Int> = _downloadingProgress

    private val _activeAnnotationObserver: MutableLiveData<Boolean> = MutableLiveData(false)
    val activeAnnotationObserver: LiveData<Boolean> = _activeAnnotationObserver

    protected var videoDownloadManager: VideoDownloadManager? = null

    protected var videoMode: Int = -1
    protected var annotateForChildStepInVideoMode = false
    protected var parentAnnotations: MutableList<VideoAnnotationData>? = null
    protected val videoAnnotationDataList =
        mutableListOf<VideoAnnotationData>()
    protected val videoParentAnnotationDataList =
        mutableListOf<MutableList<VideoAnnotationData>>()
    var annotatingFrame: Boolean = false
    protected var inChildParentAssociationMode: Boolean = false
    protected var isExtractingFrame = false

    /**
     * Templates Adapter added in BaseCanvasViewModel
     * we've to maintain cache and state
     */
    var labelList: List<Template> = listOf()
    var labelAdapter: LabelsAdapter? = null

    var position: Int = 0
        protected set
    var workAssignmentId: Int? = null
    var question: String? = null
    var workType: String? = null
    var mediaType: String? = null
    var areRecordsFetched = false
    var shouldLoadPrevAnnotations = false
    var role: String? = null
    var applicationMode: String? = null
    var userCategory: String? = null
    var wfStepId: Int? = null
    private val annotationObjectsAttributes = mutableListOf<AnnotationObjectsAttribute>()
    var imageWidth: Int = 0
    var imageHeight: Int = 0
    var currentVideoFrameCount: Int = 0
    var cameraAiModel: CameraAIModel? = null
    var isImageProcessingEnabled: Boolean = false
    var isManualAIAssistEnabled: Boolean = false
    var appFlavor: String? = null
    var requiredStreak: Int? = null
    var isFetchingRecord: Boolean = false
    var annotationVariationThreshold: Int = CROP_ERROR_IGNORANCE_THRESHOLD
    var isRecordCorruptedCalledCount = 0
    var isFirstRecordCorrupted: Boolean = false
    var isSubmittingRecords: Boolean = false

    // Sniffing Timer variables
    var mTimerRunning: Boolean = false
    var mTimeLeftInMillis: Long = START_TIME_IN_MILLIS
    var mEndTime: Long = 0

    // Interpolation variables
    private val _isMediaPlaybackStatus: MutableLiveData<Boolean> = MutableLiveData(false)
    val isMediaPlaybackStatus: LiveData<Boolean> = _isMediaPlaybackStatus
    var framesDir: File? = null
    var totalFrames = 1
    var activeSandboxAnnotationId: String? = null
    var activeAnnotationId: String? = null
    var isInPreviewMode: Boolean = false
    private val _isPreviewModeStatus: MutableLiveData<Boolean> = MutableLiveData(false)
    val isPreviewModeStatus: LiveData<Boolean> = _isPreviewModeStatus
    var isSandboxCreationMode: Boolean = false
    var sandboxRefreshDataRequest: SandboxRefreshDataRequest? = null
    var sandBoxInterpolationDataList = mutableListOf<AnnotationData>()
    var distinctDataObjects = mutableListOf<String?>()

    var user: User? = null

    abstract fun fetchRecords()

    abstract fun reloadRecord()

    abstract fun populateVideoAnnotation(record: Record)
    abstract fun setupVideoMode(record: Record)
    abstract fun submitVideoAnnotation()
    abstract fun submitVideoJudgement(judgement: Boolean)
    abstract fun startVideoProcessing()
    abstract fun sendCorruptCallback(dataRecordsCorrupt: DataRecordsCorrupt)
    abstract fun deleteCorruptedRecord(recordId: Int?)

    abstract fun submitAnnotation(annotationObjectsAttributes: List<AnnotationObjectsAttribute>)
    abstract fun saveContrastValue(progress: Int)
    abstract fun getContrast(): Int
    abstract fun fetchTemplates()
    abstract fun fetchStaticTemplates()
    abstract fun addNewLabel(labelText: String)
    abstract fun setSelectedTemplate(template: Template?)
    abstract fun isSandbox(): Boolean
    abstract fun shouldPlayHelpVideo(applicationMode: String?): Boolean
    abstract fun submitAnnotationsForBNC(selectedRecords: List<Record>?, selectedTemplate: String)
    abstract fun setupRecordsForBNCMode(records: List<Record>?)
    abstract suspend fun getLearningVideo(applicationMode: String): Video?
    abstract fun setLearningVideoMode()
    abstract fun initVideoDownloadManager(lifecycleOwner: LifecycleOwner)
    abstract fun monitorFrames(frameCount: Int?)
    abstract fun moveToSandBoxState()
    abstract fun saveRecentSearchedTemplate(template: Template)
    abstract fun submitIncorrectSniffingRecords()

    open fun getRecentSearchedTemplate(): MutableList<Template> = mutableListOf()
    open fun processNextVideoRecord() = Unit
    open fun submitJudgement(judgment: Boolean) = Unit
    open fun submitReview(review: Boolean) = Unit
    open fun processNextRecord() = Unit
    open fun undoJudgment() = Unit
    open fun undoAnnotation(): List<AnnotationObjectsAttribute> = emptyList()
    open fun currentRole(): String? = null
    open fun setupUndoRecordState() = Unit
    open fun clearAnswers() = Unit
    open fun isAllAnswersSubmitted(): Boolean = false
    open fun submitSavedAnswers() = Unit
    open fun assignWork() = Unit
    open fun setTrainingId(id: Int) = Unit
    open fun setSandboxRecord(record: Record) = Unit
    open fun resetVideoRecordSandbox() = Unit
    open fun setupInterpolationSandBoxData() = Unit
    var resetViews: ((String?, List<AnnotationData>) -> Unit)? = null

    abstract fun getSpanCount(): Int
    open fun saveSpanCount(count: Int) = Unit
    open fun setSandboxParentAnnotation() = Unit
    open fun hasAnnotatedAllSandboxChildAnnotation(): Boolean = false
    open fun getSandBoxCorrectVideoAnnotationList(): MutableList<VideoAnnotationData> =
        mutableListOf()

    open fun getSandBoxCorrectParentAnnotationDataList(): MutableList<MutableList<VideoAnnotationData>> =
        mutableListOf()

    open fun getSandboxParentAnnotations(): MutableList<VideoAnnotationData>? = mutableListOf()
    open fun getVideoAnnotationsForParentStep(): MutableList<AnnotationObjectsAttribute> =
        mutableListOf()

    fun fetchRecordsFirstTime() {
        _state.value = ProgressState
        fetchRecords()
    }

    fun resetAnnotationsIfExists(toSet: List<AnnotationObjectsAttribute>) {
        val annotationDataList = getAnnotationData().filter { it.objectIndex == activeAnnotationId }
        if (annotationDataList.isNullOrEmpty().not()) {
            activeAnnotationId = null
            setActiveAnnotationState(false)
            setAnnotationObjectiveAttributes(toSet)
        }
    }

    fun setAnnotationObjectiveAttributes(toSet: List<AnnotationObjectsAttribute>) {
        annotationObjectsAttributes.clear()
        annotationObjectsAttributes.addAll(toSet)
    }

    fun getAnnotationData(): List<AnnotationData> = annotationObjectsAttributes.annotations()

    fun isAdminRole() = (role == Role.ADMIN)
    fun isManagerRole() = (currentRole() == Role.MANAGER)

    fun shouldEnableHintButton(): Boolean {
        val applicationMode = applicationMode
        return isAdminRole().not() && listOf(
            Mode.CROP,
            Mode.MULTI_CROP,
            Mode.BINARY_CROP,
            Mode.MCMI,
            Mode.MCML,
            Mode.QUADRILATERAL,
            Mode.POLYGON,
            Mode.PAINT,
            Mode.DRAG_SPLIT
        ).contains(applicationMode)
    }

    fun shouldInitializedOpenCV(): Boolean {
        val applicationMode = applicationMode
        return listOf(
            Mode.CROP,
            Mode.MULTI_CROP,
            Mode.BINARY_CROP,
            Mode.MCMI,
            Mode.MCML,
            Mode.QUADRILATERAL
        ).contains(applicationMode)
    }

    fun setupForNextRecord() {
        if (isSandbox().not()) {
            setAnnotationObjectiveAttributes(emptyList())
            shouldLoadPrevAnnotations = false
            setupUndoRecordState()
            if (mediaType == MediaType.VIDEO) processNextVideoRecord()
            else processNextRecord()
        }
    }

    fun setupForPrevRecord(annotationObjectsAttributes: List<AnnotationObjectsAttribute>) {
        setupUndoRecordState()
        setAnnotationObjectiveAttributes(annotationObjectsAttributes)
        shouldLoadPrevAnnotations = true
    }

    fun setMediaPlaybackStatus(mediaPlaybackStatus: Boolean) {
        _isMediaPlaybackStatus.postValue(mediaPlaybackStatus)
    }

    fun setPreviewStatus(previewModeStatus: Boolean) {
        _isPreviewModeStatus.postValue(previewModeStatus)
    }

    fun enableLandscape(): Boolean {
        return listOf(
            Mode.MCMT
        ).contains(applicationMode)
    }

    fun disabledLandscape(): Boolean {
        return listOf(
            Mode.EVENT_VALIDATION,
            Mode.INPUT,
            Mode.MULTI_INPUT,
            Mode.LP_INPUT,
            Mode.CLASSIFY,
            Mode.DYNAMIC_CLASSIFY,
            Mode.BINARY_CLASSIFY
        ).contains(applicationMode) || workType == WorkType.VALIDATION
    }

    fun setImageSize(width: Int, height: Int) {
        imageWidth = width
        imageHeight = height
    }

    fun extractFrame(playbackScreenshot: Bitmap?, frameCount: Int) {
        annotatingFrame = true
        var videoAnnotationFrame: VideoAnnotationData? = null
        when (videoMode) {
            PARENT_STEP_SANDBOX,
            PARENT_STEP_VIDEO_ANNOTATION -> {
                videoAnnotationFrame = videoAnnotationDataList.find {
                    it.frameCount == frameCount
                }
                if (videoAnnotationFrame == null)
                    videoAnnotationFrame = VideoAnnotationData(frameCount, playbackScreenshot)
                else videoAnnotationFrame.bitmap = playbackScreenshot
            }

            CHILD_STEP_SANDBOX,
            CHILD_STEP_VIDEO_ANNOTATION -> {
                videoAnnotationFrame = parentAnnotations?.find { !it.isParent }
                if (videoAnnotationFrame == null)
                    videoAnnotationFrame = VideoAnnotationData(frameCount, playbackScreenshot)
            }
        }
        videoAnnotationFrame?.let {
            _state.value = VideoAnnotationDataState(it)
        }
    }

    fun addVideoAnnotationData(videoAnnotationData: VideoAnnotationData) {
        viewModelScope.launch {
            when (videoMode) {
                PARENT_STEP_SANDBOX,
                PARENT_STEP_VIDEO_ANNOTATION -> {
                    val annotatedFrame = videoAnnotationDataList.find {
                        it.frameCount == videoAnnotationData.frameCount
                    }
                    if (annotatedFrame == null) {
                        videoAnnotationData.selected = true
                        videoAnnotationData.frameCount?.let { currentVideoFrameCount = it }
                        videoAnnotationDataList.forEach { it.selected = false }
                        videoAnnotationDataList.add(videoAnnotationData)
                    } else {
                        val annotationData = getAnnotationData()
                        if (isInterpolationEnabled()) {
                            // Check whether any crop is updated with its value
                            annotatedFrame.annotations.forEach { earlierAnnotation ->
                                annotationData.find { it.objectIndex == earlierAnnotation.objectIndex }
                                    ?.let {
                                        when {
                                            it.input != earlierAnnotation.input -> {
                                                updateAllCrops(it)
                                            }

                                            it.tags != earlierAnnotation.tags -> {
                                                updateAllCrops(it)
                                            }
                                        }
                                    }
                            }
                            if (isInPreviewMode) annotatedFrame.annotations.clear()
                            annotatedFrame.annotations.addAll(videoAnnotationData.annotations)
                            if (isInPreviewMode) updateAllInterpolatedCrops()
                        } else {
                            if (annotationData.isEmpty()) videoAnnotationDataList.remove(
                                annotatedFrame
                            )
                            else {
                                annotatedFrame.annotations.clear()
                                annotatedFrame.annotations.addAll(videoAnnotationData.annotations)
                            }
                        }
                    }
                    videoAnnotationDataList.sortBy { it.frameCount }

                    _state.value = if (isInterpolationEnabled()) {
                        DrawAnnotationState(
                            annotatedFrame?.annotations ?: videoAnnotationData.annotations,
                            frameCount = videoAnnotationData.frameCount
                        )
                    } else RefreshVideoAnnotationModeState
                }

                CHILD_STEP_SANDBOX,
                CHILD_STEP_VIDEO_ANNOTATION -> {
                    videoAnnotationData.showPreview = true
                    videoAnnotationData.annotations.forEach { annotation ->
                        annotation.parentAnnotation = parentAnnotations?.last()?.rawAnnotation
                    }
                    parentAnnotations?.find { !it.isParent }?.let { parentAnnotations?.remove(it) }
                    parentAnnotations?.add(videoAnnotationData)
                    inChildParentAssociationMode = false
                    parentAnnotations = null
                    _state.value = RefreshVideoAnnotationModeState
                }
            }
        }
    }

    private fun updateAllCrops(annotationData: AnnotationData) {
        videoAnnotationDataList.forEach { videoAnnotationData ->
            videoAnnotationData.annotations.find { it.objectIndex == annotationData.objectIndex }
                ?.let {
                    it.input = annotationData.input
                    it.tags = annotationData.tags
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

    fun checkMinimumCriteria(): Int {
        return videoAnnotationDataList.flatMap { it.annotations }.count {
            it.objectIndex == activeAnnotationId
        }
    }

    fun discardCurrentAnnotation() {
        videoAnnotationDataList.map { annotationData ->
            annotationData.annotations.find {
                it.objectIndex == activeAnnotationId
            }?.let {
                annotationData.annotations.remove(it)
            }
        }
        activeAnnotationId = null
        setActiveAnnotationState(false)
    }

    private fun updateAllInterpolatedCrops() {
        val allHumanEnabledAnnotations =
            videoAnnotationDataList.flatMap { it.annotations }
                .filter { it.annotationState == AnnotationState.MANUAL }
        val distinctAnnotations =
            allHumanEnabledAnnotations.distinctBy { it.objectIndex }.map { it.objectIndex }
        distinctAnnotations.forEach { objectId ->
            val annotationSegments =
                allHumanEnabledAnnotations.filter { it.objectIndex == objectId }
                    .sortedBy { it.frameCount }
            val interpolatedAnnotations =
                (getInterpolatedAnnotationsPerFrame(objectId, annotationSegments)
                        + annotationSegments).sortedBy { it.frameCount }
            enterAllInterpolatedValues(false, interpolatedAnnotations)
        }
    }

    fun interpolateObject() {
        val filteredActiveAnnotations = videoAnnotationDataList.flatMap { it.annotations }
            .filter {
                it.objectIndex == activeAnnotationId
            }.sortedBy { it.frameCount }
        val interpolatedAnnotations =
            (getInterpolatedAnnotationsPerFrame(activeAnnotationId, filteredActiveAnnotations)
                    + filteredActiveAnnotations).sortedBy { it.frameCount }
        enterAllInterpolatedValues(true, interpolatedAnnotations)
        if (distinctDataObjects.isNullOrEmpty().not())
            distinctDataObjects.remove(activeSandboxAnnotationId)
        activeAnnotationId = null
        setActiveAnnotationState(false)
    }

    private fun enterAllInterpolatedValues(
        isFirstTime: Boolean = false,
        interpolatedAnnotations: List<AnnotationData>
    ) {
        try {
            interpolatedAnnotations.forEach { annotationData ->
                val videoAnnotationData =
                    videoAnnotationDataList.find { annotationData.frameCount == it.frameCount }
                if (videoAnnotationData == null) {
                    videoAnnotationDataList.add(
                        VideoAnnotationData(
                            frameCount = annotationData.frameCount,
                            annotations = mutableListOf(annotationData),
                            bitmap = null,
                        )
                    )
                } else {
                    videoAnnotationData.annotations.find {
                        it.objectIndex == annotationData.objectIndex
                    }?.let {
                        it.points = annotationData.points
                    } ?: run {
                        if (isFirstTime) videoAnnotationData.annotations.add(annotationData)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getInterpolatedAnnotationsPerFrame(
        activeAnnotationId: String?,
        annotationDataList: List<AnnotationData>
    ): List<AnnotationData> {
        val annotations = mutableListOf<AnnotationData>()
        annotationDataList.forEachIndexed { index, currentAnnotation ->
            if (index != annotationDataList.size - 1) {
                val endingAnnotation = annotationDataList[index + 1]
                val startPoints = currentAnnotation.points ?: emptyList()
                val endPoints = endingAnnotation.points ?: emptyList()
                if (startPoints.isEmpty() || endPoints.isEmpty()) return emptyList()
                val startingCount = currentAnnotation.frameCount
                val endingCount = endingAnnotation.frameCount
                if (startingCount == null || endingCount == null) return emptyList()
                val totalSegments = endingCount - startingCount
                for (segment in 1 until totalSegments) {
                    val midLeft =
                        (startPoints.first().first() +
                                ((endPoints.first().first() - startPoints.first()
                                    .first()) / totalSegments)
                                * segment)
                    val midTop =
                        (startPoints.first().last() +
                                ((endPoints.first().last() - startPoints.first()
                                    .last()) / totalSegments)
                                * segment)
                    val midRight =
                        (startPoints.last().first() +
                                ((endPoints.last().first() - startPoints.last()
                                    .first()) / totalSegments)
                                * segment)
                    val midBottom =
                        (startPoints.last().last() +
                                ((endPoints.last().last() - startPoints.last()
                                    .last()) / totalSegments)
                                * segment)
                    val points = mutableListOf(
                        arrayListOf(midLeft, midTop),
                        arrayListOf(midRight, midBottom)
                    )
                    val annotationData = AnnotationData(
                        objectIndex = activeAnnotationId,
                        objectName = currentAnnotation.objectName,
                        points = points,
                        type = DrawType.BOUNDING_BOX,
                        input = currentAnnotation.input,
                        tags = currentAnnotation.tags,
                        frameCount = startingCount + segment,
                        paintColor = currentAnnotation.paintColor,
                        annotationState = AnnotationState.INTERPOLATION
                    )
                    annotations.add(annotationData)
                }
            }
        }
        return annotations
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

    fun previewAnnotatedFrame(videoAnnotationData: VideoAnnotationData) {
        when (videoMode) {
            PARENT_STEP_SANDBOX,
            PARENT_STEP_VIDEO_ANNOTATION -> {
                videoAnnotationDataList.forEach { it.selected = videoAnnotationData == it }
                _state.value = VideoAnnotationDataState(videoAnnotationData)
            }

            CHILD_STEP_SANDBOX,
            CHILD_STEP_VIDEO_ANNOTATION -> {
                _state.value = VideoAnnotationDataState(videoAnnotationData)
            }
        }
    }

    fun clearAnnotatedFrame(frameCount: Int?) {
        videoAnnotationDataList.find { it.frameCount == frameCount }?.let {
            videoAnnotationDataList.remove(it)
        }
        _state.value = RefreshVideoAnnotationModeState
    }

    fun clearChildAnnotatedFrame() {
        parentAnnotations?.find { !it.isParent }?.let {
            parentAnnotations?.remove(it)
        }
        exitChildAnnotationMode()
        _state.value = RefreshVideoAnnotationModeState
    }

    fun hasVideoAnnotationData(): Boolean {
        return when (videoMode) {
            PARENT_STEP_VIDEO_VALIDATION,
            PARENT_STEP_VIDEO_ANNOTATION -> {
                videoAnnotationDataList.isEmpty()
            }

            CHILD_STEP_VIDEO_ANNOTATION,
            CHILD_STEP_VIDEO_VALIDATION -> {
                videoParentAnnotationDataList.isEmpty()
            }

            else -> false
        }
    }

    fun selectVideoFrame(selectedFrame: VideoAnnotationData) {
        when (videoMode) {
            PARENT_STEP_VIDEO_VALIDATION,
            PARENT_STEP_VIDEO_ANNOTATION -> {
                videoAnnotationDataList.forEach {
                    it.selected = it == selectedFrame
                }
                _state.value = RefreshVideoAnnotationModeState
            }

            CHILD_STEP_VIDEO_ANNOTATION -> {
            }

            CHILD_STEP_VIDEO_VALIDATION -> {
            }
        }
    }

    fun annotateChildWithRespectToParent(videoAnnotationData: VideoAnnotationData): MutableList<VideoAnnotationData>? {
        inChildParentAssociationMode = true
        videoParentAnnotationDataList.forEach { stepAnnotations ->
            stepAnnotations.forEach {
                if (it == videoAnnotationData) parentAnnotations = stepAnnotations
            }
        }
        if (videoMode == CHILD_STEP_SANDBOX) setSandboxParentAnnotation()
        return parentAnnotations
    }

    fun isInChildParentAssociationMode() = inChildParentAssociationMode
    fun exitChildAnnotationMode() {
        inChildParentAssociationMode = false
        parentAnnotations = null
    }

    fun getLastParentAnnotationHint(): VideoAnnotationData? {
        return parentAnnotations?.find { it.isParent }
    }

    fun hasAnnotatedChildAnnotation(): Boolean {
        videoParentAnnotationDataList.map { stepAnnotations ->
            stepAnnotations.find { !it.isParent }?.let { return true }
        }
        return false
    }

    protected fun getAnnotationAttribute(
        annotationData: AnnotationData,
        frameCount: Int?
    ): AnnotationObjectsAttribute {
        annotationData.frameCount = frameCount
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        return AnnotationObjectsAttribute(AnnotationValue(answer = gson.toJson(annotationData)))
    }

    fun getVideoModeState(): Int = videoMode

    protected fun setupVideoParentAnnotationList(annotations: CurrentAnnotation?, childStep: Int) {
        videoParentAnnotationDataList.clear()
        annotations.videoAnnotations().forEach { stepAnnotations ->
            val stepAnnotationList = mutableListOf<VideoAnnotationData>()
            stepAnnotations.forEachIndexed { index, stepInfo ->
                if (stepInfo.type == DrawType.JUNK) {
                    if (workType == WorkType.VALIDATION) {
                        stepAnnotationList.forEach { it.isJunk = true }
                    }
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

    fun setInitialState() {
        _state.value = InitialState
    }

    protected fun validImageAnnotation(
        annotationObjectsAttributes: List<AnnotationObjectsAttribute>,
        currentRecord: Record?,
        isSniffing: Boolean = false
    ): Boolean {
        if (currentRecord == null) return false
        if (annotationObjectsAttributes.isNullOrEmpty()) return false

        val correctAnswer = if (isSniffing) currentRecord.answer()
        else currentRecord.annotation?.answer()
        val userAnswer = annotationObjectsAttributes.firstOrNull()?.annotationValue?.answer

        val isUserAnswerJunk = (userAnswer == Judgment.JUNK)
        if (isUserAnswerJunk) return (correctAnswer == userAnswer)
        else {
            val drawType: String?
            val correctAnnotations: List<AnnotationData>?
            if (isSniffing) {
                drawType = currentRecord.drawType()
                correctAnnotations = currentRecord.annotations()
            } else {
                drawType = currentRecord.annotation?.drawType()
                correctAnnotations = currentRecord.annotation.annotations()
            }
            return when (drawType) {
                DrawType.BOUNDING_BOX -> {
                    CropMatcher.matchAnnotations(
                        annotationObjectsAttributes.annotations(),
                        correctAnnotations,
                        imageWidth,
                        imageHeight,
                        annotationVariationThreshold
                    )
                }

                DrawType.QUADRILATERAL, DrawType.POLYGON -> {
                    PathMatcher.matchAnnotations(
                        annotationObjectsAttributes.annotations(),
                        correctAnnotations,
                        imageWidth,
                        imageHeight,
                        annotationVariationThreshold
                    )
                }

                DrawType.CONNECTED_LINE -> {
                    LaneMatcher.matchAnnotations(
                        annotationObjectsAttributes.annotations(),
                        correctAnnotations,
                        annotationVariationThreshold
                    )
                }

                DrawType.SPLIT_BOX -> {
                    DragSplitMatcher.matchAnnotations(
                        annotationObjectsAttributes.annotations(),
                        correctAnnotations,
                        annotationVariationThreshold
                    )
                }

                else -> correctAnswer.equals(userAnswer, ignoreCase = true)
            }
        }
    }

    fun setOpenCvInitialized(initialized: Boolean) {
        _isOpenCvInitialized.value = initialized
    }

    fun resetVideoModeData() {
        parentAnnotations?.clear()
        videoAnnotationDataList.clear()
        videoParentAnnotationDataList.clear()
    }

    fun setActiveAnnotationState(toSet: Boolean) {
        _activeAnnotationObserver.postValue(toSet)
    }

    fun isInterpolationEnabled() = (isInterpolatedMCML() || isInterpolatedMCMT())
    fun isInterpolatedMCML() = (applicationMode == INTERPOLATED_MCML)
    fun isInterpolatedMCMT() = (applicationMode == INTERPOLATED_MCMT)

    object RecordsFinishedState : ActivityState()
    object RecordDeleteState : ActivityState()
    class TemplatesFailedState(val message: String?) : ActivityState()
    class TemplatesSuccessState(
        val addedLabel: String? = null,
        val templates: List<Template>
    ) : ActivityState()

    class VideoAnnotationDataState(val videoAnnotationData: VideoAnnotationData) : ActivityState()
    object RefreshVideoAnnotationModeState : ActivityState()
    object SniffingIncorrectWarningState : ActivityState()

    data class AccountLockedState(
        val incorrectSniffing: ArrayList<Record>,
        val isAdmin: Boolean,
        val isAccountLocked: Boolean
    ) : ActivityState()

    data class DrawAnnotationState(
        val annotations: MutableList<AnnotationData>,
        val frameCount: Int?
    ) : ActivityState()

    object ClearAnnotationState : ActivityState()

    data class DownloadingFailedState(
        val isSandBox: Boolean?,
        val dataRecordsCorrupt: DataRecordsCorrupt
    ) : ActivityState()

    data class FrameExtractionFailedState(
        val dataRecordsCorrupt: DataRecordsCorrupt
    ) : ActivityState()
}

class VideoDownloadProvider @Inject constructor(@ApplicationContext private val context: Context) {
    fun provide(
        videoDownloadListener: VideoDownloadListener
    ): VideoDownloadManager {
        return VideoDownloadManager(context, videoDownloadListener)
    }
}