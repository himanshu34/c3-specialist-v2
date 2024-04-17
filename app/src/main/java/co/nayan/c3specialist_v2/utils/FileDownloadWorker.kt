package co.nayan.c3specialist_v2.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.nayan.c3v2.core.downloadFile
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class FileDownloadWorker constructor(
    private val context: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    private val progressJob = SupervisorJob()
    private val progressScope = CoroutineScope(Dispatchers.Default + progressJob)

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val fileName = workerParameters.inputData.getString(FILE_NAME)
            val fileExtension = workerParameters.inputData.getString(FILE_EXTENSION)
            val folderName = workerParameters.inputData.getString(FOLDER_NAME)

            val outputStream = if (folderName.isNullOrEmpty()) {
                context.openFileOutput("$fileName$fileExtension", MODE_PRIVATE)
            } else {
                val file =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val directory = File(file, folderName)
                if (!directory.exists()) directory.mkdir()
                val destination = File(directory, "$fileName$fileExtension")
                FileOutputStream(destination)
            }

            val fileUrl = workerParameters.inputData.getString(FILE_URL)
            downloadFile(fileUrl ?: "", outputStream) { _, contentLength, progress ->
                progressScope.launch {
                    val contentLengthData = workDataOf(WORK_TYPE to WORK_START, WORK_LENGTH to contentLength)
                    val progressData = workDataOf(WORK_TYPE to WORK_IN_PROGRESS, WORK_PROGRESS_VALUE to progress)
                    setProgress(contentLengthData)
                    setProgress(progressData)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
            Result.failure()
        }
    }

    companion object {
        const val FILE_URL = "fileURL"
        const val FILE_NAME = "fileName"
        const val FILE_EXTENSION = "fileExtension"
        const val FOLDER_NAME = "folderName"
        const val WORK_TYPE = "WORK_TYPE"
        const val WORK_IN_PROGRESS = "WORK_IN_PROGRESS"
        const val WORK_PROGRESS_VALUE = "WORK_PROGRESS_VALUE"
        const val WORK_LENGTH = "WORK_LENGTH"
        const val WORK_START = "WORK_START"
    }
}