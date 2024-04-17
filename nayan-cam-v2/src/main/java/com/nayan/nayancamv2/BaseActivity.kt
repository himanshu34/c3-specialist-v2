package com.nayan.nayancamv2

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import co.nayan.c3v2.core.di.NayanCamModuleDependencies
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.CameraConfig
import co.nayan.c3v2.core.utils.LocaleHelper
import com.nayan.nayancamv2.ai.AIModelManager
import com.nayan.nayancamv2.ai.CameraProcessor
import com.nayan.nayancamv2.di.DaggerNayanCamComponent
import com.nayan.nayancamv2.di.NayanCamComponent
import com.nayan.nayancamv2.helper.CameraHelper
import com.nayan.nayancamv2.helper.CameraPreviewListener
import com.nayan.nayancamv2.impl.SyncWorkflowManagerImpl
import com.nayan.nayancamv2.repository.repository_cam.INayanCamRepository
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.videouploder.AttendanceSyncManager
import com.nayan.nayancamv2.videouploder.GraphHopperSyncManager
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var nayanCamComponent: NayanCamComponent

    @Inject
    lateinit var nayanCamModuleInteractor: NayanCamModuleInteractor

    @Inject
    lateinit var cameraProcessor: CameraProcessor

    @Inject
    lateinit var cameraPreviewListener: CameraPreviewListener

    @Inject
    lateinit var cameraConfig: CameraConfig

    @Inject
    lateinit var cameraHelper: CameraHelper

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    lateinit var aiModelManager: AIModelManager

    @Inject
    lateinit var storageUtil: StorageUtil

    @Inject
    lateinit var nayanCamRepository: INayanCamRepository

    @Inject
    lateinit var graphHopperSyncManager: GraphHopperSyncManager

    @Inject
    lateinit var attendanceSyncManager: AttendanceSyncManager

    @Inject
    lateinit var syncWorkflowManagerImpl: SyncWorkflowManagerImpl


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (::nayanCamComponent.isInitialized.not()) {
                nayanCamComponent = DaggerNayanCamComponent.builder()
                    .context(this)
                    .appDependencies(
                        EntryPointAccessors.fromApplication(
                            applicationContext,
                            NayanCamModuleDependencies::class.java
                        )
                    ).build()
            }

            nayanCamComponent.inject(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(context))
    }
}