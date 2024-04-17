package com.nayan.nayancamv2.encoder


import android.os.Handler
import android.os.Looper
import android.os.Message
import com.nayan.nayancamv2.model.RecordingData
import timber.log.Timber
import java.io.File

class CircularEncoderHandler(
    private val encoderCallback: Callback
) : Handler(Looper.getMainLooper()), CircularEncoder.Callback {

    override fun fileSaveComplete(
        status: Int,
        recordingData: RecordingData
    ) {
        Timber.d("🦀 CircularEncoderHandler fileSaveComplete() status: $status ")
        sendMessage(obtainMessage(MSG_FILE_SAVE_COMPLETE, status, 0, recordingData))
    }

    override fun bufferStatus(totalTimeMsec: Long) {
        sendMessage(
            obtainMessage(
                MSG_BUFFER_STATUS,
                (totalTimeMsec shr 32).toInt(),
                totalTimeMsec.toInt()
            )
        )
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MSG_BLINK_TEXT -> sendEmptyMessageDelayed(MSG_BLINK_TEXT, 1000)
            MSG_FRAME_AVAILABLE -> encoderCallback.drawFrame()
            MSG_FILE_SAVE_COMPLETE -> encoderCallback.fileSaveComplete(msg.arg1, msg.obj as RecordingData)
            MSG_BUFFER_STATUS -> {
                val duration = msg.arg1.toLong() shl 32 or
                        (msg.arg2.toLong() and 0xffffffffL)
                encoderCallback.updateBufferStatus(duration)
            }

            else -> throw java.lang.RuntimeException("Unknown message " + msg.what)
        }
    }

    companion object {
        const val MSG_BLINK_TEXT = 0
        const val MSG_FRAME_AVAILABLE = 1
        const val MSG_FILE_SAVE_COMPLETE = 2
        const val MSG_BUFFER_STATUS = 3
    }

    interface Callback {
        fun drawFrame()
        fun fileSaveComplete(status: Int, recordingData: RecordingData)
        fun updateBufferStatus(duration: Long)
    }
}


