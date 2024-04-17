package co.nayan.canvas.viewmodels

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationValue
import co.nayan.c3v2.core.models.DataRecordsCorrupt
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.RecordAnnotation
import co.nayan.c3v2.core.models.RecordJudgment
import co.nayan.c3v2.core.models.RecordReview
import co.nayan.c3v2.core.models.Template
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.answer
import co.nayan.c3views.utils.videoAnnotations
import co.nayan.canvas.interfaces.CanvasRepositoryInterface
import co.nayan.canvas.utils.FFMPegExtraction
import co.nayan.canvas.utils.ImageCachingManager
import co.nayan.canvas.utils.OnFFMPegExtractionListener
import co.nayan.canvas.utils.VideoDownloadListener
import co.nayan.canvas.utils.notifyObservers
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.CHILD_STEP_VIDEO_VALIDATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_VIDEO_ANNOTATION
import co.nayan.canvas.videoannotation.VideoAnnotationFragment.Companion.PARENT_STEP_VIDEO_VALIDATION
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val canvasRepositoryInterface: CanvasRepositoryInterface,
    private val imageCachingManager: ImageCachingManager,
    private val videoDownloadProvider: VideoDownloadProvider,
    private val ffmPegExtraction: FFMPegExtraction
) : BaseCanvasViewModel() {

    init {
        Timber.d("Initializing CanvasViewModel")
    }

    private val prevBNCAnnotations = mutableListOf<RecordAnnotation>()
    private val prevBNCRecords = mutableListOf<Record>()


    /**
     * fetch next records for [workAssignmentId]
     * then setup new records and user state [_state]
     * setup first time records fetching [areRecordsFetched] status to 'true' to prevent the API call on changing screen orientation
     */
    override fun fetchRecords() {
        if (isFetchingRecord) return
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                isFetchingRecord = true
                val records =
                    canvasRepositoryInterface.fetchRecords(workAssignmentId, currentRole())
                val nonSniffingRecords =
                    records?.filter { it.isSniffingRecord == false }?.toMutableList()
                var maxNonSniffingId = nonSniffingRecords?.maxByOrNull { it.id }?.id
                // Generate random sniffing id for sniffing records
                val sniffingRecords = records?.filter { it.isSniffingRecord == true }
                if (maxNonSniffingId != null && sniffingRecords.isNullOrEmpty().not()) {
                    sniffingRecords?.map {
                        it.randomSniffingId = maxNonSniffingId + 50
                        maxNonSniffingId++
                    }
                }

                // Shuffle sniffing records
                val shuffledRecords =
                    generateShuffleRecords(nonSniffingRecords, sniffingRecords?.toMutableList())
                if (shuffledRecords.isNullOrEmpty()) _state.value = RecordsFinishedState
                else {
                    addNewRecords(shuffledRecords)
                    if (mediaType == MediaType.VIDEO) {
                        _state.value = InitialState
                        startVideoProcessing()
                    } else {
                        imageCachingManager.cacheImages(shuffledRecords)
                        if (applicationMode == Mode.BINARY_CLASSIFY)
                            setupRecordsForBNCMode(shuffledRecords)
                        else setupRecords()
                    }
                }
                areRecordsFetched = true
                isFetchingRecord = false
            } catch (e: Exception) {
                isFetchingRecord = false
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    private fun generateShuffleRecords(
        nonSniffingRecords: MutableList<Record>?,
        sniffingRecords: MutableList<Record>?
    ): MutableList<Record> {
        val shuffledRecords = nonSniffingRecords ?: mutableListOf()
        if (sniffingRecords.isNullOrEmpty().not()) {
            try {
                val iterator = sniffingRecords?.iterator()
                while (iterator?.hasNext() == true) {
                    val item = iterator.next()
                    val position = if (shuffledRecords.isNotEmpty() && shuffledRecords.size > 1)
                            (1 until shuffledRecords.size).random() else 0
                    try {
                        val subList = if (position > 0) shuffledRecords.subList(position - 1, shuffledRecords.size - 1) else shuffledRecords
                        if (subList.isNotEmpty() && position < subList.size && position > 0) {
                            // Map below fields from previous record
                            subList[position - 1].let { prevRecord ->
                                item.driverId = prevRecord.driverId
                                item.videoRecordedOn = prevRecord.videoRecordedOn
                                item.videoSourceId = prevRecord.videoSourceId
                                item.videoId = prevRecord.videoId
                                item.metatag = prevRecord.metatag
                                item.annotationPriority = prevRecord.annotationPriority
                                item.judgmentPriority = prevRecord.judgmentPriority
                                item.reviewPriority = prevRecord.reviewPriority
                                item.cityKmlPriority = prevRecord.cityKmlPriority
                                item.applicationMode = prevRecord.applicationMode
                            }
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex)
                    }

                    if (position > 0) shuffledRecords.add(position, item)
                    else shuffledRecords.add(item)
                    iterator.remove()
                }
            } catch (exception: Exception) {
                Timber.e(exception)
            }
        }
        return shuffledRecords
    }

    private fun submitAnswers() {
        viewModelScope.launch {
            try {
                isSubmittingRecords = true
                val submitAnswers = canvasRepositoryInterface.submitSavedAnswers()
                isSubmittingRecords = false
                when {
                    isManagerRole() -> {
                        if (submitAnswers.isAccountLocked || submitAnswers.sniffingPassed.not()) {
                            if (workType == WorkType.REVIEW) {
                                val incorrectSniffing = arrayListOf<Record>()
                                _records.value?.forEach {
                                    if (submitAnswers.incorrectSniffingIds?.contains(it.id) == true) {
                                        incorrectSniffing.add(it)
                                    }
                                }
                                _state.value = AccountLockedState(
                                    incorrectSniffing,
                                    isAdmin = true,
                                    isAccountLocked = true
                                )
                            } else {
                                _state.value = AccountLockedState(
                                    arrayListOf(),
                                    isAdmin = true,
                                    isAccountLocked = false
                                )
                            }
                        } else {
                            removedSubmittedRecords(
                                submitAnswers.annotationIds ?: emptyList(),
                                submitAnswers.recordIds ?: emptyList()
                            )
                        }
                    }
                    else -> {
                        if (submitAnswers.sniffingPassed.not()) {
                            _state.value = AccountLockedState(
                                arrayListOf(), isAdmin = false,
                                isAccountLocked = false
                            )
                        } else {
                            removedSubmittedRecords(
                                submitAnswers.annotationIds ?: emptyList(),
                                submitAnswers.recordIds ?: emptyList()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                isSubmittingRecords = false
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    override fun moveToSandBoxState() {
        _state.value = AccountLockedState(
            arrayListOf(), isAdmin = false,
            isAccountLocked = false
        )
    }

    /**
     * Notify [_records] to populate records in bulk and notify [_record] to populate single record
     * and set user state [_state] to initial from progress
     */
    private fun setupRecords() {
        _records.notifyObservers()
        _record.value = _records.value?.first()
        _state.value = InitialState
    }

    /**
     * If annotations or judgments are submitted then we will remove those records from [_records]
     */
    private suspend fun removedSubmittedRecords(annotationIds: List<Int>, recordIds: List<Int>) {
        withContext(Dispatchers.Main) {
            val currentRecordCount = _records.value?.size ?: 0
            _records.value?.removeAll { recordIds.contains(it.id) || annotationIds.contains(it.currentAnnotation?.id) }
            val removedRecords = currentRecordCount - (_records.value?.size ?: 0)
            position -= removedRecords
            setupUndoRecordState()
        }
    }

    override fun processNextRecord() {
        _records.value?.let {
            // If there are pending records, present the next record
            Timber.e("Position: $position, Records Size: ${it.size}")
            if (position < it.size - 1 && position > -1) {
                position += 1
                _record.value = it[position]
            } else _state.value = RecordsFinishedState

            when {
                (position == SUBMIT_TRIGGER) -> submitAnswers()
                else -> {
                    if (it.isNotEmpty() && _record.value == it.last())
                        submitAnswers()
                }
            }
        }
    }

    override fun submitIncorrectSniffingRecords() {
        submitAnswers()
    }

    private fun undoRecord() {
        _records.value?.let {
            if (position > 0 && position <= it.size) {
                position -= 1
                _record.value = it[position]
            }
        }
    }

    override fun undoAnnotation(): List<AnnotationObjectsAttribute> {
        val annotationObjectAttributes =
            canvasRepositoryInterface.undoAnnotation()?.annotationObjectsAttributes
        undoRecord()
        return annotationObjectAttributes ?: emptyList()
    }

    override fun undoJudgment() {
        canvasRepositoryInterface.undoJudgement()
        undoRecord()
    }

    override fun reloadRecord() {
        _records.value?.let {
            if (position >= 0 && position < it.size) {
                _record.value = it[position]
            }
        }
    }

    /**
     * Add new records if they are not already present in the local list
     */
    private suspend fun addNewRecords(toAdd: List<Record>) {
        withContext(Dispatchers.Main) {
            _records.value?.clear()
            _records.value?.let { records ->
                for (newRecord in toAdd) {
                    if (!records.contains(newRecord)) {
                        records.add(newRecord)
                    }
                }
            }
        }
    }

    /**
     * Saved [review] as [RecordJudgment] into the local storage
     */
    override fun submitReview(review: Boolean) {
        _record.value?.let {
            viewModelScope.launch {
                val isSniffing = it.isSniffingRecord ?: false
                val isCorrect = (it.needsRejection?.xor(review) ?: true)
                val recordReview = RecordReview(
                    recordId = it.id,
                    review = review,
                    isSniffing = isSniffing,
                    isSniffingCorrect = isSniffing && isCorrect
                )
                canvasRepositoryInterface.submitReview(recordReview)
            }
        }
    }

    /**
     * Saved [judgment] as [RecordJudgment] into the local storage
     */
    override fun submitJudgement(judgment: Boolean) {
        _record.value?.let {
            viewModelScope.launch {
                val isSniffing = it.isSniffingRecord ?: false
                val isCorrect = (it.needsRejection?.xor(judgment) ?: true)
                if (isSniffing && isCorrect.not()) _state.value = SniffingIncorrectWarningState
                val recordJudgment = RecordJudgment(
                    recordAnnotationId = it.currentAnnotation?.id,
                    judgment = judgment,
                    isSniffing = isSniffing,
                    isSniffingCorrect = isSniffing && isCorrect,
                    dataRecordId = it.id
                )
                canvasRepositoryInterface.submitJudgment(recordJudgment)
            }
        }
    }

    /**
     * Saved annotations [annotationObjectsAttributes] as [RecordAnnotation] in local storage
     */
    override fun submitAnnotation(annotationObjectsAttributes: List<AnnotationObjectsAttribute>) {
        _record.value?.let {
            viewModelScope.launch {
                val isSniffing = mediaType == MediaType.IMAGE && it.isSniffingRecord ?: false
                val isCorrect = validImageAnnotation(annotationObjectsAttributes, it, isSniffing)
                if (isSniffing && isCorrect.not()) _state.value = SniffingIncorrectWarningState
                val recordAnnotation = RecordAnnotation(
                    dataRecordId = it.id,
                    annotationObjectsAttributes = annotationObjectsAttributes,
                    isSniffing = isSniffing,
                    isSniffingCorrect = isSniffing && isCorrect
                )
                canvasRepositoryInterface.submitAnnotation(recordAnnotation)
            }
        }
    }

    override fun submitVideoAnnotation() {
        when (videoMode) {
            PARENT_STEP_VIDEO_ANNOTATION -> {
                submitAnnotation(getVideoAnnotationsForParentStep())
            }
            CHILD_STEP_VIDEO_ANNOTATION -> {
                val videoAnnotations = mutableListOf<AnnotationObjectsAttribute>()
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
                submitAnnotation(videoAnnotations)
            }
        }
        setMediaPlaybackStatus(false)
        setupForNextRecord()
    }

    override fun getVideoAnnotationsForParentStep(): MutableList<AnnotationObjectsAttribute> {
        val videoAnnotations = mutableListOf<AnnotationObjectsAttribute>()
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
                            AnnotationData(
                                objectIndex = videoAnnotation.objectIndex,
                                objectName = videoAnnotation.objectName,
                                points = videoAnnotation.points,
                                type = videoAnnotation.type,
                                input = videoAnnotation.input,
                                tags = videoAnnotation.tags,
                                frameCount = videoAnnotation.frameCount
                            ),
                            frameAnnotation.frameCount
                        )
                    )
                }
            }
        }
        return videoAnnotations
    }

    override fun submitVideoJudgement(judgement: Boolean) {
        when (workType) {
            WorkType.REVIEW -> {
                submitReview(judgement)
            }
            WorkType.VALIDATION -> {
                submitJudgement(judgement)
            }
        }
        setMediaPlaybackStatus(false)
        setupForNextRecord()
    }

    /**
     * Return 'true' if all locally stored annotations and judgments are submitted
     */
    override fun isAllAnswersSubmitted(): Boolean {
        return canvasRepositoryInterface.isAllAnswersSubmitted()
    }

    /**
     * Forced submit locally stored annotations and judgments
     */
    override fun submitSavedAnswers() {
        viewModelScope.launch {
            try {
                canvasRepositoryInterface.submitSavedAnswers()
                _state.value = AnswersSubmittedState
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    /**
     * Return templates for classification modes
     */
    override fun fetchTemplates() {
        viewModelScope.launch {
            try {
                _templateState.value = ProgressState
                val templates = canvasRepositoryInterface.fetchTemplates(wfStepId)
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
                val response = canvasRepositoryInterface.addLabel(
                    wfStepId,
                    _record.value?.displayImage,
                    labelText
                )
                response.let {
                    if (response.second != null) {
                        val newTemplates = response.second ?: emptyList()
                        _templateState.value = TemplatesSuccessState(labelText, newTemplates)
                    } else _templateState.value = TemplatesFailedState(response.first)
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

    override fun saveRecentSearchedTemplate(template: Template) {
        canvasRepositoryInterface.saveRecentSearchedTemplate(wfStepId, template)
    }

    override fun getRecentSearchedTemplate(): MutableList<Template> {
        return canvasRepositoryInterface.getRecentSearchedTemplate(wfStepId)
    }

    override fun saveContrastValue(progress: Int) {
        canvasRepositoryInterface.saveContrast(progress)
    }

    override fun getContrast(): Int {
        return canvasRepositoryInterface.getContrast()
    }

    override fun currentRole(): String? {
        return canvasRepositoryInterface.currentRole()
    }

    override fun clearAnswers() {
        canvasRepositoryInterface.clearAnswers()
    }

    override fun setupUndoRecordState() {
        _canUndo.value = position != 0
    }

    /**
     * Used to check whether current work is assigned or not
     */
    override fun assignWork() {
        viewModelScope.launch {
            try {
                val workAssignment = canvasRepositoryInterface.assignWork()?.workAssignment
                if (workAssignmentId != workAssignment?.id) {
                    canvasRepositoryInterface.clearAnswers()
                    _state.value = RecordsFinishedState
                }
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    override fun getSpanCount(): Int {
        return canvasRepositoryInterface.getSpanCount()
    }

    override fun isSandbox() = false
    override fun shouldPlayHelpVideo(applicationMode: String?): Boolean {
        return false
    }

    override fun setupRecordsForBNCMode(records: List<Record>?) {
        _records.notifyObservers()
        if (_state.value == ProgressState) _state.value = InitialState
    }

    override fun submitAnnotationsForBNC(selectedRecords: List<Record>?, selectedTemplate: String) {
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                if (prevBNCAnnotations.isNotEmpty()) {
                    canvasRepositoryInterface.submitBNCAnnotations(prevBNCAnnotations)
                    prevBNCRecords.clear()
                    prevBNCAnnotations.clear()
                }

                val annotations = getBNCAnnotations(selectedRecords, selectedTemplate)
                prevBNCAnnotations.addAll(annotations)
                prevBNCRecords.addAll(selectedRecords ?: emptyList())
                _records.value?.removeAll(prevBNCRecords)
                canvasRepositoryInterface.submitBNCAnnotations(annotations)
                if (_records.value.isNullOrEmpty()) {
                    _state.value = RecordsFinishedState
                } else {
                    _records.notifyObservers()
                    _state.value = InitialState
                }
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    private fun getBNCAnnotations(records: List<Record>?, answer: String): List<RecordAnnotation> {
        val annotations = mutableListOf<RecordAnnotation>()
        records?.forEach {
            val isSniffing = it.isSniffingRecord ?: false
            val isCorrect = it.annotation.answer() == answer
            val annotationObjectsAttributes =
                listOf(AnnotationObjectsAttribute(AnnotationValue(answer)))
            val recordAnnotation = RecordAnnotation(
                dataRecordId = it.id,
                annotationObjectsAttributes = annotationObjectsAttributes,
                isSniffing = isSniffing,
                isSniffingCorrect = isCorrect
            )
            annotations.add(recordAnnotation)
        }
        return annotations
    }

    override suspend fun getLearningVideo(applicationMode: String): Video? {
        return canvasRepositoryInterface.getLearningVideo(applicationMode)
    }

    override fun setLearningVideoMode() {
        _learningVideoMode.value = when (workType) {
            WorkType.VALIDATION -> Mode.VALIDATE
            WorkType.ANNOTATION -> applicationMode
            else -> null
        }
    }

    override fun setupVideoMode(record: Record) {
        annotateForChildStepInVideoMode = record.parentAnnotation != null
        videoMode = if (workType == WorkType.ANNOTATION && !annotateForChildStepInVideoMode) {
            PARENT_STEP_VIDEO_ANNOTATION
        } else if (workType == WorkType.ANNOTATION && annotateForChildStepInVideoMode) {
            CHILD_STEP_VIDEO_ANNOTATION
        } else if (workType == WorkType.VALIDATION && !annotateForChildStepInVideoMode) {
            PARENT_STEP_VIDEO_VALIDATION
        } else if (workType == WorkType.VALIDATION && annotateForChildStepInVideoMode) {
            CHILD_STEP_VIDEO_VALIDATION
        } else if (workType == WorkType.REVIEW && !annotateForChildStepInVideoMode) {
            PARENT_STEP_VIDEO_VALIDATION
        } else if (workType == WorkType.REVIEW && annotateForChildStepInVideoMode) {
            CHILD_STEP_VIDEO_VALIDATION
        } else -1
    }

    override fun populateVideoAnnotation(record: Record) {
        setupVideoMode(record)
        when (videoMode) {
            PARENT_STEP_VIDEO_VALIDATION,
            PARENT_STEP_VIDEO_ANNOTATION -> {
                videoAnnotationDataList.clear()
                val videoAnnotation = record.currentAnnotation
                videoAnnotation?.let {
                    val videoAnnotationMap = HashMap<Int, VideoAnnotationData?>()
                    videoAnnotation.annotations().forEach { annotation ->
                        annotation.frameCount?.let { frameCount ->
                            val videoAnnotationData =
                                if (videoAnnotationMap.containsKey(frameCount)) videoAnnotationMap[frameCount]
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

            CHILD_STEP_VIDEO_VALIDATION -> {
                videoParentAnnotationDataList.clear()
                val childStep = record.parentAnnotation.videoAnnotations()[0].size + 1
                setupVideoParentAnnotationList(record.currentAnnotation, childStep)
            }

            CHILD_STEP_VIDEO_ANNOTATION -> {
                parentAnnotations = null
                videoParentAnnotationDataList.clear()
                val childStep = record.parentAnnotation.videoAnnotations()[0].size + 1
                if (record.currentAnnotation == null)
                    setupVideoParentAnnotationList(record.parentAnnotation, childStep)
                else setupVideoParentAnnotationList(record.currentAnnotation, childStep)
            }
        }
    }

    override fun monitorFrames(frameCount: Int?) {
        when (videoMode) {
            PARENT_STEP_VIDEO_VALIDATION,
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

            CHILD_STEP_VIDEO_ANNOTATION,
            CHILD_STEP_VIDEO_VALIDATION -> {
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

    override fun startVideoProcessing() {
        _records.value?.let {
            if (it.isNotEmpty()) {
                val firstRecord = it.first()
                _foregroundVideoDownloading.postValue(
                    firstRecord.isDownloaded.not() || firstRecord.isFramesExtracted.not()
                )
                videoDownloadManager?.deleteVideos()
                videoDownloadManager?.downloadRecord(viewModelScope, firstRecord, isSandbox(), true)
            }
        }
    }

    override fun sendCorruptCallback(dataRecordsCorrupt: DataRecordsCorrupt) {
        if (isFetchingRecord) return
        viewModelScope.launch {
            try {
                _state.value = ProgressState
                isFetchingRecord = true
                val records = canvasRepositoryInterface.sendCorruptCallback(dataRecordsCorrupt)
                val nonSniffingRecords =
                    records?.filter { it.isSniffingRecord == false }?.toMutableList()
                var maxNonSniffingId = nonSniffingRecords?.maxByOrNull { it.id }?.id
                // Generate random sniffing id for sniffing records
                val sniffingRecords = records?.filter { it.isSniffingRecord == true }
                if (maxNonSniffingId != null && sniffingRecords.isNullOrEmpty().not()) {
                    sniffingRecords?.map {
                        it.randomSniffingId = maxNonSniffingId + 50
                        maxNonSniffingId++
                    }
                }

                // Shuffle sniffing records
                val shuffledRecords =
                    generateShuffleRecords(nonSniffingRecords, sniffingRecords?.toMutableList())
                if (shuffledRecords.isNullOrEmpty()) {
                    if (dataRecordsCorrupt.dataRecordsCorruptRecord.firstRecord == false) {
                        deleteCorruptedRecord(dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId)
                        _state.value = RecordDeleteState
                    } else _state.value = RecordsFinishedState
                } else {
                    addNewRecords(shuffledRecords)
                    if (mediaType == MediaType.VIDEO) {
                        _state.value = InitialState
                        startVideoProcessing()
                    } else {
                        imageCachingManager.cacheImages(shuffledRecords)
                        if (applicationMode == Mode.BINARY_CLASSIFY) setupRecordsForBNCMode(
                            shuffledRecords
                        )
                        else setupRecords()
                    }
                }
                areRecordsFetched = true
                isFetchingRecord = false
                isFirstRecordCorrupted = false
            } catch (e: Exception) {
                isFetchingRecord = false
                isFirstRecordCorrupted = false
                Firebase.crashlytics.recordException(e)
                _state.value = ErrorState(e)
            }
        }
    }

    override fun deleteCorruptedRecord(recordId: Int?) {
        _records.value?.let { records ->
            val corruptRecord = records.find { it.id == recordId }
            records.remove(corruptRecord)
        }
    }

    private val videoDownloadListener = object : VideoDownloadListener {
        override fun onDownloadingFailed(
            isSandBox: Boolean?,
            dataRecordsCorrupt: DataRecordsCorrupt
        ) {
            if (dataRecordsCorrupt.dataRecordsCorruptRecord.firstRecord == true)
                isFirstRecordCorrupted = true
            _state.value = DownloadingFailedState(isSandBox, dataRecordsCorrupt)
        }

        override fun onVideoDownloaded(record: Record, isFirst: Boolean?) {
            viewModelScope.launch {
                // If first record is corrupted stop further download and processing
                if (isFirstRecordCorrupted || isFetchingRecord) return@launch
                startFrameExtraction(record, isFirst)
                downloadNextVideo()
            }
        }

        override fun downloadingProgress(progress: Int) {
            _downloadingProgress.postValue(progress)
        }
    }

    override fun processNextVideoRecord() {
        _records.value?.let {
            // If there are pending records, present the next record
            Timber.e("Position: $position, Records Size: ${it.size}")
            if (position < it.size && position > -1) {
                val record = it[position]
                Timber.e("currentRecord: ${record.id}")
                when {
                    record.isDownloaded.not() -> downloadNextVideo(record)
                    record.isFramesExtracted.not() -> startFrameExtraction(record)
                    else -> {
                        _record.postValue(it[position])
                        position += 1
                        setActiveAnnotationState(false)
                        setMediaPlaybackStatus(false)
                    }
                }

                _foregroundVideoDownloading.postValue(
                    record.isDownloaded.not()
                            || record.isFramesExtracted.not()
                )
            } else _state.postValue(RecordsFinishedState)

            when {
                isInterpolationEnabled() -> submitAnswers()
                position == SUBMIT_TRIGGER -> submitAnswers()
                else -> {
                    if (it.isNotEmpty() && _record.value == it.last())
                        submitAnswers()
                }
            }
        }
    }

    private val onFFMPegExtractionListener = object : OnFFMPegExtractionListener {
        override fun onSuccess(record: Record) {
            isExtractingFrame = false
            _records.value?.find { it.id == record.id }?.isFramesExtracted = true
            if (_foregroundVideoDownloading.value == true) processNextVideoRecord()
            _records.value?.firstOrNull {
                it.isDownloaded && it.isFramesExtracted.not()
            }?.let { startFrameExtraction(it) }
        }

        override fun onCancelled(dataRecordsCorrupt: DataRecordsCorrupt) {
            isExtractingFrame = false
            if (dataRecordsCorrupt.dataRecordsCorruptRecord.firstRecord == true)
                isFirstRecordCorrupted = true
            _state.postValue(FrameExtractionFailedState(dataRecordsCorrupt))
        }

        override fun onFailed(dataRecordsCorrupt: DataRecordsCorrupt) {
            isExtractingFrame = false
            if (dataRecordsCorrupt.dataRecordsCorruptRecord.firstRecord == true)
                isFirstRecordCorrupted = true
            _state.postValue(FrameExtractionFailedState(dataRecordsCorrupt))
        }
    }

    override fun initVideoDownloadManager(lifecycleOwner: LifecycleOwner) {
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
                videoDownloadManager?.downloadRecord(viewModelScope, record, isSandbox(), false)
        } ?: run {
            _records.value?.let { records ->
                records.find { it.isDownloaded.not() }?.let { record ->
                    videoDownloadManager?.downloadRecord(viewModelScope, record, isSandbox(), false)
                }
            }
        }
    }

    companion object {
        private const val SUBMIT_TRIGGER = 5
    }

    object AnswersSubmittedState : ActivityState()
}