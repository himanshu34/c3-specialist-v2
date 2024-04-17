package com.nayan.nayancamv2.di

import android.content.Context
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.CameraConfig
import com.nayan.nayancamv2.ai.AIModelManager
import com.nayan.nayancamv2.ai.CameraProcessor
import com.nayan.nayancamv2.di.database.AppDatabase
import com.nayan.nayancamv2.di.database.LocationDAO
import com.nayan.nayancamv2.di.database.SegmentTrackDAO
import com.nayan.nayancamv2.di.database.VideoUploaderDAO
import com.nayan.nayancamv2.helper.CameraHelper
import com.nayan.nayancamv2.helper.CameraPreviewListener
import com.nayan.nayancamv2.helper.CameraUtils
import com.nayan.nayancamv2.helper.IMetaDataHelper
import com.nayan.nayancamv2.helper.IRecordingHelper
import com.nayan.nayancamv2.helper.MetaDataHelperImpl
import com.nayan.nayancamv2.helper.RecordingHelperImpl
import com.nayan.nayancamv2.nightmode.NightModeConstraintSelector
import com.nayan.nayancamv2.repository.repository_cam.INayanCamRepository
import com.nayan.nayancamv2.repository.repository_cam.NayanCamRepositoryImpl
import com.nayan.nayancamv2.repository.repository_graphopper.GraphHopperRepositoryImpl
import com.nayan.nayancamv2.repository.repository_graphopper.IGraphHopperRepository
import com.nayan.nayancamv2.repository.repository_location.ILocationRepository
import com.nayan.nayancamv2.repository.repository_location.LocationRepositoryImpl
import com.nayan.nayancamv2.repository.repository_uploader.IVideoUploaderRepository
import com.nayan.nayancamv2.repository.repository_uploader.VideoUploaderRepositoryImpl
import com.nayan.nayancamv2.storage.FileMetaDataEditor
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.temperature.StateManager
import com.nayan.nayancamv2.temperature.TemperatureProvider
import com.nayan.nayancamv2.util.DriverErrorUtils
import com.nayan.nayancamv2.videouploder.AttendanceSyncManager
import com.nayan.nayancamv2.videouploder.GraphHopperSyncManager
import com.nayan.nayancamv2.videouploder.VideoUploadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NayanCamModule {
    @Provides
    @Singleton
    fun sharedPrefManager(
        context: Context
    ): SharedPrefManager = SharedPrefManager(context)

    @Provides
    @Singleton
    fun provideFileMetaDataEditor(): FileMetaDataEditor = FileMetaDataEditor()

    @Provides
    @Singleton
    fun provideStorageUtils(
        context: Context,
        sharedPrefManager: SharedPrefManager,
        nayanCamModuleInteractor: NayanCamModuleInteractor
    ) = StorageUtil(context, sharedPrefManager, nayanCamModuleInteractor)

    @Provides
    @Singleton
    fun provideINayanCamRepository(
        nayanCamRepositoryImpl: NayanCamRepositoryImpl
    ): INayanCamRepository {
        return nayanCamRepositoryImpl
    }

    @Provides
    @Singleton
    fun provideILocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): ILocationRepository {
        return locationRepositoryImpl
    }

    @Provides
    @Singleton
    fun provideIGraphHopperRepository(
        graphHopperRepositoryImpl: GraphHopperRepositoryImpl
    ): IGraphHopperRepository {
        return graphHopperRepositoryImpl
    }

    @Provides
    @Singleton
    fun provideVideoUploaderRepository(
        videoUploaderRepositoryImpl: VideoUploaderRepositoryImpl
    ): IVideoUploaderRepository {
        return videoUploaderRepositoryImpl
    }

    @Provides
    @Singleton
    fun nightModeConstraintSelector(
        sharedPrefManager: SharedPrefManager
    ): NightModeConstraintSelector = NightModeConstraintSelector(sharedPrefManager)

    @Provides
    @Singleton
    fun cameraUtils(
        context: Context,
        sharedPrefManager: SharedPrefManager
    ): CameraUtils = CameraUtils(sharedPrefManager, context)

    @Provides
    @Singleton
    fun provideUploadManager(
        context: Context,
        storageUtil: StorageUtil,
        videoUploaderRepository: IVideoUploaderRepository,
        fileMetaDataEditor: FileMetaDataEditor,
        cameraConfig: CameraConfig,
        nayanCamModuleInteractor: NayanCamModuleInteractor,
        driverErrorUtils: DriverErrorUtils
    ) = VideoUploadManager(
        context,
        storageUtil,
        videoUploaderRepository,
        fileMetaDataEditor,
        cameraConfig,
        nayanCamModuleInteractor,
        driverErrorUtils
    )

    @Provides
    @Singleton
    fun provideTemperatureProvider(
        context: Context,
        sharedPrefManager: SharedPrefManager
    ) = TemperatureProvider(context, sharedPrefManager)

    @Provides
    @Singleton
    fun provideStateManager(
        context: Context,
        sharedPrefManager: SharedPrefManager,
        temperatureProvider: TemperatureProvider
    ) = StateManager(context, temperatureProvider, sharedPrefManager)

    @Provides
    @Singleton
    fun cameraHelper(
        context: Context,
        nightModeConstraintSelector: NightModeConstraintSelector,
        cameraUtils: CameraUtils,
        nayanCamModuleInteractor: NayanCamModuleInteractor,
        cameraProcessor: CameraProcessor,
        fileMetaDataEditor: FileMetaDataEditor,
        sharedPrefManager: SharedPrefManager,
        stateManager: StateManager,
        iRecordingHelper: IRecordingHelper,
        iMetaDataHelper: IMetaDataHelper
    ) = CameraHelper(
        context,
        cameraUtils,
        nightModeConstraintSelector,
        nayanCamModuleInteractor,
        cameraProcessor,
        fileMetaDataEditor,
        sharedPrefManager,
        stateManager,
        iRecordingHelper,
        iMetaDataHelper
    )

    @Provides
    @Singleton
    fun providesAIModelManager(
        context: Context,
        sharedPrefManager: SharedPrefManager
    ) = AIModelManager(context, sharedPrefManager)

    @Provides
    @Singleton
    fun provideObjectOfInterest(
        aiModelManager: AIModelManager,
        sharedPrefManager: SharedPrefManager
    ) = CameraProcessor(
        aiModelManager,
        sharedPrefManager
    )

    @Provides
    @Singleton
    fun provideCameraPreviewListener(
        context: Context,
        cameraProcessor: CameraProcessor,
        sharedPrefManager: SharedPrefManager,
        nayanCamModuleInteractor: NayanCamModuleInteractor
    ) = CameraPreviewListener(
        context,
        cameraProcessor,
        sharedPrefManager,
        nayanCamModuleInteractor
    )

    @Provides
    @Singleton
    fun provideDriverErrorUtils(context: Context) = DriverErrorUtils(context)

    @Provides
    @Singleton
    fun provideGraphHopperManager(
        nayanCamRepository: INayanCamRepository,
        graphHopperManager: IGraphHopperRepository
    ) = GraphHopperSyncManager(
        nayanCamRepository,
        graphHopperManager
    )

    @Provides
    @Singleton
    fun provideAttendanceSyncManager(
        nayanCamRepository: INayanCamRepository,
        iLocationRepository: ILocationRepository,
        nayanCamModuleInteractor: NayanCamModuleInteractor
    ) = AttendanceSyncManager(
        nayanCamRepository,
        iLocationRepository,
        nayanCamModuleInteractor
    )

    @Provides
    @Singleton
    fun provideAppDatabase(context: Context): AppDatabase {
        return AppDatabase(context)
    }

    @Provides
    @Singleton
    fun provideLocationHistoryDao(appDatabase: AppDatabase): LocationDAO {
        return appDatabase.getLocationHistoryDAO()
    }

    @Provides
    @Singleton
    fun provideSegmentTrackingDao(appDatabase: AppDatabase): SegmentTrackDAO {
        return appDatabase.getSegmentTrackingDAO()
    }

    @Provides
    @Singleton
    fun provideVideoUploaderDao(appDatabase: AppDatabase): VideoUploaderDAO {
        return appDatabase.getVideoUploaderDAO()
    }

    @Provides
    @Singleton
    fun provideRecordingHelperInterface(recordingHelperImpl: RecordingHelperImpl): IRecordingHelper {
        return recordingHelperImpl
    }

    @Provides
    @Singleton
    fun provideMetaDataHelperInterface(metaDataHelperImpl: MetaDataHelperImpl): IMetaDataHelper {
        return metaDataHelperImpl
    }
}