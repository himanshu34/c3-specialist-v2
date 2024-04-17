package co.nayan.canvas.viewmodels

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationState
import co.nayan.c3v2.core.models.DataRecordsCorrupt
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.SandboxVideoAnnotationData
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.models.c3_module.requests.SandboxRefreshDataRequest
import co.nayan.c3v2.core.models.c3_module.requests.SandboxSubmitAnnotationRequest
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.answer
import co.nayan.c3views.utils.drawType
import co.nayan.c3views.utils.videoAnnotations
import co.nayan.canvas.config.TrainingStatus
import co.nayan.canvas.interfaces.SandboxRepositoryInterface
import co.nayan.canvas.sandbox.models.FilteredAnswers
import co.nayan.canvas.sandbox.models.LearningImageData
import co.nayan.canvas.sandbox.utils.CropMatcher
import co.nayan.canvas.sandbox.utils.DragSplitMatcher
import co.nayan.canvas.sandbox.utils.LaneMatcher
import co.nayan.canvas.sandbox.utils.PathMatcher
import co.nayan.canvas.utils.FFMPegExtraction
import co.nayan.canvas.utils.ImageCachingManager
import co.nayan.canvas.utils.OnFFMPegExtractionListener
import co.nayan.canvas.utils.VideoDownloadListener
import co.nayan.canvas.utils.getBitmapFromDirectory
import co.nayan.canvas.utils.notifyObservers
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_SANDBOX
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_SANDBOX
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_VIDEO_ANNOTATION
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SandboxViewModel @Inject constructor(
    private val sandboxRepository: SandboxRepositoryInterface,
    private val imageCachingManager: ImageCachingManager,
    private val videoDownloadProvider: VideoDownloadProvider,
    private val ffmPegExtraction: FFMPegExtraction
) : BaseCanvasViewModel() {

    init {
        Timber.d("Initiating SandboxViewModel")
    }

    private var sandboxTrainingId: Int = 0
    private var currentRecord: Record? = null

    private var sandBoxCorrectAnnotationDataList: MutableList<VideoAnnotationData> = mutableListOf()
    private val sandBoxVideoAnnotationResultList = mutableListOf<SandboxVideoAnnotationData>()
    private var sandboxParentAnnotations: MutableList<VideoAnnotationData>? = null
    private val sandBoxCorrectParentAnnotationDataList =
        mutableListOf<MutableList<VideoAnnotationData>>()

    override fun setTrainingId(id: Int) {
        sandboxTrainingId = id
    }

    override fun fetchRecords() {
        if (_records.value.isNullOrEmpty()) {
            if (role == Role.ADMIN) fetchRecordsForAdmin()
            else fetchRecordsForSpecialist()
        } else _state.value = InitialState
        areRecordsFetched = true
    }

    private fun fetchRecordsForSpecialist() {
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                val response = sandboxRepository.specialistNextRecord(sandboxTrainingId)
                val newRecords = response?.records
                requiredStreak = response?.requiredCount
                _sandboxCurrentStreak.value = response?.streak ?: 0
                if (!newRecords.isNullOrEmpty()) {
                    currentRecord = newRecords.first()
                    if (mediaType == MediaType.VIDEO) {
                        _state.value = InitialState
                        startVideoProcessing()
                    } else _record.value = currentRecord
                }
                setupTrainingStatus(response?.trainingStatus, response?.records)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                Timber.e(e)
                _state.value = ErrorState(e)
            }
        }
    }

    override fun startVideoProcessing() {
        if (role == Role.ADMIN) {
            _records.value?.let {
                if (it.isNotEmpty()) {
                    _foregroundVideoDownloading.value = true
                    _downloadingProgress.value = 0
                    startProcessing(it.first())
                }
            }
        } else {
            currentRecord?.let {
                _foregroundVideoDownloading.value = true
                _downloadingProgress.value = 0
                startProcessing(it)
            }
        }
    }

    override fun sendCorruptCallback(dataRecordsCorrupt: DataRecordsCorrupt) {

    }

    override fun deleteCorruptedRecord(recordId: Int?) {

    }

    private fun startProcessing(record: Record) {
        videoDownloadManager?.deleteVideos()
        videoDownloadManager?.downloadRecord(viewModelScope, record, isSandBox = isSandbox())
    }

    private fun setupTrainingStatus(
        status: String?, records: List<Record>?, isCorrectAnswer: Boolean = false
    ) {
        when (status) {
            TrainingStatus.IN_PROGRESS -> {
                _state.value = if (isCorrectAnswer) CorrectAnnotationState else InitialState
            }

            TrainingStatus.SUCCESS -> {
                _state.value = SandboxSuccessState
            }

            TrainingStatus.FAILED -> {
                _state.value = SandboxFailedState
            }

            else -> {
                _state.value = if (records.isNullOrEmpty()) RecordsFinishedState else InitialState
            }
        }
    }

    /**
     * Add new records if they are not already present in the local list
     */
    private fun addNewRecords(newRecords: List<Record>) {
        _records.value?.let {
            for (newRecord in newRecords) {
                if (!it.contains(newRecord)) {
                    it.add(newRecord)
                }
            }
        }
    }

    override fun reloadRecord() {
        if (currentRecord != null) {
            _record.value = currentRecord
        }
    }

    override fun submitAnnotation(annotationObjectsAttributes: List<AnnotationObjectsAttribute>) {
        if (role == Role.ADMIN) submitAdminAnnotation(annotationObjectsAttributes)
        else submitSpecialistAnnotation(annotationObjectsAttributes)
    }

    private fun submitSpecialistAnnotation(annotationObjectsAttributes: List<AnnotationObjectsAttribute>) {
        viewModelScope.launch {
            val isValidAnnotation = validAnnotation(annotationObjectsAttributes)
            if (isValidAnnotation.not()) {
                if (mediaType == MediaType.IMAGE) {
                    val learningImageData = setupLearningImageState(annotationObjectsAttributes)
                    _state.value = LearningImageSetupState(learningImageData)
                } else {
                    _state.value = if (isInterpolationEnabled()) {
                        sandBoxVideoAnnotationResultList.find { it.judgement.not() }?.let {
                            VideoModeInterpolationResultState(it, annotationObjectsAttributes)
                        } ?: run { VideoModeNextSandboxState }
                    } else VideoModeSandboxResultState(
                        sandBoxVideoAnnotationResultList,
                        annotationObjectsAttributes
                    )
                }
            } else if (isInterpolationEnabled() && distinctDataObjects.isNullOrEmpty().not()) {
                _state.value = VideoModeNextSandboxState
            } else submitAndFetchNext(isValidAnnotation, annotationObjectsAttributes)
        }
    }

    fun submitAndFetchNext(
        isValidAnnotation: Boolean,
        annotationObjectsAttributes: List<AnnotationObjectsAttribute>
    ) {
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                val recordId = _record.value?.id ?: 0
                val request = SandboxSubmitAnnotationRequest(
                    sandboxRecordId = recordId,
                    annotation = annotationObjectsAttributes,
                    status = isValidAnnotation.status()
                )
                val response =
                    sandboxRepository.submitSpecialistAnnotation(sandboxTrainingId, request)
                if (response != null) {
                    resetVideoRecordSandbox()

                    _sandboxCurrentStreak.value = response.streak ?: 0

                    val newRecords = response.records
                    if (!newRecords.isNullOrEmpty()) {
                        setAnnotationObjectiveAttributes(emptyList())
                        shouldLoadPrevAnnotations = false
                        currentRecord = newRecords.first()
                        if (mediaType == MediaType.VIDEO) startVideoProcessing()
                        else _record.value = currentRecord
                    }
                    setupTrainingStatus(
                        response.trainingStatus,
                        response.records,
                        isValidAnnotation
                    )
                } else _state.value = RecordSubmissionFailedState
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                Timber.e(e)
                _state.value = ErrorState(e)
            }
        }
    }

    private fun setupLearningImageState(annotationObjectsAttributes: List<AnnotationObjectsAttribute>): LearningImageData {
        val userAnnotations = annotationObjectsAttributes.annotations()
        val correctAnnotations = currentRecord?.annotation.annotations()

        val correctAnswer = currentRecord?.annotation.answer()
        val incorrectAnswer =
            annotationObjectsAttributes.firstOrNull()?.annotationValue?.answer ?: ""

        val learningImageData =
            when (currentRecord?.annotation?.drawType() ?: annotationObjectsAttributes.drawType()) {
                DrawType.BOUNDING_BOX -> {
                    val filteredAnnotations = CropMatcher.filteredAnnotations(
                        userAnnotations,
                        correctAnnotations,
                        imageWidth,
                        imageHeight,
                        annotationVariationThreshold
                    )
                    LearningImageData(filteredAnnotations, null, currentRecord)
                }

                DrawType.QUADRILATERAL, DrawType.POLYGON -> {
                    val filteredAnnotations = PathMatcher.filteredAnnotations(
                        userAnnotations,
                        correctAnnotations,
                        imageWidth,
                        imageHeight,
                        annotationVariationThreshold
                    )
                    LearningImageData(filteredAnnotations, null, currentRecord)
                }

                DrawType.CONNECTED_LINE -> {
                    val filteredAnnotations = LaneMatcher.filteredAnnotations(
                        userAnnotations, correctAnnotations, annotationVariationThreshold
                    )
                    LearningImageData(filteredAnnotations, null, currentRecord)
                }

                DrawType.SPLIT_BOX -> {
                    val filteredAnnotations = DragSplitMatcher.filteredAnnotations(
                        userAnnotations, correctAnnotations, annotationVariationThreshold
                    )
                    LearningImageData(filteredAnnotations, null, currentRecord)
                }

                else -> {
                    val filteredAnswers = FilteredAnswers(correctAnswer, incorrectAnswer)
                    LearningImageData(null, filteredAnswers, currentRecord)
                }
            }

        if (learningImageData.filteredAnswers == null) {
            if (correctAnswer == Judgment.JUNK) {
                learningImageData.filteredAnswers = FilteredAnswers(correctAnswer, "")
            }
            if (incorrectAnswer == Judgment.JUNK) {
                learningImageData.filteredAnswers = FilteredAnswers("", incorrectAnswer)
            }
        }

        return learningImageData
    }

    private fun validAnnotation(annotationObjectsAttributes: List<AnnotationObjectsAttribute>): Boolean {
        return if (mediaType == MediaType.IMAGE)
            validImageAnnotation(annotationObjectsAttributes, currentRecord)
        else validVideoAnnotation()
    }

    private fun validVideoAnnotation(): Boolean {
        sandBoxVideoAnnotationResultList.clear()
        when (videoMode) {
            PARENT_STEP_SANDBOX -> {
                sandBoxCorrectAnnotationDataList.forEach { correctAnnotation ->
                    val correctAnnotations = if (isInterpolationEnabled())
                        correctAnnotation.annotations.filter {
                            it.objectIndex == activeSandboxAnnotationId
                                    && it.annotationState == AnnotationState.MANUAL
                        }
                    else correctAnnotation.annotations

                    if (correctAnnotations.isNullOrEmpty().not()) {
                        if (correctAnnotation.bitmap == null) {
                            correctAnnotation.bitmap = getBitmapFromDirectory(
                                totalFrames, correctAnnotation.frameCount ?: 0, framesDir
                            )
                        }

                        sandBoxVideoAnnotationResultList.add(
                            SandboxVideoAnnotationData(
                                correctAnnotation.frameCount,
                                correctAnnotation
                            )
                        )
                    }
                }

                sandBoxVideoAnnotationResultList.forEach { sandboxResult ->
                    videoAnnotationDataList.find { it.frameCount == sandboxResult.frameCount }
                        ?.let { userAnnotation ->
                            if (isInterpolationEnabled() && userAnnotation.bitmap == null)
                                userAnnotation.bitmap = sandboxResult.correctVideoAnnotation.bitmap

                            sandboxResult.userVideoAnnotation = userAnnotation
                            sandboxResult.judgement = matchVideoAnnotations(
                                sandboxResult.correctVideoAnnotation,
                                userAnnotation
                            )
                        }
                }
            }

            CHILD_STEP_SANDBOX -> {
                sandBoxCorrectParentAnnotationDataList.forEach { stepAnnotations ->
                    val correctAnnotation = stepAnnotations.last()
                    sandBoxVideoAnnotationResultList.add(
                        SandboxVideoAnnotationData(
                            correctAnnotation.frameCount,
                            correctAnnotation
                        )
                    )
                }
                sandBoxVideoAnnotationResultList.forEach { sandboxResult ->
                    videoParentAnnotationDataList.forEach { stepAnnotation ->
                        val userAnnotation = stepAnnotation.last()
                        if (!userAnnotation.isParent &&
                            !userAnnotation.isConsiderForChildSandboxJudgment &&
                            userAnnotation.frameCount == sandboxResult.frameCount &&
                            sandboxResult.userVideoAnnotation == null
                        ) {
                            sandboxResult.userVideoAnnotation = userAnnotation
                            userAnnotation.isConsiderForChildSandboxJudgment = true
                            sandboxResult.judgement = matchVideoAnnotations(
                                sandboxResult.correctVideoAnnotation,
                                userAnnotation
                            )
                        }
                    }
                }
            }
        }
        return sandBoxVideoAnnotationResultList.find { !it.judgement } == null
    }

    override fun saveContrastValue(progress: Int) {
        sandboxRepository.saveContrast(progress)
    }

    override fun getContrast(): Int {
        return sandboxRepository.getContrast()
    }

    /**
     * Return templates for classification modes
     */
    override fun fetchTemplates() {
        viewModelScope.launch {
            try {
                _templateState.value = ProgressState
                val templates = sandboxRepository.fetchTemplates(wfStepId)
                _templateState.value = TemplatesSuccessState(null, templates)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _templateState.value = ErrorState(e)
            }
        }
    }

    /**
     * Return templates for event validation mode
     */
    override fun fetchStaticTemplates() {
        val templates: MutableList<Template> = mutableListOf()
        viewModelScope.launch {
            try {
                _templateState.value = ProgressState
                templates.add(
                    Template(
                        templateName = "Yes",
                        templateIcon = "https://storage.googleapis.com/c3-data-public/c3-data-public/c3/uploads/templates/yes.png"
                    )
                )
                templates.add(
                    Template(
                        templateName = "No",
                        templateIcon = "https://storage.googleapis.com/c3-data-public/c3-data-public/c3/uploads/templates/no.png"
                    )
                )
                _templateState.value = TemplatesSuccessState(null, templates)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _templateState.value = ErrorState(e)
            }
        }
    }

    override fun addNewLabel(labelText: String) {
        viewModelScope.launch {
            try {
                _templateState.value = ProgressState
                val response =
                    sandboxRepository.addLabel(wfStepId, _record.value?.displayImage, labelText)
                response.let {
                    if (response.second != null)
                        _templateState.value =
                            TemplatesSuccessState(labelText, response.second ?: emptyList())
                    else _templateState.value = TemplatesFailedState(response.first)
                }
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _templateState.value = ErrorState(e)
            }
        }
    }

    override fun setSelectedTemplate(template: Template?) {
        _template.value = template
    }

    override fun isSandbox() = true
    override fun shouldPlayHelpVideo(applicationMode: String?): Boolean {
        return applicationMode?.let {
            sandboxRepository.shouldPlayHelpVideo(it)
        } ?: run { true }
    }

    override fun setupRecordsForBNCMode(records: List<Record>?) {
        records?.let { addNewRecords(it) }
        if (_records.value.isNullOrEmpty())
            _state.value = RecordsFinishedState
        else {
            _records.notifyObservers()
            if (_state.value == ProgressState) _state.value = InitialState
        }
    }

    override fun submitAnnotationsForBNC(selectedRecords: List<Record>?, selectedTemplate: String) {
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                /*val annotations = getBNCAnnotations(selectedRecords, selectedTemplate)
                sandboxRepository.submitBNCAnnotations(annotations)
                _records.value?.removeAll(selectedRecords ?: emptyList())*/
                _records.notifyObservers()
                _state.value =
                    if (_records.value.isNullOrEmpty()) SandboxSuccessState else InitialState
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    private fun fetchRecordsForAdmin() {
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                val response = sandboxRepository.adminRecords(sandboxTrainingId)
                val newRecords =
                    response?.sandboxRecords?.sortedByDescending { it.annotation?.size }
                if (!newRecords.isNullOrEmpty()) {
                    addNewRecords(newRecords)
                    currentRecord = newRecords.first()
                    if (mediaType == MediaType.VIDEO) startVideoProcessing()
                    else _record.value = currentRecord
                }
                _records.notifyObservers()
                _state.value = if (_records.value.isNullOrEmpty())
                    RecordsFinishedState else InitialState
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                Timber.e(e)
                _state.value = ErrorState(e)
            }
        }
    }

    private fun submitAdminAnnotation(annotationObjectsAttributes: List<AnnotationObjectsAttribute>) {
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                val recordId = _record.value?.id ?: 0
                val request =
                    SandboxSubmitAnnotationRequest(null, annotationObjectsAttributes, null)
                val response = sandboxRepository.submitAdminAnnotation(recordId, request)
                if (response.success == true) {
                    if (isSandboxCreationMode) {
                        val refreshDataRequest = if (isInterpolationEnabled().not())
                            SandboxRefreshDataRequest(recordId, annotationObjectsAttributes)
                        else SandboxRefreshDataRequest(
                            recordId,
                            listOf(annotationObjectsAttributes.first())
                        )
                        sandboxRefreshDataRequest = refreshDataRequest
                    }
                    setAnnotationObjectiveAttributes(emptyList())
                    shouldLoadPrevAnnotations = false
                    if (mediaType == MediaType.VIDEO) {
                        _state.value = InitialState
                        processNextVideoRecord()
                    } else {
                        _records.value?.removeAll { it.id == recordId }
                        processNextRecord()
                    }
                } else _state.value = RecordSubmissionFailedState
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                Timber.e(e)
                _state.value = RecordSubmissionFailedState
            }
        }
    }

    override suspend fun getLearningVideo(applicationMode: String): Video? {
        return sandboxRepository.getLearningVideo(applicationMode)
    }

    override fun setLearningVideoMode() {
        _learningVideoMode.value = applicationMode
    }

    override fun getSandBoxCorrectVideoAnnotationList() = sandBoxCorrectAnnotationDataList

    override fun getSandBoxCorrectParentAnnotationDataList() =
        sandBoxCorrectParentAnnotationDataList

    override fun setupVideoMode(record: Record) {
        annotateForChildStepInVideoMode = record.parentAnnotation != null
        videoMode = if (workType == WorkType.ANNOTATION
            && !annotateForChildStepInVideoMode && isSandbox() && !isAdminRole()
        ) {
            PARENT_STEP_SANDBOX
        } else if (workType == WorkType.ANNOTATION
            && annotateForChildStepInVideoMode && isSandbox() && !isAdminRole()
        ) {
            CHILD_STEP_SANDBOX
        } else if (workType == WorkType.ANNOTATION
            && !annotateForChildStepInVideoMode && isSandbox() && isAdminRole()
        ) {
            PARENT_STEP_VIDEO_ANNOTATION
        } else if (workType == WorkType.ANNOTATION
            && annotateForChildStepInVideoMode && isSandbox() && isAdminRole()
        ) {
            CHILD_STEP_VIDEO_ANNOTATION
        } else {
            -1
        }
    }

    override fun populateVideoAnnotation(record: Record) {
        setupVideoMode(record)
        when (videoMode) {
            PARENT_STEP_VIDEO_ANNOTATION -> {
                videoAnnotationDataList.clear()
                val videoAnnotationMap = HashMap<Int, VideoAnnotationData?>()
                record.annotation.annotations().forEach { annotation ->
                    annotation.frameCount?.let { fc ->
                        val videoAnnotationData = if (videoAnnotationMap.containsKey(fc))
                            videoAnnotationMap[fc] else VideoAnnotationData(fc, bitmap = null)
                        annotation.frameCount = fc
                        videoAnnotationData?.annotations?.add(annotation)
                        videoAnnotationMap[fc] = videoAnnotationData
                    }
                }
                for ((_, value) in videoAnnotationMap) {
                    value?.let { videoAnnotationDataList.add(it) }
                }
                videoAnnotationDataList.sortBy { it.frameCount }
            }

            CHILD_STEP_VIDEO_ANNOTATION -> {
                parentAnnotations = null
                videoParentAnnotationDataList.clear()
                val childStep = record.parentAnnotation.videoAnnotations()[0].size + 1
                if (record.annotation == null) {
                    setupVideoParentAnnotationList(record.parentAnnotation, childStep)
                } else {
                    setupChildAnnotationList(record.annotation, childStep)
                }
            }

            PARENT_STEP_SANDBOX -> {
                videoAnnotationDataList.clear()
                sandBoxCorrectAnnotationDataList.clear()
                distinctDataObjects.clear()
                sandBoxInterpolationDataList.clear()
                val videoAnnotationMap = HashMap<Int, VideoAnnotationData?>()
                val annotations = record.annotation.annotations()
                if (isInterpolationEnabled()) {
                    distinctDataObjects = annotations.distinctBy { it.objectIndex }
                        .map { it.objectIndex }.toMutableList()
                    distinctDataObjects.forEach { objectId ->
                        val interpolatedFrames = annotations.filter { it.objectIndex == objectId }
                            .sortedBy { it.frameCount }
                        if (interpolatedFrames.isNullOrEmpty().not()) {
                            sandBoxInterpolationDataList.add(interpolatedFrames.first())
                            sandBoxInterpolationDataList.add(interpolatedFrames.last())
                        }
                    }
                }
                annotations.forEach { annotation ->
                    annotation.frameCount?.let { frameCount ->
                        val videoAnnotationData = if (videoAnnotationMap.containsKey(frameCount))
                            videoAnnotationMap[frameCount]
                        else VideoAnnotationData(frameCount, bitmap = null)
                        annotation.frameCount = frameCount
                        videoAnnotationData?.annotations?.add(annotation)
                        videoAnnotationMap[frameCount] = videoAnnotationData
                    }
                }
                for ((_, value) in videoAnnotationMap) {
                    value?.let { sandBoxCorrectAnnotationDataList.add(it) }
                }
                sandBoxCorrectAnnotationDataList.sortBy { it.frameCount }
            }

            CHILD_STEP_SANDBOX -> {
                parentAnnotations = null
                sandboxParentAnnotations = null
                videoParentAnnotationDataList.clear()
                sandBoxCorrectParentAnnotationDataList.clear()
                val childStep = record.parentAnnotation.videoAnnotations()[0].size + 1
                setupVideoParentAnnotationList(record.parentAnnotation, childStep)
                setupChildAnnotationList(record.annotation, childStep)
            }
        }
    }

    private fun setupChildAnnotationList(
        videoAnnotation: List<AnnotationObjectsAttribute>?,
        childStep: Int
    ) {
        videoAnnotation.videoAnnotations().forEach { stepAnnotations ->
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
            if (isAdminRole()) videoParentAnnotationDataList.add(stepAnnotationList)
            else sandBoxCorrectParentAnnotationDataList.add(stepAnnotationList)
        }
    }

    override fun setupInterpolationSandBoxData() {
        activeSandboxAnnotationId = if (distinctDataObjects.isNullOrEmpty().not())
            distinctDataObjects.first() else null
    }

    override fun monitorFrames(frameCount: Int?) {
        when (videoMode) {
            PARENT_STEP_VIDEO_ANNOTATION -> {
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
                                    it.selected = it.frameCount == highlightFrame.frameCount
                                }
                                _state.value = RefreshVideoAnnotationModeState
                            }
                        }
                    }
                }
            }

            CHILD_STEP_VIDEO_ANNOTATION -> {
                frameCount?.let { fc ->
                    videoParentAnnotationDataList.forEach { stepAnnotations ->
                        stepAnnotations.forEach {
                            if (it.bitmap == null && fc == it.frameCount) {
                                currentVideoFrameCount = it.frameCount ?: 1
                                _state.value = RefreshVideoAnnotationModeState
                            }
                        }
                    }
                }
            }

            PARENT_STEP_SANDBOX -> {
                frameCount?.let { fc ->
                    currentVideoFrameCount = fc
                    if (isInterpolationEnabled()) {
                        val highlightFrame = videoAnnotationDataList.find { it.frameCount == fc }
                        if (isInPreviewMode.not()) {
                            sandBoxInterpolationDataList.find {
                                (activeSandboxAnnotationId == it.objectIndex) && (fc == it.frameCount)
                            }?.let {
                                sandBoxCorrectAnnotationDataList.forEach {
                                    it.selected = (fc == it.frameCount)
                                }
                                _state.value = ToggleScreenCaptureState(true)
                            } ?: run {
                                _state.value = if (highlightFrame == null) ClearAnnotationState
                                else DrawAnnotationState(
                                    highlightFrame.annotations,
                                    highlightFrame.frameCount
                                )
                            }
                        } else {
                            _state.value = if (highlightFrame == null) ClearAnnotationState
                            else DrawAnnotationState(
                                highlightFrame.annotations,
                                highlightFrame.frameCount
                            )
                        }
                    } else {
                        val comparisonFrame =
                            sandBoxCorrectAnnotationDataList.find { fc == it.frameCount }
                        comparisonFrame?.let { cf ->
                            if (cf.selected) return
                            else {
                                sandBoxCorrectAnnotationDataList.forEach {
                                    it.selected = (fc == it.frameCount)
                                }
                                _state.value = ToggleScreenCaptureState(true)
                            }
                        } ?: run {
                            sandBoxCorrectAnnotationDataList.forEach { it.selected = false }
                            _state.value = ToggleScreenCaptureState(false)
                        }
                    }
                }
            }

            CHILD_STEP_SANDBOX -> {
                frameCount?.let { fc ->
                    currentVideoFrameCount = fc
                    var extractFrame = false
                    var refreshVideoAnnotationView = false
                    if (inChildParentAssociationMode) {
                        val childAnnotation = sandboxParentAnnotations?.last()
                        childAnnotation?.let {
                            if (!it.selected && fc == it.frameCount) {
                                childAnnotation.selected = true
                                refreshVideoAnnotationView = true
                            } else if (childAnnotation.selected) {
                                if (fc == it.frameCount) return
                                else {
                                    childAnnotation.selected = false
                                    refreshVideoAnnotationView = false
                                }
                            }
                        }
                        _state.value = ToggleScreenCaptureState(refreshVideoAnnotationView)
                    } else {
                        videoParentAnnotationDataList.forEach { stepAnnotations ->
                            stepAnnotations.forEach { videoAnnotation ->
                                if (fc == videoAnnotation.frameCount
                                    && videoAnnotation.bitmap == null
                                ) {
                                    extractFrame = true
                                    refreshVideoAnnotationView = true
                                }
                            }
                        }

                        if (extractFrame)
                            _state.value = ToggleScreenCaptureState(refreshVideoAnnotationView)
                    }
                }
            }
        }
    }

    override fun moveToSandBoxState() {}
    override fun submitIncorrectSniffingRecords() {}
    override fun saveRecentSearchedTemplate(template: Template) {
        sandboxRepository.saveRecentSearchedTemplate(wfStepId, template)
    }

    override fun submitVideoAnnotation() {
        val videoAnnotations = mutableListOf<AnnotationObjectsAttribute>()
        when (videoMode) {
            PARENT_STEP_VIDEO_ANNOTATION -> {
                if (videoAnnotationDataList.isEmpty()) {
                    videoAnnotations.add(
                        getAnnotationAttribute(
                            annotationData = AnnotationData(type = DrawType.JUNK), frameCount = null
                        )
                    )
                } else {
                    videoAnnotationDataList.forEach { frameAnnotation ->
                        frameAnnotation.annotations.forEach { videoAnnotation ->
                            videoAnnotations.add(
                                getAnnotationAttribute(
                                    videoAnnotation,
                                    frameAnnotation.frameCount
                                )
                            )
                        }
                    }
                }
                submitAdminAnnotation(videoAnnotations)
            }

            CHILD_STEP_VIDEO_ANNOTATION -> {
                videoParentAnnotationDataList.forEach { stepAnnotationList ->
                    val frameAnnotation = stepAnnotationList.last()
                    if (frameAnnotation.isParent) {
                        videoAnnotations.add(
                            getAnnotationAttribute(
                                AnnotationData(
                                    type = DrawType.JUNK,
                                    parentAnnotation = frameAnnotation.rawAnnotation
                                ), null
                            )
                        )
                    } else {
                        frameAnnotation.annotations.forEach { childAnnotation ->
                            videoAnnotations.add(
                                getAnnotationAttribute(
                                    childAnnotation,
                                    frameAnnotation.frameCount
                                )
                            )
                        }
                    }
                }
                submitAdminAnnotation(videoAnnotations)
            }

            PARENT_STEP_SANDBOX -> {
                if (videoAnnotationDataList.isEmpty()) {
                    videoAnnotations.add(
                        getAnnotationAttribute(
                            annotationData = AnnotationData(type = DrawType.JUNK), frameCount = null
                        )
                    )
                } else {
                    videoAnnotationDataList.forEach { frameAnnotation ->
                        frameAnnotation.annotations.forEach { videoAnnotation ->
                            videoAnnotations.add(
                                getAnnotationAttribute(
                                    videoAnnotation,
                                    frameAnnotation.frameCount
                                )
                            )
                        }
                    }
                }
                submitSpecialistAnnotation(videoAnnotations)
            }

            CHILD_STEP_SANDBOX -> {
                videoParentAnnotationDataList.forEach { stepAnnotationList ->
                    val frameAnnotation = stepAnnotationList.lastOrNull()
                    if (frameAnnotation?.isParent == true) {
                        videoAnnotations.add(
                            getAnnotationAttribute(
                                AnnotationData(
                                    type = DrawType.JUNK,
                                    parentAnnotation = frameAnnotation.rawAnnotation
                                ), null
                            )
                        )
                    } else {
                        frameAnnotation?.annotations?.forEach { childAnnotation ->
                            videoAnnotations.add(
                                getAnnotationAttribute(
                                    childAnnotation,
                                    frameAnnotation.frameCount
                                )
                            )
                        }
                    }
                }
                submitSpecialistAnnotation(videoAnnotations)
            }
        }

        setMediaPlaybackStatus(false)
    }

    private fun matchVideoAnnotations(
        correctAnnotation: VideoAnnotationData,
        userAnnotation: VideoAnnotationData
    ): Boolean {
        val userAnnotations = if (isInterpolationEnabled())
            userAnnotation.annotations.filter { it.objectIndex == activeSandboxAnnotationId }
        else userAnnotation.annotations

        val correctAnnotations = if (isInterpolationEnabled())
            correctAnnotation.annotations.filter { it.objectIndex == activeSandboxAnnotationId }
        else correctAnnotation.annotations

        return when (correctAnnotations.firstOrNull()?.type) {
            DrawType.BOUNDING_BOX -> {
                correctAnnotation.bitmap?.let {
                    CropMatcher.matchAnnotations(
                        userAnnotations,
                        correctAnnotations,
                        it.width,
                        it.height,
                        annotationVariationThreshold
                    )
                } ?: true
            }

            DrawType.QUADRILATERAL, DrawType.POLYGON -> {
                correctAnnotation.bitmap?.let {
                    PathMatcher.matchAnnotations(
                        userAnnotations,
                        correctAnnotations,
                        it.width,
                        it.height,
                        annotationVariationThreshold
                    )
                } ?: true
            }

            DrawType.CONNECTED_LINE -> {
                LaneMatcher.matchAnnotations(
                    userAnnotations,
                    correctAnnotations,
                    annotationVariationThreshold
                )
            }

            DrawType.SPLIT_BOX -> {
                DragSplitMatcher.matchAnnotations(
                    userAnnotations,
                    correctAnnotations,
                    annotationVariationThreshold
                )
            }

            else -> false
        }
    }

    override fun hasAnnotatedAllSandboxChildAnnotation(): Boolean {
        var status = true
        videoParentAnnotationDataList.forEach { stepAnnotations ->
            val childAnnotation = stepAnnotations.find { !it.isParent }
            if (childAnnotation == null) {
                status = false
            }
        }
        return status
    }

    override fun resetVideoRecordSandbox() {
        sandBoxVideoAnnotationResultList.clear()
        when (videoMode) {
            PARENT_STEP_SANDBOX -> {
                videoAnnotationDataList.clear()
            }

            CHILD_STEP_SANDBOX -> {
                videoParentAnnotationDataList.forEach { stepAnnotations ->
                    val childAnnotation = stepAnnotations.find { !it.isParent }
                    stepAnnotations.remove(childAnnotation)
                }
            }
        }
    }

    override fun getSpanCount(): Int {
        return sandboxRepository.getSpanCount()
    }

    override fun saveSpanCount(count: Int) {
        sandboxRepository.saveSpanCount(count)
    }

    override fun setSandboxRecord(record: Record) {
        val newRecords = mutableListOf(record)
        if (!newRecords.isNullOrEmpty()) {
            addNewRecords(newRecords)
            currentRecord = newRecords.first()
            if (mediaType == MediaType.VIDEO) startVideoProcessing()
            else _record.value = currentRecord
        }
        _records.notifyObservers()
        _state.value = if (_records.value.isNullOrEmpty()) RecordsFinishedState
        else InitialState
        isSandboxCreationMode = true
    }

    override fun setSandboxParentAnnotation() {
        sandBoxCorrectParentAnnotationDataList.forEach { stepAnnotations ->
            parentAnnotations?.let {
                val sandboxFirstParent = stepAnnotations.first()
                sandboxFirstParent.bitmap = it.first().bitmap
                if (sandboxFirstParent.frameCount == it.first().frameCount
                    && matchVideoAnnotations(sandboxFirstParent, it.first())
                ) {
                    sandboxParentAnnotations = stepAnnotations
                    return@forEach
                } else {
                    sandboxFirstParent.bitmap = null
                }
            }
        }
    }

    override fun getSandboxParentAnnotations(): MutableList<VideoAnnotationData>? {
        return sandboxParentAnnotations
    }

    private val videoDownloadListener = object : VideoDownloadListener {
        override fun onDownloadingFailed(
            isSandBox: Boolean?,
            dataRecordsCorrupt: DataRecordsCorrupt
        ) {
            _state.value = DownloadingFailedState(isSandBox, dataRecordsCorrupt)
        }

        override fun onVideoDownloaded(record: Record, isFirst: Boolean?) {
            viewModelScope.launch {
                startFrameExtraction(record, isFirst)
                downloadNextVideo()
            }
        }

        override fun downloadingProgress(progress: Int) {
            _downloadingProgress.postValue(progress)
        }
    }

    override fun processNextRecord() {
        _records.value?.let {
            currentRecord = it.firstOrNull()
            _state.value = if (currentRecord == null) RecordsFinishedState
            else {
                _record.value = currentRecord
                InitialState
            }
        }
    }

    override fun processNextVideoRecord() {
        _records.value?.let {
            // If there are pending records, present the next record
            Timber.e("Position: $position, Records Size: ${it.size}")
            if (position < it.size && position > -1) {
                val record = it[position]
                if (record.isDownloaded && record.isFramesExtracted) {
                    _record.postValue(it[position])
                    position += 1
                    setMediaPlaybackStatus(false)
                }
                _foregroundVideoDownloading.postValue(record.isDownloaded.not() || record.isFramesExtracted.not())
            } else _state.postValue(RecordsFinishedState)
        }
    }

    private fun processRecord() {
        currentRecord?.let { record ->
            if (record.isDownloaded && record.isFramesExtracted) {
                _record.postValue(record)
            }
            _foregroundVideoDownloading.postValue(record.isDownloaded.not() || record.isFramesExtracted.not())
        }
    }

    private val onFFMPegExtractionListener = object : OnFFMPegExtractionListener {
        override fun onSuccess(record: Record) {
            isExtractingFrame = false
            if (isAdminRole()) {
                record.isFramesExtracted = true
                if (_foregroundVideoDownloading.value == true) processNextVideoRecord()
                _records.value?.firstOrNull {
                    it.isDownloaded && it.isFramesExtracted.not()
                }?.let { startFrameExtraction(it) }
            } else {
                currentRecord?.isFramesExtracted = true
                processRecord()
            }
        }

        override fun onCancelled(dataRecordsCorrupt: DataRecordsCorrupt) {
            _state.postValue(FrameExtractionFailedState(dataRecordsCorrupt))
        }

        override fun onFailed(dataRecordsCorrupt: DataRecordsCorrupt) {
            _state.postValue(FrameExtractionFailedState(dataRecordsCorrupt))
        }
    }

    override fun initVideoDownloadManager(
        lifecycleOwner: LifecycleOwner
    ) {
        ffmPegExtraction.setOnFFMPegExtractionListener(onFFMPegExtractionListener)
        videoDownloadManager = videoDownloadProvider.provide(videoDownloadListener)
    }

    private fun startFrameExtraction(record: Record, isFirst: Boolean? = false) {
        if (isExtractingFrame) return
        isExtractingFrame = true
        viewModelScope.launch { ffmPegExtraction.extractFrames(record, isFirst) }
    }

    private fun downloadNextVideo(record: Record? = null) {
        record?.let {
            if (it.isDownloaded.not())
                videoDownloadManager?.downloadRecord(viewModelScope, record, isSandbox())
        } ?: run {
            _records.value?.let { records ->
                records.find { it.isDownloaded.not() }?.let { record ->
                    videoDownloadManager?.downloadRecord(viewModelScope, record, isSandbox())
                }
            }
        }
    }

    override fun submitVideoJudgement(judgement: Boolean) {
        setMediaPlaybackStatus(false)
    }

    private fun Boolean.status() = if (this) "correct" else "incorrect"

    object SandboxSuccessState : ActivityState()
    object SandboxFailedState : ActivityState()
    object CorrectAnnotationState : ActivityState()
    object RecordSubmissionFailedState : ActivityState()
    object VideoModeNextSandboxState : ActivityState()

    data class LearningImageSetupState(val learningImageData: LearningImageData) : ActivityState()
    data class ToggleScreenCaptureState(
        val status: Boolean
    ) : ActivityState()

    data class VideoModeInterpolationResultState(
        val sandboxResult: SandboxVideoAnnotationData,
        val annotationObjectsAttributes: List<AnnotationObjectsAttribute>
    ) : ActivityState()

    data class VideoModeSandboxResultState(
        val sandboxResult: MutableList<SandboxVideoAnnotationData>,
        val annotationObjectsAttributes: List<AnnotationObjectsAttribute>
    ) : ActivityState()
}
