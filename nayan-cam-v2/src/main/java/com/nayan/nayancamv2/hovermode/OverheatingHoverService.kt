package com.nayan.nayancamv2.hovermode

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.LocaleHelper
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import com.nayan.nayancamv2.BaseHoverService
import com.nayan.nayancamv2.NayanCamActivity
import com.nayan.nayancamv2.helper.GlobalParams.currentTemperature
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.temperature.TemperatureUtil
import com.nayan.nayancamv2.util.Constants
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_2_sec
import com.nayan.nayancamv2.util.isServiceRunning
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class OverheatingHoverService : BaseHoverService() {

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    override fun openNayanRecorder() {
        showToast(getString(R.string.please_wait_starting_recorder))
        postDelayed(TimeUnit.SECONDS.toMillis(2)) {
            startActivity(
                Intent(this, NayanCamActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Constants.IS_FROM_HOVER, true)
            )
        }
        stopSelf()
    }

    override fun restartCameraService() {
        val intent = Intent(this, NayanCamActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(Constants.IS_FROM_BACKGROUND, true)
        postDelayed(DELAYED_2_sec) { startActivity(intent) }
        stopSelf()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun checkTemperature(
        temperature: Float,
        driverLiteThreshold: Float,
        overHeatingThreshold: Float
    ) {
        currentTemperature = temperature
        Timber.d("checkTemperature: %s", temperature)
        val currentTime = System.currentTimeMillis()
        val secondsBeforeLastMessage =
            TimeUnit.MILLISECONDS.toSeconds(currentTime - lastShownTempMessage)
        Timber.e("secondsBeforeLastMessage %s", secondsBeforeLastMessage)

        if (secondsBeforeLastMessage > 25 || temperature >= overHeatingThreshold) {
            lastShownTempMessage = currentTime
            val message = TemperatureUtil.getTempMessage(
                this@OverheatingHoverService,
                temperature,
                driverLiteThreshold,
                overHeatingThreshold
            )
            updateHoverIcon(HoverIconType.TemperatureError)
            temperatureMessageTxt.text = message
            messageParentLayout.visible()
            postDelayed(DELAYED_2_sec) { messageParentLayout.gone() }
        }

        if (temperature <= overHeatingThreshold) {
            sharedPrefManager.setForcedLITEMode(false)
            launchHoverService()
        }
    }

    override fun setNotification() {
        try {
            val notificationId = 1234
            val channelId = "co.nayan.android.overheating_service"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                createNotificationChannel(channelId, getString(R.string.hover_service))

            val notification = NotificationCompat.Builder(this, channelId).apply {
                setContentTitle(getString(R.string.hover_service))
                setContentText(getString(R.string.cooldown_mode_started))
                setSmallIcon(R.drawable.notification_icon)
            }.build()
            notification.flags =
                NotificationCompat.FLAG_ONGOING_EVENT or NotificationCompat.FLAG_NO_CLEAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val serviceType = FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                ServiceCompat.startForeground(this, notificationId, notification, serviceType)
            } else startForeground(notificationId, notification)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                // App not in a valid state to start foreground service
                // (e.g. started from bg)
            }
        }
    }

    private fun launchHoverService() {
        floatingView.gone()
        stopSelf()
        postDelayed(DELAYED_2_sec) {
            Intent(this, BackgroundCameraService::class.java).apply {
                if (isServiceRunning<BackgroundCameraService>()) stopService(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(this)
                else startService(this)
            }
        }
    }
}