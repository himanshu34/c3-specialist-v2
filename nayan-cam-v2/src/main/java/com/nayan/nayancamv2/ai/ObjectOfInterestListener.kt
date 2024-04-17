package com.nayan.nayancamv2.ai

import android.graphics.Bitmap

interface ObjectOfInterestListener {
    fun onObjectDetected(
        bitmap: Bitmap,
        className: String,
        workFlowIndex: Int,
        aiModelIndex: Int
    ) {
    }

    fun onStartRecording(
        modelName: String,
        labelName: String,
        confidence: String,
        recordedWorkFlowMetaData: String
    )

    fun onRunningOutOfRAM(availMem: Float)
    fun isWorkflowAvailable(status: Boolean)
    fun onAIScanning()
    fun updateCameraISOExposure()
}