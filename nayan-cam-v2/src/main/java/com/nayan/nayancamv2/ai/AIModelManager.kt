package com.nayan.nayancamv2.ai

import android.content.Context
import co.nayan.c3v2.core.downloadFile
import co.nayan.c3v2.core.models.driver_module.AIWorkFlowModel
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.c3v2.core.models.driver_module.LastSyncDetails
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.util.getMD5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class AIModelManager @Inject constructor(
    private val context: Context,
    private val sharedPrefManager: SharedPrefManager
) {
    suspend fun getUnavailableCameraAIModel(
        cameraAIWorkFlows: MutableList<AIWorkFlowModel>?
    ): List<CameraAIModel> = withContext(Dispatchers.Default) {
        return@withContext cameraAIWorkFlows?.flatMap { it.cameraAIModels }
            ?.filter { cameraAIModel ->
                val fileName = "${cameraAIModel.name?.replace(" ", "_")}.tflite"
                val alreadyPresent = (isCameraAIModelAlreadyPresent(fileName)
                        && cameraAIModel.checksum == context.getFileStreamPath(fileName).getMD5())
                !alreadyPresent && cameraAIModel.link != null
            }.orEmpty()
    }

    fun isCameraAIModelAlreadyPresent(aiModel: CameraAIModel): Boolean {
        val fileName = "${aiModel.name?.replace(" ", "_")}.tflite"
        val file = context.getFileStreamPath(fileName)
        return file.exists()
    }

    private fun isCameraAIModelAlreadyPresent(fileName: String) =
        context.getFileStreamPath(fileName).exists()

    fun getCameraAIModelFile(aiModel: CameraAIModel): File? {
        val fileName = "${aiModel.name?.replace(" ", "_")}.tflite"
        val modelFile = context.getFileStreamPath(fileName)
        val alreadyPresent = (isCameraAIModelAlreadyPresent(fileName)
                && aiModel.checksum == modelFile.getMD5())
        return if (alreadyPresent) modelFile else null
    }

    fun getLastWorkflowsSyncDetails() = sharedPrefManager.getLastWorkflowsSyncDetails()

    fun getCameraAIWorkFlows() = sharedPrefManager.getCameraAIWorkFlows()

    fun saveCameraAIWorkFlows(
        lastSyncDetails: LastSyncDetails,
        workflowList: MutableList<AIWorkFlowModel>?
    ) {
        sharedPrefManager.saveCameraAIWorkFlows(lastSyncDetails, workflowList)
    }

    suspend fun startDownloading(
        items: List<CameraAIModel>
    ): ActivityState = withContext(Dispatchers.IO) {
        val state = try {
            val results = makeDownloadRequest(items)
            results?.let {
                // You can process the results as needed
                val anyFailed = it.any { (_, success) -> !success }
                // Return true if any task failed, false if all tasks were successful
                if (anyFailed) FailState(Exception("Error while downloading model files"), items)
                else {
                    if (areAllDownloadsConfirmed(items, context)) DownloadFinishedState
                    else FailState(Exception("Model checksum value mismatched error"), items)
                }
            } ?: run { FailState(Exception("Error while downloading model files"), items) }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            FailState(e, items)
        }

        return@withContext state
    }

    private suspend fun makeDownloadRequest(
        downloadableItems: List<CameraAIModel>,
        onProgress: ((task: CameraAIModel, progress: Int) -> Unit)? = null
    ): List<Pair<CameraAIModel, Boolean>>? = supervisorScope {
        return@supervisorScope try {
            val deferredList = downloadableItems
                .filter { it.link.isNullOrEmpty().not() }
                .map { item ->
                    // Add tflite AI models to download
                    async(Dispatchers.IO) {
                        val fileName = "${item.name?.replace(" ", "_")}.tflite"
                        val file = File(context.filesDir, fileName)
                        val fileStream = FileOutputStream(file)
                        val result = downloadFile(item.link!!, fileStream) { _, _, progress ->
                            onProgress?.invoke(item, progress)
                        }
                        Pair(item, result.first)
                    }
                }

            deferredList.awaitAll()
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            null
        }
    }

    private fun areAllDownloadsConfirmed(
        downloadableItems: List<CameraAIModel>,
        context: Context
    ): Boolean {
        return downloadableItems.all { isDownloadConfirmed(it, context) }
    }

    private fun isDownloadConfirmed(
        item: CameraAIModel,
        context: Context
    ): Boolean {
        val fileName = "${item.name?.replace(" ", "_")}.tflite"
        val file = context.getFileStreamPath(fileName)
        return if (file.exists()) {
            val phoneChecksum = file.getMD5()
            if (item.checksum == phoneChecksum) true
            else {
                Timber.e("Checksum needs to be replaced for ${item.name} from ${item.checksum} to $phoneChecksum")
                file.delete()
                false
            }
        } else false
    }

    object DownloadFinishedState : ActivityState()

    data class FailState(
        val exception: Exception,
        val downloadableItems: List<CameraAIModel>
    ) : ActivityState()
}