package co.nayan.c3specialist_v2.screen_sharing

import android.content.Context
import androidx.activity.result.ActivityResultCaller
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingPermissionsListener
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingPermissionsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class ScreenSharingViewModel @Inject constructor(
    private val permissionManagerProvider: PermissionManagerProvider
) : ViewModel() {

    private var meetingPermissionsManager: MeetingPermissionsManager? = null

    private val _permissionState: MutableLiveData<Boolean> = MutableLiveData()
    val permissionState: LiveData<Boolean> = _permissionState

    fun initMeetingPermissionsManager(activityResultCaller: ActivityResultCaller) {
        meetingPermissionsManager = permissionManagerProvider.provide(activityResultCaller)
        meetingPermissionsManager?.meetingPermissionListener = object : MeetingPermissionsListener {
            override fun onPermissionGranted() {
                _permissionState.value = true
            }

            override fun onPermissionsDenied() {
                _permissionState.value = false
            }
        }
    }

    fun requestPermission() {
        meetingPermissionsManager?.requestPermissions()
    }
}

class PermissionManagerProvider @Inject constructor(@ApplicationContext private val context: Context) {
    fun provide(
        activityResultCaller: ActivityResultCaller?
    ): MeetingPermissionsManager {
        return MeetingPermissionsManager(activityResultCaller)
    }
}