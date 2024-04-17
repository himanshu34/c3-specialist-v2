package co.nayan.c3specialist_v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Random

class MyBootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED)
            HoverRestartJobService.scheduleJob(context!!, Random().nextInt(10))
    }
}