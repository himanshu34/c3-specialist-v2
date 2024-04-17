package co.nayan.canvas.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import co.nayan.c3v2.core.models.DataRecordsCorrupt
import co.nayan.c3v2.core.models.DataRecordsCorruptRecord
import co.nayan.c3v2.core.models.Record
import co.nayan.canvas.config.ErrorCode.PROCESSING_CORRUPTED
import co.nayan.canvas.config.ErrorCode.PROCESSING_FAILED
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class FFMPegExtraction @Inject constructor(
    private val fileManager: FileManager
) {

    private var ffmPegExtractionListener: OnFFMPegExtractionListener? = null

    fun setOnFFMPegExtractionListener(toSet: OnFFMPegExtractionListener) {
        ffmPegExtractionListener = toSet
    }

    private fun getFrameRate(file: File): Int {
        val extractor = MediaExtractor()
        var frameRate = 30
        try {
            extractor.setDataSource(file.path)
            val numTracks = extractor.trackCount
            loop@ for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                        break@loop
                    }
                }
            }

            Timber.tag("FFMPegExtraction").e("Frame Rate using MediaExtractor --> $frameRate")
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
        } finally {
            extractor.release()
        }

        return frameRate
    }

    suspend fun extractFrames(
        record: Record,
        isFirst: Boolean? = false
    ) = withContext(Dispatchers.IO) {
        try {
            val file = fileManager.getFile(record.id)
            val frameRate = getFrameRate(file)
            val cmd = "-i ${file.path} -vf fps=${frameRate} ${
                File(fileManager.getDir(record.id), "out%d.jpg").path
            }"

            val executionId = FFmpegKit.executeAsync(cmd) { session ->
                session?.let {
                    when {
                        ReturnCode.isSuccess(it.returnCode) -> {
                            ffmPegExtractionListener?.onSuccess(record)
                            Timber.tag(TAG).d(
                                "Async command execution completed successfully. Time taken ${it.duration} ms"
                            )
                        }

                        ReturnCode.isCancel(it.returnCode) -> {
                            val errorBody = DataRecordsCorrupt(
                                DataRecordsCorruptRecord(
                                    record.id,
                                    record.workAssignmentId,
                                    isFirst,
                                    record.isSniffingRecord,
                                    PROCESSING_FAILED,
                                    "Processing cancelled by user"
                                )
                            )
                            ffmPegExtractionListener?.onCancelled(errorBody)
                            Timber.tag(TAG).e("Async command execution cancelled by user.")
                        }

                        else -> {
                            val errorBody = DataRecordsCorrupt(
                                DataRecordsCorruptRecord(
                                    record.id,
                                    record.workAssignmentId,
                                    isFirst,
                                    record.isSniffingRecord,
                                    PROCESSING_CORRUPTED,
                                    "Failure in extracting frames"
                                )
                            )
                            ffmPegExtractionListener?.onFailed(errorBody)
                            Timber.tag(TAG)
                                .e("Async command execution failed with returnCode=${it.returnCode}")
                        }
                    }
                }
            }
            Timber.d("FFmpegKit status -> $executionId")
        } catch (e: Exception) {
            e.printStackTrace()
            val errorBody = DataRecordsCorrupt(
                DataRecordsCorruptRecord(
                    record.id,
                    record.workAssignmentId,
                    isFirst,
                    record.isSniffingRecord,
                    PROCESSING_FAILED,
                    e.message.toString()
                )
            )
            ffmPegExtractionListener?.onFailed(errorBody)
            return@withContext
        }
    }

    companion object {
        private val TAG = FFMPegExtraction::class.java.name
    }
}

interface OnFFMPegExtractionListener {
    fun onSuccess(record: Record)
    fun onCancelled(dataRecordsCorrupt: DataRecordsCorrupt)
    fun onFailed(dataRecordsCorrupt: DataRecordsCorrupt)
}