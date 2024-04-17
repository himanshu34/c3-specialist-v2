package co.nayan.c3v2.core.di.qualifier

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OkHttpClientBase

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OkHttpClientGraphHopper