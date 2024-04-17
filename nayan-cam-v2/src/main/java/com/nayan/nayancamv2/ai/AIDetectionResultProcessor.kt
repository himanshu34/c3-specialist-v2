package com.nayan.nayancamv2.ai

import android.graphics.Bitmap
import co.nayan.c3v2.core.models.CameraAIModelRule
import co.nayan.imageprocessing.classifiers.Recognition
import co.nayan.imageprocessing.model.InputConfigDetector
import com.nayan.nayancamv2.helper.GlobalParams
import com.nayan.nayancamv2.model.AIMetaData
import com.nayan.nayancamv2.model.ValidObjects
import timber.log.Timber
import java.io.File
import java.util.Locale

class AIDetectionResultProcessor {
    lateinit var aiWorkFlowManager: IAIWorkFlowManager
    private val TAG = this.javaClass.simpleName

    fun processOCRResult(
        bitmap: Bitmap,
        maxResult: Recognition,
        workFlowIndex: Int,
        aiModelIndex: Int,
        modelFile: File,
        cameraAIModelRules: MutableList<CameraAIModelRule>,
        timeRequiredToExecute: String,
        aiResults: HashMap<Int, AIMetaData>
    ) {
        if (maxResult.title.length > 3) {
            val cropBitmap = maxResult.title.textAsBitmap()
            cropBitmap.let { recognizedImage ->
                Timber.tag("$TAG$workFlowIndex/$aiModelIndex").e("cropBitmap not null")
                if (::aiWorkFlowManager.isInitialized)
                    aiWorkFlowManager.onStateChanged(
                        ShowBitmapState(
                            recognizedImage,
                            maxResult.title,
                            workFlowIndex,
                            aiModelIndex
                        )
                    )
            }

            val aiMeta = AIMetaData(
                modelFile.name,
                maxResult.title,
                maxResult.confidence.toString(),
                System.currentTimeMillis(),
                timeRequiredToExecute,
                GlobalParams.currentTemperature
            )
            if (cameraAIModelRules.isEmpty()) {
                Timber.tag("$TAG$workFlowIndex/$aiModelIndex")
                    .e("cameraAIModel Rules not available")
                aiResults[aiModelIndex] = aiMeta
                if (::aiWorkFlowManager.isInitialized) {
                    aiWorkFlowManager.onStateChanged(
                        StartRecordingState(
                            modelFile.name,
                            maxResult.title,
                            maxResult.confidence.toString(),
                            workFlowIndex,
                            aiResults
                        )
                    )
                    aiWorkFlowManager.onStateChanged(UpdateISOExposureState)
                } else {
                    cameraAIModelRules[0].let { rule ->
                        rule.nextModel?.let { nextAiModel ->
                            aiResults[aiModelIndex] = aiMeta
                            ProcessFrameNextAIState(
                                nextAiModel,
                                cropBitmap,
                                workFlowIndex,
                                aiModelIndex + 1,
                                aiResults
                            )
                        } ?: run {
                            aiResults[aiModelIndex] = aiMeta
                            if (::aiWorkFlowManager.isInitialized) {
                                aiWorkFlowManager.onStateChanged(
                                    StartRecordingState(
                                        modelFile.name,
                                        maxResult.title,
                                        maxResult.confidence.toString(),
                                        workFlowIndex,
                                        aiResults
                                    )
                                )
                                aiWorkFlowManager.onStateChanged(UpdateISOExposureState)
                            }
                        }
                    }
                }
            }
        } else {
            if (::aiWorkFlowManager.isInitialized)
                aiWorkFlowManager.onStateChanged(
                    ProcessNextWorkFlowState(
                        bitmap,
                        workFlowIndex + 1
                    )
                )
        }
    }

    fun processLPResult(
        bitmap: Bitmap,
        maxResult: Recognition,
        workFlowIndex: Int,
        aiModelIndex: Int,
        modelFile: File,
        cameraAIModelRules: MutableList<CameraAIModelRule>,
        timeRequiredToExecute: String,
        aiResults: HashMap<Int, AIMetaData>
    ) {
        val rect = maxResult.location
        if ((rect.top).between(1, bitmap.height) &&
            (rect.height()).between(1, bitmap.height) &&
            (rect.width()).between(1, bitmap.width) &&
            (rect.left).between(1, bitmap.width) &&
            (rect.top + rect.height()).between(1, bitmap.height) &&
            (rect.left + rect.width()).between(1, bitmap.width)
        ) {
            val cropBitmap = Bitmap.createBitmap(
                bitmap,
                rect.left.toInt(),
                rect.top.toInt(),
                rect.width().toInt(),
                rect.height().toInt()
            )
            val aiMeta = AIMetaData(
                modelFile.name,
                maxResult.title,
                maxResult.confidence.toString(),
                System.currentTimeMillis(),
                timeRequiredToExecute,
                GlobalParams.currentTemperature
            )
            cropBitmap?.let { recognizedImage ->
                Timber.tag("$TAG$workFlowIndex/$aiModelIndex").e("cropBitmap not null")
                if (::aiWorkFlowManager.isInitialized)
                    aiWorkFlowManager.onStateChanged(
                        ShowBitmapState(
                            recognizedImage,
                            maxResult.title,
                            workFlowIndex,
                            aiModelIndex
                        )
                    )
                if (cameraAIModelRules.isEmpty()) {
                    Timber.tag("$TAG$workFlowIndex/$aiModelIndex")
                        .e("cameraAIModel Rules not available")

                    aiResults[aiModelIndex] = aiMeta
                    if (::aiWorkFlowManager.isInitialized)
                        aiWorkFlowManager.onStateChanged(
                            StartRecordingState(
                                modelFile.name,
                                maxResult.title,
                                maxResult.confidence.toString(),
                                workFlowIndex,
                                aiResults
                            )
                        )
                } else {
                    cameraAIModelRules[0].let { rule ->
                        rule.nextModel?.let { nextAiModel ->
                            aiResults[aiModelIndex] = aiMeta
                            if (::aiWorkFlowManager.isInitialized)
                                aiWorkFlowManager.onStateChanged(
                                    ProcessFrameNextAIState(
                                        nextAiModel,
                                        cropBitmap,
                                        workFlowIndex,
                                        aiModelIndex + 1,
                                        aiResults
                                    )
                                )
                        } ?: run {
                            aiResults[aiModelIndex] = aiMeta
                            if (::aiWorkFlowManager.isInitialized)
                                aiWorkFlowManager.onStateChanged(
                                    StartRecordingState(
                                        modelFile.name,
                                        maxResult.title,
                                        maxResult.confidence.toString(),
                                        workFlowIndex,
                                        aiResults
                                    )
                                )
                        }
                    }
                }
            } ?: run {
                Timber.tag("$TAG$workFlowIndex/$aiModelIndex").e("cropBitmap is null")
                if (::aiWorkFlowManager.isInitialized)
                    aiWorkFlowManager.onStateChanged(
                        ProcessNextWorkFlowState(
                            bitmap,
                            workFlowIndex + 1
                        )
                    )
            }
        } else {
            if (::aiWorkFlowManager.isInitialized)
                aiWorkFlowManager.onStateChanged(
                    ProcessNextWorkFlowState(
                        bitmap,
                        workFlowIndex + 1
                    )
                )
        }

    }

    fun processObjectDetectorResult(
        bitmap: Bitmap,
        results: List<Recognition>,
        workFlowIndex: Int,
        aiModelIndex: Int,
        modelFile: File,
        cameraAIModelRules: MutableList<CameraAIModelRule>,
        config: InputConfigDetector,
        timeRequiredToExecute: String,
        aiResults: HashMap<Int, AIMetaData>
    ) {
        if (cameraAIModelRules.isEmpty()) {
            Timber.tag("$TAG$workFlowIndex/$aiModelIndex").e("cameraAIModel Rules not available")
            val maxResult = results.maxByOrNull { r -> r.confidence }!!
            val aiMeta = AIMetaData(
                modelFile.name,
                maxResult.title,
                maxResult.confidence.toString(),
                System.currentTimeMillis(),
                timeRequiredToExecute,
                GlobalParams.currentTemperature
            )
            aiResults[aiModelIndex] = aiMeta
            //show full frame as bitmap --> new mod
            if (::aiWorkFlowManager.isInitialized) {
                aiWorkFlowManager.onStateChanged(
                    StartRecordingState(
                        modelFile.name,
                        maxResult.title,
                        maxResult.confidence.toString(),
                        workFlowIndex,
                        aiResults
                    )
                )
            }
        } else {
            val validResults = ArrayList<ValidObjects>()
            cameraAIModelRules.map { rule ->
                rule.label?.let { ruleLabel ->
                    val requiredResult = results
                        .filter { it.confidence > rule.confidence }
                        .filter {
                            it.title.lowercase(Locale.getDefault()).trim() ==
                                    ruleLabel.lowercase(Locale.getDefault()).trim()
                        }
                        .filter {
                            val rect = it.location
                            val width: Float =
                                bitmap.width * rect.width() / config.inputWidth
                            val height: Float =
                                bitmap.height * rect.height() / config.inputHeight

                            (height < (bitmap.height * 0.9) &&
                                    width < (bitmap.width * 0.9) &&
                                    height >= (rule.size?.height ?: 0)
                                    && width >= (rule.size?.width ?: 0))
                        }


                    if (requiredResult.isNotEmpty()) {
                        Timber.tag("$TAG$workFlowIndex/$aiModelIndex")
                            .e("${requiredResult.size} results passed camera rules")
                        requiredResult.maxByOrNull { r -> r.confidence }?.let {
                            val rect = it.location

                            val left: Float = bitmap.width * rect.left / config.inputWidth
                            val top: Float = bitmap.height * rect.top / config.inputHeight
                            val width: Float =
                                bitmap.width * rect.width() / config.inputWidth
                            val height: Float =
                                bitmap.height * rect.height() / config.inputHeight

                            val cropBitmap = if ((top).between(1, bitmap.height) &&
                                (height).between(1, bitmap.height) &&
                                (width).between(1, bitmap.width) &&
                                (left).between(1, bitmap.width) &&
                                top + height <= bitmap.height &&
                                left + width <= bitmap.width
                            ) {
                                Bitmap.createBitmap(
                                    bitmap,
                                    left.toInt(),
                                    top.toInt(),
                                    width.toInt(),
                                    height.toInt()
                                )
                            } else null

                            cropBitmap?.let { recognizedImage ->
                                Timber.tag("$TAG$workFlowIndex/$aiModelIndex")
                                    .e("cropBitmap not null")
                                if (::aiWorkFlowManager.isInitialized)
                                    aiWorkFlowManager.onStateChanged(
                                        ShowBitmapState(
                                            recognizedImage,
                                            ruleLabel,
                                            workFlowIndex,
                                            aiModelIndex
                                        )
                                    )

                                rule.nextModel?.let { nextAiModel ->
                                    validResults.add(
                                        ValidObjects(
                                            it,
                                            nextAiModel,
                                            recognizedImage
                                        )
                                    )
                                } ?: run {
                                    validResults.add(
                                        ValidObjects(
                                            it,
                                            null,
                                            recognizedImage
                                        )
                                    )
                                }
                            } ?: run {
                                Timber.tag("$TAG$workFlowIndex/$aiModelIndex")
                                    .e("cropBitmap is null")
                            }
                        }
                    }
                }
            }

            if (validResults.isEmpty()) {
                Timber.tag("$TAG$workFlowIndex/$aiModelIndex").e("validResults isEmpty")
                if (::aiWorkFlowManager.isInitialized)
                    aiWorkFlowManager.onStateChanged(
                        ProcessNextWorkFlowState(
                            bitmap,
                            workFlowIndex + 1
                        )
                    )
            } else {
                validResults.maxByOrNull { it.objectDetected.confidence }!!.let {
                    val aiMeta = AIMetaData(
                        modelFile.name,
                        it.objectDetected.title,
                        it.objectDetected.confidence.toString(),
                        System.currentTimeMillis(),
                        timeRequiredToExecute,
                        GlobalParams.currentTemperature
                    )

                    if (it.nextAiModel == null) {
                        aiResults[aiModelIndex] = aiMeta
                        if (::aiWorkFlowManager.isInitialized)
                            aiWorkFlowManager.onStateChanged(
                                StartRecordingState(
                                    modelFile.name,
                                    it.objectDetected.title,
                                    it.objectDetected.confidence.toString(),
                                    workFlowIndex,
                                    aiResults
                                )
                            )
                        // Added the average check to update night mode current exposure iso level
                        if (it.objectDetected.title.equals("plate", ignoreCase = true))
                            aiWorkFlowManager.onStateChanged(UpdateISOExposureState)

                    } else {
                        aiResults[aiModelIndex] = aiMeta
                        if (::aiWorkFlowManager.isInitialized)
                            aiWorkFlowManager.onStateChanged(
                                ProcessFrameNextAIState(
                                    it.nextAiModel,
                                    it.cropBitmap,
                                    workFlowIndex,
                                    aiModelIndex + 1,
                                    aiResults
                                )
                            )
                    }
                }
            }
        }
    }
}
