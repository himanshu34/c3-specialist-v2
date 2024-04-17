package co.nayan.c3specialist_v2.screen_sharing.config

import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

class MeetingPermissionsManager(activityResultCaller: ActivityResultCaller?) {

    var meetingPermissionListener: MeetingPermissionsListener? = null

    private val requestPermission =
        activityResultCaller?.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                meetingPermissionListener?.onPermissionGranted()
            } else {
                meetingPermissionListener?.onPermissionsDenied()
            }
        }

    fun requestPermissions() {
        requestPermission?.launch(
            arrayOf(
                android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
                android.Manifest.permission.RECORD_AUDIO
            )
        )
    }
}

interface MeetingPermissionsListener {
    fun onPermissionGranted()
    fun onPermissionsDenied()
}