package co.nayan.c3specialist_v2.di

import android.content.Context
import co.nayan.appsession.SessionRepositoryInterface
import co.nayan.c3specialist_v2.config.CoreConfigImpl
import co.nayan.c3specialist_v2.config.LoginConfigImpl
import co.nayan.c3specialist_v2.config.SessionRepositoryImpl
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.impl.CityKmlManagerImpl
import co.nayan.c3specialist_v2.impl.ICityKmlManager
import co.nayan.c3specialist_v2.impl.NayanCamModuleImpl
import co.nayan.c3specialist_v2.referral.GooglePlayInstallManagerImpl
import co.nayan.c3specialist_v2.referral.IGooglePlayInstallManager
import co.nayan.c3specialist_v2.repositories.*
import co.nayan.c3specialist_v2.storage.AdminSharedStorage
import co.nayan.c3specialist_v2.storage.ManagerSharedStorage
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3specialist_v2.storage.SpecialistSharedStorage
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.config.CoreConfig
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.location.LocationManagerImpl
import co.nayan.c3v2.core.models.CameraConfig
import co.nayan.c3v2.login.config.LoginConfig
import co.nayan.canvas.interfaces.CanvasRepositoryInterface
import co.nayan.canvas.interfaces.SandboxRepositoryInterface
import co.nayan.review.recordsgallery.ReviewRepositoryInterface
import com.nayan.nayancamv2.impl.ISyncWorkflowManager
import com.nayan.nayancamv2.impl.SyncWorkflowManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object C3SpecialistModule {

    @Singleton
    @Provides
    fun googlePlayInstallManager(googlePlayInstallManagerImpl: GooglePlayInstallManagerImpl): IGooglePlayInstallManager {
        return googlePlayInstallManagerImpl
    }

    @Singleton
    @Provides
    fun providesSharedStorage(@ApplicationContext context: Context) = SharedStorage(context)

    @Singleton
    @Provides
    fun providesSpecialistSharedStorage(
        @ApplicationContext context: Context
    ) = SpecialistSharedStorage(context)

    @Singleton
    @Provides
    fun providesManagerSharedStorage(
        @ApplicationContext context: Context
    ) = ManagerSharedStorage(context)

    @Singleton
    @Provides
    fun providesAdminSharedStorage(
        @ApplicationContext context: Context
    ) = AdminSharedStorage(context)

    @Singleton
    @Provides
    fun providesLoginConfigImpl(loginConfigImpl: LoginConfigImpl): LoginConfig {
        return loginConfigImpl
    }

    @Singleton
    @Provides
    fun providesCoreConfigImpl(coreConfigImpl: CoreConfigImpl): CoreConfig {
        return coreConfigImpl
    }

    @Provides
    @Singleton
    fun providesCityKmlManagerImpl(cityKmlManagerImpl: CityKmlManagerImpl): ICityKmlManager {
        return cityKmlManagerImpl
    }

    @Provides
    @Singleton
    fun providesSyncWorkflowManagerImpl(syncWorkflowManagerImpl: SyncWorkflowManagerImpl): ISyncWorkflowManager {
        return syncWorkflowManagerImpl
    }

    @Provides
    fun providesCanvasRepositoryImpl(
        sharedStorage: SharedStorage,
        apiClientFactory: ApiClientFactory,
        specialistSharedStorage: SpecialistSharedStorage,
        managerSharedStorage: ManagerSharedStorage,
        adminSharedStorage: AdminSharedStorage
    ): CanvasRepositoryInterface {
        return when (sharedStorage.getRoleForCanvas()) {
            Role.MANAGER -> ManagerCanvasRepository(managerSharedStorage, apiClientFactory)
            Role.ADMIN -> AdminCanvasRepository(adminSharedStorage, apiClientFactory)
            else -> SpecialistCanvasRepository(specialistSharedStorage, apiClientFactory)
        }
    }

    @Singleton
    @Provides
    fun providesSpecialistSandboxRepositoryImpl(
        sharedStorage: SpecialistSharedStorage,
        apiClientFactory: ApiClientFactory
    ): SandboxRepositoryInterface {
        return SpecialistSandboxRepository(sharedStorage, apiClientFactory)
    }

    @Singleton
    @Provides
    fun providesReviewRepositoryImpl(
        sharedStorage: ManagerSharedStorage,
        apiClientFactory: ApiClientFactory
    ): ReviewRepositoryInterface {
        return ReviewRepositoryImpl(sharedStorage, apiClientFactory)
    }

    @Singleton
    @Provides
    fun providesSessionRepository(
        apiClientFactory: ApiClientFactory,
        userRepository: UserRepository
    ): SessionRepositoryInterface {
        return SessionRepositoryImpl(apiClientFactory, userRepository)
    }

    @Singleton
    @Provides
    fun providesNayanCamModuleInteractor(
        sharedStorage: SharedStorage,
        sessionRepositoryInterface: SessionRepositoryInterface,
        userRepository: UserRepository,
        locationManagerImpl: LocationManagerImpl,
        apiClientFactory: ApiClientFactory,
        deviceInfoHelperImpl: DeviceInfoHelperImpl
    ): NayanCamModuleInteractor {
        return NayanCamModuleImpl(
            sharedStorage,
            sessionRepositoryInterface,
            userRepository,
            locationManagerImpl,
            apiClientFactory,
            deviceInfoHelperImpl
        )
    }

    @Provides
    @Singleton
    fun providesCameraConfig(): CameraConfig {
        return CameraConfig.Builder()
            .showBoundingBox(true)
            .showOverlay(true)
            .setNightModeEnabled(false)
            .setSwitchCameraEnabled(false)
            .isDubaiPoliceEnabled(false)
            .shouldShowSettingsOnPreview(false)
            .build()
    }
}