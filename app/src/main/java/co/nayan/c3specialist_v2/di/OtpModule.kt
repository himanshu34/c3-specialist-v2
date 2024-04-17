package co.nayan.c3specialist_v2.di

import co.nayan.c3specialist_v2.phoneverification.otp.IOtpManager
import co.nayan.c3specialist_v2.phoneverification.otp.OtpManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
abstract class OtpModule {

    @Binds
    abstract fun bindOtpManagerImpl(otpManagerImpl: OtpManagerImpl): IOtpManager
}
