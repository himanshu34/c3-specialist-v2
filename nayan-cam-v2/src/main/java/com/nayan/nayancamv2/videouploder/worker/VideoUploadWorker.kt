package com.nayan.nayancamv2.videouploder.worker

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import co.nayan.c3v2.core.di.NayanCamModuleDependencies
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.isKentCam
import co.nayan.nayancamv2.R
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.di.DaggerNayanCamComponent
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.getBatteryLevel
import com.nayan.nayancamv2.util.isServiceRunning
import com.nayan.nayancamv2.videouploder.VideoUploadManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class VideoUploadWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private var appContext: Context = context

    @Inject
    lateinit var videoUploadManager: VideoUploadManager

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
                ).build().inject(this@VideoUploadWorker)

            val batteryLevel = appContext.getBatteryLevel()
            val isSufficientBattery = if (nayanCamModuleInteractor.isSurveyor()
                || nayanCamModuleInteractor.getDeviceModel().isKentCam()
            ) true else (batteryLevel >= 15)
            when {
                (appContext.isServiceRunning<ExternalCameraProcessingService>()) -> Result.failure()

                (isSufficientBattery && nayanCamModuleInteractor.isLoggedIn()) -> {
                    if (::videoUploadManager.isInitialized) {
                        setForeground(getForegroundInfo(videoUploadManager))
                        videoUploadManager.checkVideoFilesStatus()
                    }
                    Result.success()
                }

                else -> Result.failure()
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            Result.failure()
        }
    }

    private fun getForegroundInfo(videoUploadManager: VideoUploadManager): ForegroundInfo {
        val title = applicationContext.getString(R.string.upload_notification_title)
        val message = applicationContext.getString(R.string.upload_notification_message)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ForegroundInfo(
                R.id.upload_notification,
                videoUploadManager.getUploadNotification(title, message),
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        else ForegroundInfo(
            R.id.upload_notification,
            videoUploadManager.getUploadNotification(title, message)
        )
    }
}