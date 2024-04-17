package co.nayan.c3specialist_v2.screen_sharing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityScreenSharingBinding
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingIntent
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingInvitationAction
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.CALL_END
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.CHECK_PERMISSIONS
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.MEDIA_PROJECTION_REQUEST
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.SETUP_SCREEN_SHARING_UI_FOR_REMOTE
import co.nayan.c3specialist_v2.screen_sharing.config.UserStatus
import co.nayan.c3specialist_v2.screen_sharing.models.MeetingAction
import co.nayan.c3specialist_v2.screen_sharing.models.MeetingStatus
import co.nayan.c3specialist_v2.screen_sharing.utils.RequestMediaProjection
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.models.c3_module.UserListItem
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_2_sec
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.EglBase
import org.webrtc.MediaStream
import javax.inject.Inject

@AndroidEntryPoint
class ScreenSharingActivity : AppCompatActivity() {

    @Inject
    lateinit var projectionManager: ProjectionManager
    private val screenSharingViewModel: ScreenSharingViewModel by viewModels()
    private val binding: ActivityScreenSharingBinding by viewBinding(ActivityScreenSharingBinding::inflate)

    private var isRemoteConnected = false

    private val callConnectingHandler = Handler(Looper.getMainLooper())
    private val connectionCheckRunnable: Runnable = Runnable {
        if (isRemoteConnected.not()) {
            binding.hungUpIv.performClick()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_sharing)

        binding.remoteStreamView.init(EglBase.create().eglBaseContext, null)

        setupPermissionManager()
        setupActions()
        setupObservers()
        setupClicks()

        callConnectingHandler.removeCallbacks(connectionCheckRunnable)
        callConnectingHandler.postDelayed(connectionCheckRunnable, 30_000)
    }

    override fun onDestroy() {
        binding.remoteStreamView.release()
        super.onDestroy()
    }

    private fun setupPermissionManager() {
        screenSharingViewModel.initMeetingPermissionsManager(this)
        screenSharingViewModel.permissionState.observe(this) { isGranted ->
            if (isGranted) {
                LocalBroadcastManager.getInstance(this@ScreenSharingActivity)
                    .sendBroadcast(Intent(MeetingIntent.PERMISSIONS_GRANTED))
            } else {
                setResult(RESULT_OK, Intent().apply {
                    putExtra(Extras.PERMISSION_DENIED, true)
                })
                binding.hungUpIv.performClick()
            }
        }
    }

    private fun setupActions() {
        when (intent.action) {
            MeetingServiceConstants.START_SERVICE -> {
                Intent(this, MeetingService::class.java).apply {
                    action = MeetingServiceConstants.START_SERVICE
                    putExtra(Extras.REMOTE_USER_ID, intent.getStringExtra(Extras.REMOTE_USER_ID))
                    putExtra(
                        Extras.REMOTE_USER_NAME, intent.getStringExtra(Extras.REMOTE_USER_NAME)
                    )
                    putExtra(Extras.IS_ADMIN, intent.getBooleanExtra(Extras.IS_ADMIN, false))
                }.also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        startForegroundService(it)
                    else startService(it)
                }
            }

            MeetingInvitationAction.RECEIVE -> {
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(MeetingInvitationAction.RECEIVE))
                screenSharingViewModel.requestPermission()
            }
        }
    }

    private fun setupObservers() {
        MeetingService.MEETING_SERVICE.observe(this, meetingServiceObserver)
        MeetingService.MEETING_MEDIA_STREAM.observe(this, mediaStreamObserver)
        MeetingService.MEETING_STATUS.observe(this, meetingStatusObserver)
        MeetingService.REMOTE_INFO.observe(this, remoteDataObserver)
        MeetingService.SCREEN_SHARING_UI_FOR_LOCAL.observe(
            this, localScreenSharingUIObserver
        )
        MeetingService.MEETING_SERVICE_ERROR.observe(this, meetingErrorObserver)
    }

    private val meetingErrorObserver: Observer<String?> = Observer {
        it?.let {
            report(it)
        }
    }

    private val localScreenSharingUIObserver: Observer<Pair<Boolean, Boolean?>?> = Observer {
        it?.let {
            setupScreenSharingUIForLocal(it)
        }
    }

    private val mediaStreamObserver: Observer<MediaStream?> = Observer { mediaStream ->
        mediaStream?.videoTracks?.firstOrNull()?.addSink(binding.remoteStreamView)
    }

    private val meetingStatusObserver: Observer<MeetingStatus?> = Observer { status ->
        status?.let {
            setupRemoteStatus(status)
            setupLocalStatus(status)
        }
    }

    private val remoteDataObserver: Observer<UserListItem?> = Observer { user ->
        user?.name?.let { name ->
            binding.remoteNameTxt.text = name
            binding.profileTxt.text = name.firstOrNull().toString()
        }
    }

    private fun setupClicks() {
        binding.reconnectTxt.setOnClickListener {

        }
        binding.micIv.setOnClickListener {
            if (it.isSelected) {
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(MeetingIntent.RESUME_AUDIO))
            } else {
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(MeetingIntent.PAUSE_AUDIO))
            }
            it.isSelected = !it.isSelected
        }
        binding.hungUpIv.setOnClickListener {
            stopScreenSharing()
            setupScreenSharingUIForRemote(false)
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(MeetingIntent.HUNG_UP))
        }
        binding.shareScreenIv.setOnClickListener {
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(MeetingIntent.START_SCREEN_CAPTURE))
        }
        binding.stopSharingTxt.setOnClickListener {
            stopScreenSharing()
        }
    }

    private val mediaProjectionRequest = registerForActivityResult(RequestMediaProjection()) {
        it?.let { data ->
            projectionManager.mediaProjectionPermissionResultCode = RESULT_OK
            projectionManager.mMediaProjectionPermissionResultData = data
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(MeetingIntent.START_SCREEN_SHARING))
        }
    }

    private val meetingServiceObserver: Observer<MeetingAction?> = Observer {
        it?.let {
            when (it.action) {
                MEDIA_PROJECTION_REQUEST -> {
                    mediaProjectionRequest
                        .launch(projectionManager.mediaProjectionManager?.createScreenCaptureIntent())
                }

                SETUP_SCREEN_SHARING_UI_FOR_REMOTE -> {
                    setupScreenSharingUIForRemote(isAdded = it.value as Boolean)
                }

                CHECK_PERMISSIONS -> {
                    screenSharingViewModel.requestPermission()
                }

                CALL_END -> {
                    removeObservers()
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(Intent(MeetingIntent.OBSERVERS_REMOVED))
                    postDelayed(1000) { finish() }
                }
            }
        }
    }

    private fun removeObservers() {
        MeetingService.MEETING_SERVICE.removeObserver(meetingServiceObserver)
        MeetingService.MEETING_MEDIA_STREAM.removeObserver(mediaStreamObserver)
        MeetingService.MEETING_STATUS.removeObserver(meetingStatusObserver)
        MeetingService.REMOTE_INFO.removeObserver(remoteDataObserver)
        MeetingService.SCREEN_SHARING_UI_FOR_LOCAL
            .removeObserver(localScreenSharingUIObserver)
        MeetingService.MEETING_SERVICE_ERROR.removeObserver(meetingErrorObserver)
    }

    private fun stopScreenSharing() {
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(MeetingIntent.STOP_SCREEN_SHARING))
    }

    private fun setupRemoteStatus(status: MeetingStatus) {
        val colorFilter = when (status.remoteStatus) {
            UserStatus.CONNECTED -> {
                isRemoteConnected = true
                ContextCompat.getColor(this, R.color.green)
            }

            UserStatus.DISCONNECTED -> {
                isRemoteConnected = false
                ContextCompat.getColor(this, R.color.red)

            }

            else -> {
                ContextCompat.getColor(this, R.color.colorGreyValue)
            }
        }
        binding.remoteStatusIv.setColorFilter(colorFilter)
    }

    private fun setupLocalStatus(status: MeetingStatus) {
        when (status.localStatus) {
            UserStatus.CONNECTING -> {
                binding.localStatusTxt.text = status.localStatus
                binding.reconnectTxt.gone()
                binding.localStatusContainer.visible()
                binding.localStatusContainer.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.yellow)
                )
            }

            UserStatus.CONNECTED -> {
                val user = binding.remoteNameTxt.text.toString()
                binding.localStatusTxt.text =
                    getString(R.string.local_connection_message).format(user)
                binding.reconnectTxt.gone()
                binding.localStatusContainer.visible()
                binding.localStatusContainer.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.green)
                )
                postDelayed(DELAYED_2_sec) { binding.localStatusContainer.gone() }
            }

            UserStatus.DISCONNECTED -> {
                binding.localStatusTxt.text = status.localStatus
                binding.reconnectTxt.gone()
                binding.localStatusContainer.visible()
                binding.localStatusContainer.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.red)
                )
            }
        }
    }

    private fun setupScreenSharingUIForLocal(pair: Pair<Boolean, Boolean?>) {
        val isSharingScreen = pair.first
        val isAdmin = pair.second

        if (isSharingScreen) {
            binding.remoteNonStreamingContainer.gone()
            binding.stopSharingTxt.visible()
            binding.shareScreenIv.gone()
        } else {
            binding.remoteNonStreamingContainer.visible()
            binding.stopSharingTxt.gone()
            if (isAdmin?.not() == true) {
                binding.shareScreenIv.visible()
            }
        }
    }

    private fun setupScreenSharingUIForRemote(isAdded: Boolean) {
        if (isAdded) {
            binding.remoteStreamView.visible()
            binding.remoteNonStreamingContainer.gone()
        } else {
            binding.remoteStreamView.gone()
            binding.remoteNonStreamingContainer.visible()
        }
    }

    private fun report(message: String) {
        Snackbar.make(window.decorView, message, Snackbar.LENGTH_LONG).show()
    }
}
