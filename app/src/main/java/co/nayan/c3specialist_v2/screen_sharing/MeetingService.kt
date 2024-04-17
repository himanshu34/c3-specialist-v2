package co.nayan.c3specialist_v2.screen_sharing

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.content.res.Resources
import android.graphics.Color
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingIntent
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingInvitationAction
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.CALL_END
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.CHECK_PERMISSIONS
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.MEDIA_PROJECTION_REQUEST
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.REMOTE_CONNECTED
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.REMOTE_DISCONNECTED
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.RETURN
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.SCREEN_RESOLUTION_SCALE
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.SETUP_SCREEN_SHARING_UI_FOR_REMOTE
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.START_SCREEN_SHARING
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.START_SERVICE
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants.STOP_SCREEN_SHARING
import co.nayan.c3specialist_v2.screen_sharing.config.UserStatus
import co.nayan.c3specialist_v2.screen_sharing.models.MeetingAction
import co.nayan.c3specialist_v2.screen_sharing.models.MeetingStatus
import co.nayan.c3specialist_v2.screen_sharing.models.PeerConnectionParameters
import co.nayan.c3specialist_v2.screen_sharing.utils.WebRtcClient
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.di.preference.PreferenceHelper
import co.nayan.c3v2.core.models.c3_module.UserListItem
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.MediaStream
import javax.inject.Inject

@AndroidEntryPoint
class MeetingService : Service() {

    private var webRtcClient: WebRtcClient? = null

    @Inject
    lateinit var projectionManager: ProjectionManager

    @Inject
    lateinit var sharedStorage: SharedStorage

    @Inject
    lateinit var mPreferenceHelper: PreferenceHelper

    private var isIncomingCallActionPerformed = false

    private val inCallStatusUpdateHandler = Handler(Looper.getMainLooper())
    private val inCallStatusRunnable: Runnable = object : Runnable {
        override fun run() {
            webRtcClient?.inCallStatusUpdate()
            inCallStatusUpdateHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        enableSpeaker()
        projectionManager.setProjectionListener(projectionListener)
    }

    override fun onDestroy() {
        incomingCallHandler.removeCallbacks(inCallStatusRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        intent.action?.let { action ->
            setupAction(action)
            if (action == START_SERVICE || action == MeetingInvitationAction.IN_COMING_CALL) {
                setupExtras(intent)
            }
        }
        REMOTE_INFO.postValue(
            UserListItem(webRtcClient?.remoteId?.toInt(), webRtcClient?.remoteName)
        )
        return START_NOT_STICKY
    }

    private fun setupAction(action: String) {
        when (action) {
            START_SERVICE, MeetingInvitationAction.IN_COMING_CALL -> {
                initIntentFilter()
                webRtcClient = WebRtcClient(this, sharedStorage, mPreferenceHelper)
                webRtcClient?.setRtcListener(webRTCListener)
            }

            STOP_SCREEN_SHARING -> {
                showNotification(false, action)
            }

            START_SCREEN_SHARING -> {
                showNotification(true, action)
            }

            REMOTE_CONNECTED -> {
                showNotification(false, action)
            }

            REMOTE_DISCONNECTED -> {
                ServiceCompat.stopForeground(this, 0)
                stopSelf()
            }
        }
    }

    private fun setupExtras(intent: Intent) {
        if (webRtcClient?.remoteId == null) {
            webRtcClient?.remoteId = intent.getStringExtra(Extras.REMOTE_USER_ID)
        }
        if (webRtcClient?.remoteName == null) {
            webRtcClient?.remoteName = intent.getStringExtra(Extras.REMOTE_USER_NAME)
        }
        webRtcClient?.isAdmin = intent.getBooleanExtra(Extras.IS_ADMIN, false)

        val deviceWidth = Resources.getSystem().displayMetrics.widthPixels
        val deviceHeight = Resources.getSystem().displayMetrics.heightPixels

        val peerConnectionParameters = PeerConnectionParameters(
            videoWidth = deviceWidth / SCREEN_RESOLUTION_SCALE,
            videoHeight = deviceHeight / SCREEN_RESOLUTION_SCALE
        )
        if (webRtcClient?.peerConnectionParams == null) {
            webRtcClient?.peerConnectionParams = peerConnectionParameters
        }
    }

    private val webRTCListener = object : WebRtcClient.RtcListener {
        override fun onAddRemoteStream(mediaStream: MediaStream?, endPoint: Int) {
            if (mediaStream == null) return
            MEETING_MEDIA_STREAM.postValue(mediaStream)
        }

        override fun onLocalStatusChanged() {
            val status = MeetingStatus(webRtcClient?.localStatus, webRtcClient?.remoteStatus)
            MEETING_STATUS.postValue(status)
            if (webRtcClient?.localStatus == UserStatus.CONNECTED) {
                incomingCallHandler.removeCallbacks(inCallStatusRunnable)
                incomingCallHandler.post(inCallStatusRunnable)
                if (webRtcClient?.isAdmin == true) {
                    val action = MeetingAction(CHECK_PERMISSIONS, null)
                    MEETING_SERVICE.postValue(action)
                } else {
                    showCallingNotification()
                }
            } else {
                incomingCallHandler.removeCallbacks(inCallStatusRunnable)
            }
        }

        override fun onRemoteConnected() {
            val status = MeetingStatus(webRtcClient?.localStatus, webRtcClient?.remoteStatus)
            MEETING_STATUS.postValue(status)
            if (webRtcClient?.isSharingScreen?.not() == true) {
                setupAction(REMOTE_CONNECTED)
            }
        }

        override fun onRemoteDisconnected() {
            val status = MeetingStatus(webRtcClient?.localStatus, webRtcClient?.remoteStatus)
            MEETING_STATUS.postValue(status)
            setupAction(REMOTE_DISCONNECTED)
            MEETING_SERVICE.postValue(MeetingAction(CALL_END, null))
        }

        override fun onScreenSharing(isAdded: Boolean) {
            MEETING_SERVICE.postValue(MeetingAction(SETUP_SCREEN_SHARING_UI_FOR_REMOTE, isAdded))
        }

        override fun onHungUp() {
            MEETING_SERVICE.postValue(MeetingAction(SETUP_SCREEN_SHARING_UI_FOR_REMOTE, false))
            disconnect()
        }

        override fun onCallReject() {
            disconnect()
        }
    }

    private fun initIntentFilter() {
        IntentFilter().apply {
            addAction(MeetingIntent.PERMISSIONS_GRANTED)
            addAction(MeetingIntent.START_SCREEN_CAPTURE)
            addAction(MeetingIntent.START_SCREEN_SHARING)
            addAction(MeetingIntent.STOP_SCREEN_SHARING)
            addAction(MeetingIntent.RESUME_AUDIO)
            addAction(MeetingIntent.PAUSE_AUDIO)
            addAction(MeetingIntent.HUNG_UP)
            addAction(MeetingIntent.OBSERVERS_REMOVED)
            addAction(MeetingInvitationAction.CANCEL)
            addAction(MeetingInvitationAction.RECEIVE)
            LocalBroadcastManager.getInstance(this@MeetingService)
                .registerReceiver(meetingBroadcastReceiver, this)
        }
    }

    private val meetingBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MeetingIntent.PERMISSIONS_GRANTED -> {
                    if (webRtcClient?.isAdmin == true) {
                        webRtcClient?.setupAdmin()
                    } else {
                        startScreenCapture()
                    }
                }

                MeetingIntent.STOP_SCREEN_SHARING -> {
                    if (webRtcClient?.isSharingScreen == true) {
                        projectionManager.reset()
                        setupAction(STOP_SCREEN_SHARING)
                    }
                }

                MeetingIntent.RESUME_AUDIO -> {
                    webRtcClient?.startAudioStream()
                }

                MeetingIntent.PAUSE_AUDIO -> {
                    webRtcClient?.stopAudioStream()
                }

                MeetingIntent.HUNG_UP -> {
                    webRtcClient?.hungUp()
                    disconnect()
                }

                MeetingIntent.START_SCREEN_SHARING -> {
                    setupAction(START_SCREEN_SHARING)
                    projectionManager.startProjection()
                }

                MeetingIntent.START_SCREEN_CAPTURE -> {
                    startScreenCapture()
                }

                MeetingIntent.OBSERVERS_REMOVED -> {
                    resetLiveData()
                    webRtcClient = null
                    unregisterMeetingReceiver()
                    incomingCallHandler.removeCallbacks(inCallStatusRunnable)
                    ServiceCompat.stopForeground(this@MeetingService, 0)
                    stopSelf()
                }

                MeetingInvitationAction.RECEIVE -> {
                    isIncomingCallActionPerformed = true
                    ServiceCompat.stopForeground(this@MeetingService, 0)
                }

                MeetingInvitationAction.CANCEL -> {
                    isIncomingCallActionPerformed = true
                    webRtcClient?.rejectCall()
                    ServiceCompat.stopForeground(this@MeetingService, 0)
                    stopSelf()
                }
            }
        }
    }

    private fun unregisterMeetingReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(meetingBroadcastReceiver)
    }

    private fun resetLiveData() {
        SCREEN_SHARING_UI_FOR_LOCAL.postValue(null)
        MEETING_SERVICE.postValue(null)
        MEETING_SERVICE_ERROR.postValue(null)
        MEETING_MEDIA_STREAM.postValue(null)
        MEETING_STATUS.postValue(MeetingStatus(UserStatus.CONNECTING, UserStatus.DISCONNECTED))
        REMOTE_INFO.postValue(null)
    }

    private val projectionListener = object : ProjectionListener {
        override fun onStartProjection() {
            startScreenSharing()
        }

        override fun onStopProjection(shouldFinish: Boolean) {
            if (shouldFinish) {
                MEETING_SERVICE.postValue(MeetingAction(CALL_END, null))
            } else {
                webRtcClient?.removeVideoTrack()
                SCREEN_SHARING_UI_FOR_LOCAL.postValue(Pair(false, webRtcClient?.isAdmin))
            }
        }

        override fun showErrorMessage(message: String) {
            MEETING_SERVICE_ERROR.postValue(message)
        }
    }

    private fun startScreenSharing() {
        if (webRtcClient?.isSharingScreen?.not() == true) {
            val screenCapturer = projectionManager.createScreenCapturer()
            webRtcClient?.onScreenSharingStarted(screenCapturer)
            SCREEN_SHARING_UI_FOR_LOCAL.postValue(Pair(true, webRtcClient?.isAdmin))
        }
    }

    private fun startScreenCapture() {
        if (projectionManager.mediaProjection == null) {
            projectionManager.mediaProjectionManager =
                application.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            MEETING_SERVICE.postValue(MeetingAction(MEDIA_PROJECTION_REQUEST, null))
        }
    }

    private fun showNotification(isSharingScreen: Boolean, intentAction: String) {
        val description: String
        val title: String
        if (isSharingScreen) {
            description = "You are sharing your screen.Tap to return to the meeting."
            title = "Sharing Screen"
        } else {
            description = "Tap to return to the meeting."
            title = "Your meeting"
        }

        val channelId = getString(R.string.notification_channel_id)
        val notificationId = 999

        val stopSharingAction = Intent(this, MeetingNotificationActionReceiver::class.java).apply {
            action = STOP_SCREEN_SHARING
        }
        val stopSharingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            stopSharingAction,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openMeetingScreenAction = Intent(this, ScreenSharingActivity::class.java).apply {
            action = RETURN
        }
        val openMeetingScreenIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openMeetingScreenAction,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId).apply {
            setSmallIcon(co.nayan.nayancamv2.R.drawable.notification_icon)
            setContentTitle(title)
            setContentText(description)
            setStyle(NotificationCompat.BigTextStyle().bigText(description))
            priority = NotificationCompat.PRIORITY_DEFAULT
            color = Color.RED
            setContentIntent(openMeetingScreenIntent)
            if (isSharingScreen) {
                addAction(R.drawable.ic_stop, "STOP SHARING", stopSharingIntent)
            }
        }

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else startForeground(notificationId, notification)
        MEETING_SERVICE.postValue(MeetingAction(intentAction, null))
    }

    private val incomingCallHandler = Handler(Looper.getMainLooper())
    private val incomingCallRunnable: Runnable = Runnable {
        if (isIncomingCallActionPerformed.not()) {
            webRtcClient?.disconnect()
            ServiceCompat.stopForeground(this, 0)
            stopSelf()
        }
    }

    private fun showCallingNotification() {
        incomingCallHandler.removeCallbacks(incomingCallRunnable)
        incomingCallHandler.postDelayed(incomingCallRunnable, 30_000)

        val channelId = getString(R.string.notification_channel_id)
        val notificationId = 999
        val receiveCallAction = Intent(this, ScreenSharingActivity::class.java).apply {
            action = MeetingInvitationAction.RECEIVE
        }
        val receiveCallPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            receiveCallAction,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelCallAction = Intent(this, MeetingNotificationActionReceiver::class.java).apply {
            action = MeetingInvitationAction.CANCEL
        }
        val cancelCallPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            cancelCallAction,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        NotificationCompat.Builder(this, channelId).apply {
            setContentTitle("Incoming Call")
            setContentText("${webRtcClient?.remoteName} is calling...")
            setSmallIcon(co.nayan.nayancamv2.R.drawable.notification_icon)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setAutoCancel(true)
            setTimeoutAfter(30_000)
            setFullScreenIntent(receiveCallPendingIntent, true)
            priority = NotificationCompat.PRIORITY_HIGH
            addAction(R.drawable.bg_call_green, "Receive Call", receiveCallPendingIntent)
            addAction(R.drawable.bg_call_red, "Cancel call", cancelCallPendingIntent)
        }.also {
            val incomingCallNotification = it.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    notificationId,
                    incomingCallNotification,
                    FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else startForeground(notificationId, incomingCallNotification)
        }
    }

    private fun disconnect() {
        webRtcClient?.disconnect()
        projectionManager.reset(shouldFinish = true)
    }

    private fun enableSpeaker() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.apply {
            mode = AudioManager.MODE_IN_CALL
            isSpeakerphoneOn = true
        }
    }

    companion object {
        val SCREEN_SHARING_UI_FOR_LOCAL = MutableLiveData<Pair<Boolean, Boolean?>?>()
        val MEETING_SERVICE = MutableLiveData<MeetingAction?>()
        val MEETING_SERVICE_ERROR = MutableLiveData<String?>()
        val MEETING_MEDIA_STREAM = MutableLiveData<MediaStream?>()
        val MEETING_STATUS = MutableLiveData(
            MeetingStatus(UserStatus.CONNECTING, UserStatus.DISCONNECTED)
        )
        val REMOTE_INFO = MutableLiveData<UserListItem?>()
    }
}
