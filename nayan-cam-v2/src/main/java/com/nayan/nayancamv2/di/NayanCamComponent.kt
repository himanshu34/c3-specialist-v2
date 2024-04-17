package com.nayan.nayancamv2.di

import android.content.Context
import co.nayan.c3v2.core.di.NayanCamModuleDependencies
import com.nayan.nayancamv2.BaseActivity
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.BaseHoverService
import com.nayan.nayancamv2.hovermode.HoverService
import com.nayan.nayancamv2.hovermode.OverheatingHoverService
import com.nayan.nayancamv2.hovermode.PortraitHoverService
import com.nayan.nayancamv2.modeldownloader.AIModelsSyncWorker
import com.nayan.nayancamv2.scout.CameraConnectionFragment
import com.nayan.nayancamv2.scout.ScoutModePreviewFragment
import com.nayan.nayancamv2.ui.cam.NayanCamFragment
import com.nayan.nayancamv2.videouploder.worker.AttendanceSyncWorker
import com.nayan.nayancamv2.videouploder.worker.GraphHopperSyncWorker
import com.nayan.nayancamv2.videouploder.worker.VideoFilesSyncWorker
import com.nayan.nayancamv2.videouploder.worker.VideoUploadWorker
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    dependencies = [NayanCamModuleDependencies::class],
    modules = [NayanCamModule::class, ViewModelModule::class, ServiceModule::class]
)
interface NayanCamComponent {

    fun inject(activity: BaseActivity)
    fun inject(fragment: NayanCamFragment)
    fun inject(fragment: CameraConnectionFragment)
    fun inject(fragment: ScoutModePreviewFragment)
    fun inject(worker: VideoUploadWorker)
    fun inject(graphHopperWorker: GraphHopperSyncWorker)
    fun inject(attendanceWorker: AttendanceSyncWorker)
    fun inject(aiModelsSyncWorker: AIModelsSyncWorker)
    fun inject(videoFilesSyncWorker: VideoFilesSyncWorker)
    fun inject(hoverService: HoverService)
    fun inject(hoverService: BaseHoverService)
    fun inject(externalCameraProcessingService: ExternalCameraProcessingService)

    @Component.Builder
    interface Builder {
        fun context(@BindsInstance context: Context): Builder
        fun appDependencies(nayanModuleDependencies: NayanCamModuleDependencies): Builder
        fun build(): NayanCamComponent
    }
}