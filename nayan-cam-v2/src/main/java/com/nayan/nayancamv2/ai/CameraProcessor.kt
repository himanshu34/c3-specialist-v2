package com.nayan.nayancamv2.ai

import android.graphics.Bitmap
import co.nayan.c3v2.core.models.driver_module.AIWorkFlowModel
import com.nayan.nayancamv2.storage.SharedPrefManager
import timber.log.Timber

/**
 * This class is responsible to run the AI model on image coming from camera
 *
 * @property aiModelManager
 * @property sharedPrefManager
 */
class CameraProcessor(
    private val aiModelManager: AIModelManager,
    private val sharedPrefManager: SharedPrefManager
) : AIWorkflowManager() {

    override fun setScreenRotation(rotation: Int) {
        mScreenRotation = rotation
    }

    override fun getAIModelManager(): AIModelManager = aiModelManager

    override suspend fun startProcessing(
        image: Bitmap,
        isAIMode: Boolean,
        cameraAIWorkFlows: List<AIWorkFlowModel>
    ) {
        Timber.e("---------------->>>>>>>>>>>")
        Timber.e("startProcessing:")
        Timber.e("---------------->>>>>>>>>>>")
        processImage(image = image, isAIMode = isAIMode, cameraAIWorkFlows)
    }

    override fun getSharedPrefManager(): SharedPrefManager = sharedPrefManager

    override fun onAIScanning() {
        mObjectOfInterestListener.onAIScanning()
    }

    override fun onRunningOutOfRAM(availMem: Float) {
        mObjectOfInterestListener.onRunningOutOfRAM(availMem)
    }

    override fun isWorkflowAvailable(status: Boolean) {
        mObjectOfInterestListener.isWorkflowAvailable(status)
    }

    override fun onObjectDetected(
        bitmap: Bitmap,
        className: String,
        workFlowIndex: Int,
        aiModelIndex: Int
    ) {
        mObjectOfInterestListener.onObjectDetected(bitmap, className, workFlowIndex, aiModelIndex)
    }

    override suspend fun updateCameraISOExposure() {
        mObjectOfInterestListener.updateCameraISOExposure()
    }

    override fun onStartRecording(
        modelName: String,
        labelName: String,
        confidence: String,
        workflowMeta: String
    ) {
        mObjectOfInterestListener.onStartRecording(
            modelName,
            labelName,
            confidence,
            workflowMeta
        )
    }
}