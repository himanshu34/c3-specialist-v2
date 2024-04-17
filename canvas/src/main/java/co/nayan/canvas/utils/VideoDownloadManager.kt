package co.nayan.canvas.utils

import android.content.Context
import co.nayan.c3v2.core.downloadFile
import co.nayan.c3v2.core.models.DataRecordsCorrupt
import co.nayan.c3v2.core.models.DataRecordsCorruptRecord
import co.nayan.c3v2.core.models.Record
import co.nayan.canvas.config.ErrorCode.DOWNLOAD_CORRUPTED
import co.nayan.canvas.config.ErrorCode.DOWNLOAD_FAILED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class VideoDownloadManager constructor(
    private val context: Context,
    private val downloadListener: VideoDownloadListener
) {
    fun deleteVideos() {
        val dir = File(context.filesDir, "NayanVideoMode")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun downloadRecord(
        viewModelScope: CoroutineScope,
        record: Record,
        isSandBox: Boolean? = false,
        isFirst: Boolean? = false
    ) = viewModelScope.launch {
        record.mediaUrl?.let { url ->
            val result = withContext(Dispatchers.IO) {
                val file = createFile("${record.id}.mp4")
                return@withContext file?.let {
                    downloadFile(url, FileOutputStream(file)) { bytesRead, contentLength, _ ->
                        val progress = (bytesRead * 100 / contentLength).toInt()
                        downloadListener.downloadingProgress(progress)
                    }
                } ?: run { Pair(false, "Error while creating file, before downloading") }
            }

            if (result.first) {
                if (result.second.contains("File downloaded successfully")) {
                    record.isDownloaded = true
                    downloadListener.onVideoDownloaded(record, isFirst)
                } else {
                    val errorBody = DataRecordsCorrupt(
                        DataRecordsCorruptRecord(
                            record.id,
                            record.workAssignmentId,
                            isFirst,
                            record.isSniffingRecord,
                            DOWNLOAD_FAILED,
                            result.second
                        )
                    )
                    record.isDownloaded = false
                    downloadListener.onDownloadingFailed(isSandBox, errorBody)
                }
            } else {
                val errorBody = DataRecordsCorrupt(
                    DataRecordsCorruptRecord(
                        record.id,
                        record.workAssignmentId,
                        isFirst,
                        record.isSniffingRecord,
                        DOWNLOAD_CORRUPTED,
                        result.second
                    )
                )
                record.isDownloaded = false
                downloadListener.onDownloadingFailed(isSandBox, errorBody)
            }
        }
    }

    private fun createFile(fileName: String): File? {
        return try {
            // Create file if not exists
            val dir = File(context.filesDir, "NayanVideoMode")
            if (dir.exists().not()) dir.mkdir()
            val destination = File(dir, fileName)
            destination.createNewFile()
            destination
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}

interface VideoDownloadListener {
    fun onDownloadingFailed(isSandBox: Boolean?, dataRecordsCorrupt: DataRecordsCorrupt)
    fun onVideoDownloaded(record: Record, isFirst: Boolean?)
    fun downloadingProgress(progress: Int)
}