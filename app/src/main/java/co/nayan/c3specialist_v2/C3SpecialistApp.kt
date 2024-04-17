package co.nayan.c3specialist_v2

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest.Companion.MIN_PERIODIC_INTERVAL_MILLIS
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.nayan.c3v2.core.utils.LocaleHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.NayanCamApplication
import com.nayan.nayancamv2.util.Constants.SEGMENTS_SYNC_TAG
import com.nayan.nayancamv2.util.Constants.VIDEO_UPLOADER_TAG
import com.nayan.nayancamv2.videouploder.worker.GraphHopperSyncWorker
import com.nayan.nayancamv2.videouploder.worker.VideoUploadWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class C3SpecialistApp : NayanCamApplication() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            Firebase.crashlytics.log("thread.name: " + thread.name)
            Firebase.crashlytics.log("thread.id: " + thread.id)
            Firebase.crashlytics.log("exception: ${e.message}")
            Firebase.crashlytics.recordException(e)

            Timber.tag("ExceptionHandler").e("Called")
            Timber.tag("ExceptionHandler").e("--->> ${e.message}")
            Timber.tag("ExceptionHandler").e("--->> ${e.cause?.message}")
        }

        scheduleWorker()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    /**
     * schedule worker for video upload
     *
     */
    private fun scheduleWorker() {
        val mConstraints = Constraints.Builder().apply {
            setRequiredNetworkType(NetworkType.CONNECTED)
        }.build()
        val workManager = WorkManager.getInstance(this)

        // Video uploader configuration setup
        val periodicUploadRequest = PeriodicWorkRequestBuilder<VideoUploadWorker>(
            MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
        ).also {
            it.setConstraints(mConstraints)
            it.addTag(VIDEO_UPLOADER_TAG)
        }.build()

        workManager.enqueueUniquePeriodicWork(
            VIDEO_UPLOADER_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicUploadRequest
        )

        // Segments syncing configuration setup
        val periodicWorkRequest = PeriodicWorkRequestBuilder<GraphHopperSyncWorker>(
            MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
        ).also {
            it.setConstraints(mConstraints)
            it.addTag(SEGMENTS_SYNC_TAG)
        }.build()

        workManager.enqueueUniquePeriodicWork(
            SEGMENTS_SYNC_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }
}