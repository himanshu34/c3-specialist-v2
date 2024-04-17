package com.nayan.nayancamv2.ai

import android.content.Context
import android.graphics.Bitmap
import co.nayan.c3v2.core.getDeviceAvailableRAM
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.c3v2.core.models.driver_module.AIWorkFlowModel
import co.nayan.imageprocessing.config.ImageProcessingType
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.nayan.nayancamv2.between
import com.nayan.nayancamv2.getCurrentEnabledWorkflows
import com.nayan.nayancamv2.helper.GlobalParams
import com.nayan.nayancamv2.helper.GlobalParams.isProcessingFrame
import com.nayan.nayancamv2.model.AIMetaData
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_1
import com.nayan.nayancamv2.util.rotate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

abstract class AIWorkflowManager : IAIWorkFlowManager {

    private var lastRAMErrorShownAt = 0L
    private var shouldUpdateWorkflow: Boolean = false
    lateinit var mObjectOfInterestListener: ObjectOfInterestListener
    private var workFlowListTemp: MutableList<AIWorkFlowModel>? = null
    private var workFlows = listOf<AIWorkFlowModel>()
    private var lastRAMValidationAt: Long = 0L
    private var availMem = 0F
    private lateinit var modelConfigManager: ModelConfigManager
    private lateinit var aiDetectionResultProcessor: AIDetectionResultProcessor
    private val processImageJob = SupervisorJob()
    protected var workFlowMetadataList = HashMap<Int, HashMap<Int, AIMetaData>>()
    private val TAG = this.javaClass.simpleName
    protected var mScreenRotation = 0
    private var lastWorkflowErrorShownAt = 0L
    private val inferenceExceptionHandler = CoroutineExceptionHandler { _, _ ->
        onStateChanged(InitState)
    }
    private val processImageScope =
        CoroutineScope(Dispatchers.IO + processImageJob + inferenceExceptionHandler)

    private var context: Context? = null
    fun initClassifiers(mContext: Context) {
        this.context = mContext
        onStateChanged(InitState)
    }

    suspend fun processImage(
        image: Bitmap,
        isAIMode: Boolean,
        cameraAIWorkFlows: List<AIWorkFlowModel>
    ) = processImageScope.launch {
        val bitmap = if (mScreenRotation.between(0, 180)) image
        else image.rotate(0 - mScreenRotation.toFloat())
        if (isAIMode) {
            if (workFlows.isEmpty()) workFlows = cameraAIWorkFlows
            if (shouldUpdateWorkflow) {
                shouldUpdateWorkflow = false
                workFlowListTemp?.let { workFlows = it }
            }
            checkMemoryAvailability()
            processAIWorkflows(bitmap, cameraAIWorkFlows)
        } else {
            onStateChanged(
                StartRecordingState(
                    "CR",
                    "",
                    "",
                    -1,
                    HashMap()
                )
            )
        }
    }


    private fun checkMemoryAvailability() {
        val diff = System.currentTimeMillis() - lastRAMValidationAt
        if (availMem == 0F || lastRAMValidationAt == 0L || diff > DELAYED_1) {
            availMem = context?.getDeviceAvailableRAM() ?: 0.0f
            lastRAMValidationAt = System.currentTimeMillis()
        }
    }

    private fun isMemorySufficient(aiModel: CameraAIModel): Boolean {
        return if (aiModel.ram < availMem) {
            onAIScanning()
            true
        } else {
            val diff =
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastRAMErrorShownAt)
            if (lastRAMErrorShownAt == 0L || diff > 30) {
                lastRAMErrorShownAt = System.currentTimeMillis()
                onRunningOutOfRAM(availMem)
            }
            false
        }
    }

    private suspend fun processAIWorkflows(
        image: Bitmap,
        cameraAIWorkFlows: List<AIWorkFlowModel>?
    ) {
        // Check if there are any AI workflows available
        if (cameraAIWorkFlows.isNullOrEmpty()) {
            handleNoWorkflows()
            return
        }

        // Start processing AI workflows recursively
        processAIWorkflowTasks(image, 0)
    }

    private fun handleNoWorkflows() {
        val currentTimeInMillis = System.currentTimeMillis()
        val diffLastWorkflowError =
            TimeUnit.MILLISECONDS.toSeconds(currentTimeInMillis - lastWorkflowErrorShownAt)
        if (lastWorkflowErrorShownAt == 0L || diffLastWorkflowError > 30) {
            lastWorkflowErrorShownAt = currentTimeInMillis
            isWorkflowAvailable(false)
        }
        isProcessingFrame = false
    }

    private suspend fun processAIWorkflowTasks(bitmap: Bitmap, currentIndex: Int) {
        if (currentIndex >= workFlows.size) {
            isProcessingFrame = false
            return
        }
        workFlows[currentIndex].cameraAIModels.firstOrNull()?.let { aiModel ->
            Timber.tag(TAG)
                .e(
                    "%s%s%s",
                    "${aiModel.name} -->>ramRequired: ",
                    aiModel.ram,
                    " ramAvailable: $availMem"
                )
            if (isMemorySufficient(aiModel)) {
                onAIScanning()
                processFrame(
                    aiModel,
                    bitmap,
                    currentIndex,
                    0,
                    HashMap()
                )
            } else processAIWorkflowTasks(bitmap, currentIndex + 1)
        } ?: run { processAIWorkflowTasks(bitmap, currentIndex + 1) }
    }

    private suspend fun processFrame(
        aiModel: CameraAIModel,
        bitmap: Bitmap,
        workFlowIndex: Int,
        aiModelIndex: Int = 0,
        aiResults: HashMap<Int, AIMetaData>
    ) {
        checkInitInstances()
        Timber.tag("$TAG$workFlowIndex/$aiModelIndex").e("processFrame")
        getAIModelManager().getCameraAIModelFile(aiModel)?.let { modelFile ->
            val modelMappedByteBuffer = modelConfigManager.getModelFile(modelFile)

            val config = modelConfigManager.getInputConfig(
                bitmap,
                aiModel,
                modelFile,
                modelMappedByteBuffer
            )
            // Get Results from tflite model
            Timber.tag("$TAG$workFlowIndex/$aiModelIndex")
                .e("%s%s", "exec modelFile: w*h = ${bitmap.width}*", bitmap.height)

            val processStartTime = System.currentTimeMillis()
            val results = modelConfigManager.runAIModel(aiModel, config)
            val timeRequiredToExecute =
                (System.currentTimeMillis() - processStartTime).toString() + " MS"

            Timber.tag("$TAG$workFlowIndex/$aiModelIndex")
                .e("results size: ${results.size} Time: $timeRequiredToExecute")
            Timber.tag("$TAG$workFlowIndex/$aiModelIndex")
                .e("max results: ${results.maxByOrNull { r -> r.confidence }}")
            if (results.isEmpty()) {
                Timber.tag("$TAG$workFlowIndex/$aiModelIndex").e("AIModel not responding")
                processAIWorkflowTasks(bitmap, workFlowIndex + 1)
            } else {
                val cameraAIModelRules = modelConfigManager.getAIModelRules(
                    workFlows,
                    workFlowIndex,
                    aiModel.id
                )
                when (aiModel.category) {
                    ImageProcessingType.OCR -> {
                        aiDetectionResultProcessor.processOCRResult(
                            bitmap,
                            results.maxByOrNull { r -> r.confidence }!!,
                            workFlowIndex,
                            aiModelIndex,
                            modelFile,
                            cameraAIModelRules,
                            timeRequiredToExecute,
                            aiResults
                        )
                    }

                    ImageProcessingType.LP -> {
                        aiDetectionResultProcessor.processLPResult(
                            bitmap,
                            results.maxByOrNull { r -> r.confidence }!!,
                            workFlowIndex,
                            aiModelIndex,
                            modelFile,
                            cameraAIModelRules,
                            timeRequiredToExecute,
                            aiResults
                        )
                    }

                    else -> {
                        aiDetectionResultProcessor.processObjectDetectorResult(
                            bitmap,
                            results,
                            workFlowIndex,
                            aiModelIndex,
                            modelFile,
                            cameraAIModelRules,
                            config,
                            timeRequiredToExecute,
                            aiResults
                        )
                    }
                }
            }
        } ?: run {
            Timber.tag("$TAG$workFlowIndex/$aiModelIndex").e("AIModel file not available")
            processAIWorkflowTasks(bitmap, workFlowIndex + 1)
        }
    }

    private fun checkInitInstances() {
        if (::modelConfigManager.isInitialized.not())
            modelConfigManager = ModelConfigManager()
        if (::aiDetectionResultProcessor.isInitialized.not()) {
            aiDetectionResultProcessor = AIDetectionResultProcessor()
            aiDetectionResultProcessor.aiWorkFlowManager = this
        }
    }

    private suspend fun showBitmap(
        bitmap: Bitmap,
        className: String,
        workFlowIndex: Int,
        aiModelIndex: Int
    ) = withContext(Dispatchers.IO) {
        if (getSharedPrefManager().shouldShowAIPreview())
            onObjectDetected(bitmap, className, workFlowIndex, aiModelIndex)
    }

    abstract fun setScreenRotation(rotation: Int)
    abstract fun getAIModelManager(): AIModelManager
    abstract suspend fun startProcessing(
        image: Bitmap,
        isAIMode: Boolean,
        cameraAIWorkFlows: List<AIWorkFlowModel>
    )

    abstract fun getSharedPrefManager(): SharedPrefManager
    abstract fun onAIScanning()
    abstract fun onRunningOutOfRAM(availMem: Float)
    abstract fun isWorkflowAvailable(status: Boolean)
    abstract fun onObjectDetected(
        bitmap: Bitmap,
        className: String,
        workFlowIndex: Int,
        aiModelIndex: Int
    )

    abstract fun onStartRecording(
        modelName: String,
        labelName: String,
        confidence: String,
        workflowMeta: String
    )

    abstract suspend fun updateCameraISOExposure()

    // Other helper methods and properties...
    override fun onStateChanged(state: InferenceState) {
        when (state) {
            is StartRecordingState -> {
                Timber.tag(TAG).d("onStartRecordingState")
                processImageScope.launch {
                    if (state.workFlowIndex >= 0)
                        workFlowMetadataList[state.workFlowIndex] = state.aiResults
                    onStartRecording(
                        state.modelName,
                        state.labelName,
                        state.confidence,
                        workFlowMetadataList.toString()
                    )
                    isProcessingFrame = false
                    clearMetaData()
                }
            }

            is ShowBitmapState -> {
                Timber.tag(TAG).d("showBitmapState")
                processImageScope.launch {
                    showBitmap(
                        state.recognizedImage,
                        state.ruleLabel,
                        state.workFlowIndex,
                        state.aiModelIndex
                    )
                }
            }

            is ProcessFrameNextAIState -> {
                Timber.tag(TAG).d("processFrameNextAIState")
                processImageScope.launch {
                    processFrame(
                        state.aiModel,
                        state.bitmap,
                        state.workFlowIndex,
                        state.aiModelIndex,
                        state.aiResults
                    )
                }
            }

            is ProcessNextWorkFlowState -> {
                Timber.tag(TAG).d("ProcessNextWorkFlowState")
                Timber.d(TAG, "ProcessNextWorkFlowState")
                processImageScope.launch {
                    processAIWorkflowTasks(state.bitmap, state.newIndex)
                }
            }

            InitState -> {
                Timber.tag(TAG).d("InitState")
                isProcessingFrame = false
                clearMetaData()
            }

            UpdateISOExposureState -> {
                Timber.tag(TAG).d("UpdateExposure")
                processImageScope.launch { updateCameraISOExposure() }
            }
        }
    }

    private fun clearMetaData() {
        Timber.tag(TAG).e("workFlowMetadataList.clear()---->>>>>>>>>")
        Firebase.crashlytics.log("workFlowMetadataList.clear()---->>>>>>>>>")
        workFlowMetadataList.clear()
    }

    suspend fun updateWorkFlowList(
        workFlowList: MutableList<AIWorkFlowModel>
    ) = withContext(Dispatchers.IO) {
        val location =
            GlobalParams.userLocation?.let { LatLng(it.latitude, it.longitude) } ?: run { null }
        val currentEnabledWorkflows = getCurrentEnabledWorkflows(workFlowList, location)
        workFlowListTemp =
            currentEnabledWorkflows.filter { it.workflow_IsDroneEnabled.not() }.toMutableList()
        shouldUpdateWorkflow = true
        Timber.tag(TAG).e("on updateWorkFlowList---->>>>>>>>> %s", workFlowList.size)
    }
}
