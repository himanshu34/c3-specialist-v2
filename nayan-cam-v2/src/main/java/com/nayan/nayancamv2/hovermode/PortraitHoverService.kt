package com.nayan.nayancamv2.hovermode

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import co.nayan.c3v2.core.di.NayanCamModuleDependencies
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.showToast
import co.nayan.c3v2.core.utils.LocaleHelper
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.nayancamv2.R
import com.nayan.nayancamv2.BaseHoverService
import com.nayan.nayancamv2.NayanCamActivity
import com.nayan.nayancamv2.createRotateAnimation
import com.nayan.nayancamv2.di.DaggerNayanCamComponent
import com.nayan.nayancamv2.handleOrientation
import com.nayan.nayancamv2.helper.GlobalParams.currentTemperature
import com.nayan.nayancamv2.helper.GlobalParams.isInCorrectScreenOrientation
import com.nayan.nayancamv2.hovermode.HandlerWithID.Companion.runnableIDStartCamera
import com.nayan.nayancamv2.hovermode.HandlerWithID.Companion.runnableIDTilt
import com.nayan.nayancamv2.temperature.TemperatureUtil
import com.nayan.nayancamv2.util.CommonUtils
import com.nayan.nayancamv2.util.Constants
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_2_sec
import com.nayan.nayancamv2.util.RotationLiveData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Runnable
import timber.log.Timber
import java.util.concurrent.TimeUnit

class PortraitHoverService : BaseHoverService() {


    private val rotateAnim by lazy { createRotateAnimation() }
    private lateinit var rotationLiveData: RotationLiveData
    private val tiltHandler by lazy { HandlerWithID() }
    private val tiltRunnable = Runnable { updateHoverIcon(HoverIconType.OrientationError) }

    private val restartCameraHandler by lazy { HandlerWithID() }
    private val restartCameraRunnable = Runnable {
        exit()
        Intent(this, BackgroundCameraService::class.java).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(this)
            else startService(this)
        }
    }

    override fun openNayanRecorder() {
        showToast(getString(R.string.please_wait_starting_recorder))
        postDelayed(1500) {
            startActivity(
                Intent(this, NayanCamActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Constants.IS_FROM_HOVER, true)
            )
        }
        stopSelf()
    }

    override fun checkTemperature(temperature: Float, driverLiteThreshold: Float, overHeatingThreshold: Float) {
        currentTemperature = temperature
        Timber.d("checkTemperature: %s", temperature)
        val currentTime = System.currentTimeMillis()
        val secondsBeforeLastMessage =
            TimeUnit.MILLISECONDS.toSeconds(currentTime - lastShownTempMessage)
        Timber.e("secondsBeforeLastMessage %s", secondsBeforeLastMessage)

        if (secondsBeforeLastMessage > 25 || temperature >= overHeatingThreshold) {
            lastShownTempMessage = currentTime
            val message = TemperatureUtil.getTempMessage(this, temperature, driverLiteThreshold, overHeatingThreshold)
            temperatureMessageTxt.text = message
            messageParentLayout.visible()
            postDelayed(DELAYED_2_sec) { messageParentLayout.gone() }
        }

        when {
            temperature >= overHeatingThreshold -> {
                CommonUtils.playSound(
                    R.raw.camera_error_alert,
                    this,
                    storageUtil.getVolumeLevel()
                )
                exit()
                postDelayed(DELAYED_2_sec) {
                    Intent(this, OverheatingHoverService::class.java).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            startForegroundService(this)
                        else startService(this)
                    }
                }
            }

            else -> updateHoverIcon(HoverIconType.DefaultHoverIcon)
        }
    }

    override fun setNotification() {
        try {
            val notificationId = 1234
            val channelId = "co.nayan.android.portrait_service"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                createNotificationChannel(channelId, getString(R.string.hover_service))
            val notification = NotificationCompat.Builder(this, channelId).apply {
                setContentTitle(getString(R.string.hover_service))
                setContentText(getString(R.string.vertical_mode_started))
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


    override fun restartCameraService() {
        restartingCameraService()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        DaggerNayanCamComponent.builder()
            .context(this)
            .appDependencies(
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    NayanCamModuleDependencies::class.java
                )
            ).build().inject(this)

        setUpObservers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (::rotationLiveData.isInitialized) rotationLiveData.onInactive()
        restartCameraHandler.removeCallbacks(restartCameraRunnable)
        tiltHandler.removeCallbacks(tiltRunnable)
        super.onDestroy()
    }

    private fun setUpObservers() {
        rotationLiveData = RotationLiveData(this).apply {
            observe(this@PortraitHoverService) { state ->
                handleOrientation(
                    state,
                    mNayanCamModuleInteractor.getDeviceModel(),
                    orientationCallback
                )
            }
            onActive()
        }
    }

    private val orientationCallback = object : (Boolean, Boolean) -> Unit {

        override fun invoke(orientationStatus: Boolean, isDefault: Boolean) {
            handleTilt(orientationStatus)
            if (!isDefault && isInCorrectScreenOrientation == orientationStatus) return
            else {
                isInCorrectScreenOrientation = orientationStatus

                if (isInCorrectScreenOrientation
                    && !restartCameraHandler.hasActiveRunnable(runnableIDStartCamera)
                ) restartCameraHandler.dropCameraDelay(runnableIDStartCamera, restartCameraRunnable)
                else restartCameraHandler.removeCallbacks(restartCameraRunnable)
            }
        }
    }

    private fun handleTilt(orientationStatus: Boolean) {
        if (orientationStatus) tiltHandler.removeCallbacks(tiltRunnable)
        else if (!tiltHandler.hasActiveRunnable(runnableIDTilt))
            tiltHandler.tiltDelayed(runnableIDTilt, tiltRunnable)
    }

    override fun showOrientationHint() {
        floatingViewIvLogo.setImageResource(R.drawable.ic_black_rotate)
        if (rotateAnim.hasStarted().not() || rotateAnim.hasEnded()) {
            floatingViewIvLogo.clearAnimation()
            floatingViewIvLogo.startAnimation(rotateAnim)
        }
    }

    private fun restartingCameraService() {
        floatingView.gone()
        //starting the camera activity, as camera can't be started from the background service
        val intent = Intent(this, NayanCamActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(Constants.IS_FROM_BACKGROUND, true)
        postDelayed(DELAYED_2_sec) { startActivity(intent) }
        stopSelf()
    }
}