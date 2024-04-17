package co.nayan.c3specialist_v2.storage

import android.content.Context
import co.nayan.c3v2.core.models.CameraAIModel
import com.nayan.nayancamv2.util.getMD5
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedStorage: SharedStorage
) {
    private fun isCameraAIModelAlreadyPresent(fileName: String) =
        context.getFileStreamPath(fileName).exists()

    suspend fun deleteCameraAIModels() = withContext(Dispatchers.IO) {
        sharedStorage.getCameraAIModels().forEach {
            val fileName = "${it.name?.replace(" ", "_")}.tflite"
            val file = context.getFileStreamPath(fileName)
            if (file.exists()) file.delete()
        }
    }

    fun shouldDownload(cameraAIModel: CameraAIModel?): Boolean {
        return if (cameraAIModel == null) false
        else {
            val fileName = "${cameraAIModel.name?.replace(" ", "_")}.tflite"
            val alreadyPresent = isCameraAIModelAlreadyPresent(fileName) &&
                    cameraAIModel.checksum == context.getFileStreamPath(fileName).getMD5()
            alreadyPresent.not()
        }
    }

    fun saveDownloadDetailsFor(cameraAIModel: CameraAIModel) {
        sharedStorage.syncCameraAIModel(cameraAIModel)
    }
}