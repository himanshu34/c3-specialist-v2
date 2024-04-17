package co.nayan.c3v2.core.di

import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import co.nayan.c3v2.core.models.CameraConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * This class acts as bridge between app module and driver sdk
 * see more "https://developer.android.com/training/dependency-injection/hilt-multi-module"
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NayanCamModuleDependencies {
    fun nayanCamModuleInteractor(): NayanCamModuleInteractor
    fun cameraConfig(): CameraConfig
}