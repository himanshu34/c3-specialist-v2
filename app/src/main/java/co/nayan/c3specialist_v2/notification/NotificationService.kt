package co.nayan.c3specialist_v2.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.screen_sharing.MeetingService
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingInvitationAction
import co.nayan.nayancamv2.R
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nayan.nayancamv2.helper.GlobalParams.exceptionHandler
import com.nayan.nayancamv2.repository.repository_notification.INotificationHelper
import com.nayan.nayancamv2.util.BROADCAST_NOTIFICATION
import com.nayan.nayancamv2.util.Notifications.AMOUNT_RECEIVED
import com.nayan.nayancamv2.util.Notifications.EVENTS_PAYOUT
import com.nayan.nayancamv2.util.Notifications.EVENT_TYPE
import com.nayan.nayancamv2.util.Notifications.NOTIFICATION_TYPE
import com.nayan.nayancamv2.util.Notifications.POINTS_RECEIVED
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Random
import javax.inject.Inject

@AndroidEntryPoint
class NotificationService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var notificationHelper: INotificationHelper

    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val TAG = NotificationService::class.java.simpleName
    private val notificationJob = SupervisorJob()
    private val notificationServiceScope = CoroutineScope(Dispatchers.IO + notificationJob)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag(TAG).e(token)
        notificationServiceScope.launch(exceptionHandler) {
            if (::userRepository.isInitialized && userRepository.isUserLoggedIn())
                userRepository.registerFCMToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.tag(TAG).e("remoteMessage.data ${remoteMessage.data}")
        Timber.tag(TAG).e("remoteMessage.notification ${remoteMessage.notification}")
        try {
            if (remoteMessage.data.containsKey(NOTIFICATION_TYPE)) {
                if (remoteMessage.data.containsKey("title") && remoteMessage.data.containsKey("message")) {
                    val title = remoteMessage.data["title"] ?: ""
                    val message = remoteMessage.data["message"] ?: ""
                    val type = remoteMessage.data[NOTIFICATION_TYPE] ?: ""

                    val notification = getNotification(
                        title = title,
                        message = message,
                        type = type
                    )
                    //notification.sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

                    notificationManager.notify(Random().nextInt(999), notification)

                    val notificationIntent = Intent(BROADCAST_NOTIFICATION).apply {
                        putExtra(NOTIFICATION_TYPE, type)
                        when (type) {
                            EVENTS_PAYOUT -> {
                                putExtra(EVENT_TYPE, remoteMessage.data[EVENT_TYPE])
                                putExtra(POINTS_RECEIVED, remoteMessage.data[POINTS_RECEIVED])
                            }

                            else -> putExtra(AMOUNT_RECEIVED, remoteMessage.data[AMOUNT_RECEIVED])
                        }
                    }

                    notificationServiceScope.launch(exceptionHandler) {
                        if (::notificationHelper.isInitialized)
                            notificationHelper.addNotification(notificationIntent)
                    }
                }
            } else initMeeting(remoteMessage)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            Firebase.crashlytics.recordException(e)
        }
    }

    private fun initMeeting(remoteMessage: RemoteMessage) {
        try {
            val data =
                RemoteNotificationData.createData(remoteMessage.data["notification"]) ?: return
            if (data.userId == null) return
            val intent = Intent(this, MeetingService::class.java).apply {
                action = MeetingInvitationAction.IN_COMING_CALL
                putExtra(Extras.REMOTE_USER_ID, data.userId.toString())
                putExtra(Extras.REMOTE_USER_NAME, data.title)
                putExtra(Extras.REMOTE_MESSAGE_BODY, data.body)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent)
            else startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
        }
    }

    private fun getNotification(
        title: String,
        message: String,
        type: String
    ): Notification {
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannel(type)
        val builder = NotificationCompat.Builder(this, type)
            .setContentTitle(title)
            .setTicker(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(false)
            .setDefaults(Notification.DEFAULT_ALL)
            .setSmallIcon(R.drawable.notification_icon)

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(type: String) {
        val channel =
            NotificationChannel(type, type, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = type
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0)
                val soundUri =
                    Uri.parse("android.resource://" + applicationContext.packageName.toString() + "/" + R.raw.flying_pop_bonus)
                if (soundUri != null) {
                    val audioAttributes: AudioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                    setSound(soundUri, audioAttributes)
                }
            }

        notificationManager.createNotificationChannel(channel)
    }
}