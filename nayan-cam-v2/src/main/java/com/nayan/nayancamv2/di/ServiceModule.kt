package com.nayan.nayancamv2.di

import com.nayan.nayancamv2.repository.repository_notification.INotificationHelper
import com.nayan.nayancamv2.repository.repository_notification.NotificationHelperImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @Provides
    fun provideINotificationImpl(): INotificationHelper {
        return NotificationHelperImpl
    }
}