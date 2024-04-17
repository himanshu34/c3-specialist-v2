package co.nayan.c3specialist_v2.screen_sharing

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.VideoCapturer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectionManager @Inject constructor() {

    var mMediaProjectionPermissionResultData: Intent? = null
    var mediaProjectionPermissionResultCode = 0
    var mediaProjection: MediaProjection? = null
    var mediaProjectionManager: MediaProjectionManager? = null
    private var projectionListener: ProjectionListener? = null

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Timber.e("OnStop")
        }
    }

    fun reset(shouldFinish: Boolean = false) {
        if (mediaProjection != null) {
            mediaProjectionCallback.onStop()
            mediaProjection?.stop()
            mediaProjection?.unregisterCallback(mediaProjectionCallback)

            mMediaProjectionPermissionResultData = null
            mediaProjectionPermissionResultCode = 0
            mediaProjection = null
            mediaProjectionManager = null
        }

        projectionListener?.onStopProjection(shouldFinish)
    }

    fun startProjection() {
        mMediaProjectionPermissionResultData?.let { data ->
            mediaProjection = mediaProjectionManager?.getMediaProjection(
                AppCompatActivity.RESULT_OK, data
            )
            mediaProjection?.registerCallback(mediaProjectionCallback, null)
            projectionListener?.onStartProjection()
        }
    }

    fun setProjectionListener(toSet: ProjectionListener) {
        projectionListener = toSet
    }

    fun createScreenCapturer(): VideoCapturer? {
        if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            projectionListener?.showErrorMessage("User didn't give permission to capture the screen.")
            return null
        }
        return ScreenCapturerAndroid(mMediaProjectionPermissionResultData, mediaProjectionCallback)
    }
}

interface ProjectionListener {
    fun onStartProjection()
    fun onStopProjection(shouldFinish: Boolean)
    fun showErrorMessage(message: String)
}