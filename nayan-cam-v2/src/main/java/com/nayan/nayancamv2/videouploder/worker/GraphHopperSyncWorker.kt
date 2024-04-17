package com.nayan.nayancamv2.videouploder.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import co.nayan.c3v2.core.config.Role.DRIVER
import co.nayan.c3v2.core.di.NayanCamModuleDependencies
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.nayancamv2.R
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.di.DaggerNayanCamComponent
import com.nayan.nayancamv2.videouploder.GraphHopperSyncManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class GraphHopperSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private var appContext: Context = context

    @Inject
    lateinit var graphHopperManager: GraphHopperSyncManager

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor

    override suspend fun doWork(): Result = coroutineScope {
        return@coroutineScope try {
            DaggerNayanCamComponent.builder()
                .context(appContext)
                .appDependencies(
                    EntryPointAccessors.fromApplication(
                        applicationContext,
                        NayanCamModuleDependencies::class.java
                    )
                ).build().inject(this@GraphHopperSyncWorker)

            // GraphHopper Syncing
            val currentRoles = nayanCamModuleInteractor.getRoles()
            if (nayanCamModuleInteractor.isLoggedIn() && currentRoles.contains(DRIVER)) {
                setForeground(getForegroundInfo())
                val isUserLoggingOut = inputData.getBoolean("isUserLoggingOut", false)
                if (::graphHopperManager.isInitialized)
                    graphHopperManager.startSyncingData(isUserLoggingOut)
            }

            Result.success()
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = appContext.getString(R.string.segments_sync_started)
        val message = appContext.getString(R.string.segments_sync_assets)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ForegroundInfo(
                R.id.syncing_notification,
                getUploadNotification(title, message),
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        else ForegroundInfo(R.id.syncing_notification, getUploadNotification(title, message))
    }

    private fun getUploadNotification(title: String, message: String): Notification {
        val channelId = "com.nayan.segment_sync_service"

        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, title, IMPORTANCE_LOW)
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        val notificationBuilder = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(message)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.notification_icon)

        return notificationBuilder.build()
    }
}