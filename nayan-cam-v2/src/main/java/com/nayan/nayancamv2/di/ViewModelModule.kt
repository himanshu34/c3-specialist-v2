package com.nayan.nayancamv2.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nayan.nayancamv2.extcam.common.ExtCamViewModel
import com.nayan.nayancamv2.extcam.dashcam.DashCamConnectionViewModel
import com.nayan.nayancamv2.ui.cam.NayanCamViewModel
import com.nayan.nayancamv2.viewmodel.NayanCamViewModelFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(ViewModelComponent::class)
abstract class ViewModelModule {
    @Binds
    @IntoMap
    @ViewModelKey(NayanCamViewModel::class)
    abstract fun bindNayanCamViewModel(nayanCamViewModel: NayanCamViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ExtCamViewModel::class)
    abstract fun bindExtCamViewModel(extCamViewModel: ExtCamViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(DashCamConnectionViewModel::class)
    abstract fun bindDashCamConnectionViewModel(dashCamConnectionViewModel: DashCamConnectionViewModel): ViewModel

    @Binds
    abstract fun bindViewModelFactory(factory: NayanCamViewModelFactory): ViewModelProvider.Factory
}