package co.nayan.c3v2.core.api.factory

import co.nayan.c3v2.core.api.ApiClientBase
import co.nayan.c3v2.core.api.ApiClientGraphHopper
import co.nayan.c3v2.core.api.ApiClientLogin
import co.nayan.c3v2.core.api.ApiClientNayanCam
import co.nayan.c3v2.core.di.qualifier.RetrofitBase
import co.nayan.c3v2.core.di.qualifier.RetrofitGraphHopper
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClientFactory @Inject constructor(
    @RetrofitBase private val mRetrofitClient: Retrofit,
    @RetrofitGraphHopper private val mRetrofitClientGraphHopper: Retrofit
) : IApiFactory {
    override val apiClientLogin: ApiClientLogin
        get() = mRetrofitClient.create(ApiClientLogin::class.java)
    override val apiClientBase: ApiClientBase
        get() = mRetrofitClient.create(ApiClientBase::class.java)
    override val apiClientNayanCam: ApiClientNayanCam
        get() = mRetrofitClient.create(ApiClientNayanCam::class.java)
    override val apiClientGraphHopper: ApiClientGraphHopper
        get() = mRetrofitClientGraphHopper.create(ApiClientGraphHopper::class.java)
}