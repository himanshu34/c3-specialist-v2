package co.nayan.c3specialist_v2.receiver

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import co.nayan.c3specialist_v2.splash.SplashActivity
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.isKentCam
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@SuppressLint("SpecifyJobSchedulerIdRange")
@AndroidEntryPoint
class HoverRestartJobService : JobService() {

    @Inject
    lateinit var deviceInfoHelperImpl: DeviceInfoHelperImpl

    override fun onStartJob(params: JobParameters?): Boolean {
        if (::deviceInfoHelperImpl.isInitialized) {
            val deviceModel = deviceInfoHelperImpl.getDeviceConfig()?.model
            if (deviceModel?.isKentCam() == true) {
                startActivity(Intent(this, SplashActivity::class.java).apply {
                    flags = FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Return true to reschedule the job if it was interrupted
    }

    companion object {
        private const val JOB_ID_BASE = 1000
        private const val JOB_ID_RANGE = 10

        fun scheduleJob(context: Context, uniqueJobId: Int) {
            if (uniqueJobId in 0..<JOB_ID_RANGE) {
                val jobServiceComponent = ComponentName(context, HoverRestartJobService::class.java)
                val jobInfo = JobInfo.Builder(JOB_ID_BASE + uniqueJobId, jobServiceComponent)
                    .setPersisted(true) // Job survives device reboots
                    .setPeriodic(15 * 60 * 1000) // 15 minutes interval (adjust as needed)
                    .build()

                val jobScheduler = context.getSystemService(JobScheduler::class.java)
                jobScheduler.schedule(jobInfo)
            }
        }
    }
}