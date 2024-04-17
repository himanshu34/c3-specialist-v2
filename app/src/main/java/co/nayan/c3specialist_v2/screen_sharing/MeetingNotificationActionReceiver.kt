package co.nayan.c3specialist_v2.screen_sharing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingIntent
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingInvitationAction
import co.nayan.c3specialist_v2.screen_sharing.config.MeetingServiceConstants

class MeetingNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            when (intent?.action) {
                MeetingServiceConstants.STOP_SCREEN_SHARING -> {
                    LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(Intent(MeetingIntent.STOP_SCREEN_SHARING))
                }
                MeetingInvitationAction.CANCEL -> {
                    LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(Intent(intent.action))
                }
                else -> {
                }
            }
        }
    }
}