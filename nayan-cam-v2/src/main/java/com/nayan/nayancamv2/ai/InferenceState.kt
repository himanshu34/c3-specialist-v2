package com.nayan.nayancamv2.ai

import android.graphics.Bitmap
import co.nayan.c3v2.core.models.CameraAIModel
import com.nayan.nayancamv2.model.AIMetaData

open class InferenceState
data class StartRecordingState(
    val modelName: String,
    val labelName: String,
    val confidence: String,
    val workFlowIndex: Int,
    var aiResults: HashMap<Int, AIMetaData>
) : InferenceState()

data class ShowBitmapState(
    val recognizedImage: Bitmap,
    val ruleLabel: String,
    val workFlowIndex: Int,
    val aiModelIndex: Int
) : InferenceState()

data class ProcessFrameNextAIState(
    val aiModel: CameraAIModel,
    val bitmap: Bitmap,
    val workFlowIndex: Int,
    val aiModelIndex: Int = 0,
    val aiResults: HashMap<Int, AIMetaData>
) : InferenceState()

data class ProcessNextWorkFlowState(
    val bitmap: Bitmap,
    val newIndex: Int
) : InferenceState()

object UpdateISOExposureState : InferenceState()
object InitState : InferenceState()