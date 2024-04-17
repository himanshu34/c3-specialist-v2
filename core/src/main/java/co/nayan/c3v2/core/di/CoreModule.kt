package co.nayan.c3v2.core.di

import co.nayan.c3v2.core.api.client_error.ClientErrorManagerImpl
import co.nayan.c3v2.core.api.client_error.IClientErrorHelper
import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.api.factory.IApiFactory
import co.nayan.c3v2.core.config.CoreConfig
import co.nayan.c3v2.core.debugMode
import co.nayan.c3v2.core.device_info.DeviceInfoHelperImpl
import co.nayan.c3v2.core.device_info.IDeviceInfoHelper
import co.nayan.c3v2.core.di.preference.PreferenceHelper
import co.nayan.c3v2.core.di.qualifier.InterceptorBase
import co.nayan.c3v2.core.di.qualifier.OkHttpClientBase
import co.nayan.c3v2.core.di.qualifier.OkHttpClientGraphHopper
import co.nayan.c3v2.core.di.qualifier.RetrofitBase
import co.nayan.c3v2.core.di.qualifier.RetrofitGraphHopper
import co.nayan.c3v2.core.interceptors.AuthHeaderInterceptor
import co.nayan.c3v2.core.interceptors.NetworkConnectionInterceptor
import co.nayan.c3v2.core.interceptors.SocketTimeOutInterceptor
import co.nayan.c3v2.core.location.ILocationManager
import co.nayan.c3v2.core.location.LocationManagerImpl
import co.nayan.c3v2.core.loggingIntercept
import co.nayan.c3v2.core.okHttp
import co.nayan.c3v2.core.retrofit
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.core.Persister
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @InterceptorBase
    @Provides
    @Singleton
    fun provideHeaderAuthorizationInterceptor(mPreferenceHelper: PreferenceHelper): Interceptor {
        return Interceptor { chain ->
            var request: Request = chain.request()
            val headers: Headers = request.headers.newBuilder().apply {
                mPreferenceHelper.getAuthenticationHeaders()?.let {
                    add("access-token", it.access_token)
                    add("client", it.client)
                    add("expiry", it.expiry)
                    add("uid", it.uid)
                }
                add("Content-Type", "application/json")
            }.build()
            request = request.newBuilder().headers(headers).build()
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun providesHttpLoggingInterceptor() = loggingIntercept {
        debugMode {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    @OkHttpClientBase
    @Provides
    @Singleton
    fun provideHttpClient(
        networkConnectionInterceptor: NetworkConnectionInterceptor,
        httpLoggingInterceptor: HttpLoggingInterceptor,
        @InterceptorBase headerAuthorizationInterceptor: Interceptor,
        authHeaderInterceptor: AuthHeaderInterceptor,
        socketTimeOutInterceptor: SocketTimeOutInterceptor
    ) = okHttp {
        val protocols = ArrayList<Protocol>()
        protocols.add(Protocol.HTTP_1_1) // <-- The only protocol used
        //add(Protocol.HTTP_2);

        readTimeout(60, TimeUnit.SECONDS)
        connectTimeout(60, TimeUnit.SECONDS)
        writeTimeout(3, TimeUnit.MINUTES)
        protocols(protocols)
        addInterceptor(networkConnectionInterceptor)
        addInterceptor(httpLoggingInterceptor)
        addInterceptor(socketTimeOutInterceptor)
        addInterceptor(headerAuthorizationInterceptor)
        addInterceptor(authHeaderInterceptor)
    }

    @RetrofitBase
    @Provides
    @Singleton
    fun provideRetrofitClient(
        gson: Gson,
        @OkHttpClientBase okHttpClient: OkHttpClient,
        coreConfig: CoreConfig
    ) = retrofit {
        baseUrl(coreConfig.apiBaseUrl())
        client(okHttpClient)
        addConverterFactory(GsonConverterFactory.create(gson))
    }

    @OkHttpClientGraphHopper
    @Provides
    @Singleton
    fun provideGraphHopperOkHttpClient(
        networkConnectionInterceptor: NetworkConnectionInterceptor,
        httpLoggingInterceptor: HttpLoggingInterceptor,
        socketTimeOutInterceptor: SocketTimeOutInterceptor
    ) = okHttp {
        connectTimeout(100, TimeUnit.SECONDS)
        readTimeout(400, TimeUnit.SECONDS)
        writeTimeout(2, TimeUnit.MINUTES)
        addInterceptor(networkConnectionInterceptor)
        addInterceptor(httpLoggingInterceptor)
        addInterceptor(socketTimeOutInterceptor)
    }

    @Provides
    @Singleton
    fun provideIApiClientFactory(factory: ApiClientFactory): IApiFactory {
        return factory
    }

    @RetrofitGraphHopper
    @Provides
    @Singleton
    fun provideRetrofitClientForGraphHopper(
        @OkHttpClientGraphHopper okHttpClient: OkHttpClient,
        coreConfig: CoreConfig
    ) = retrofit {
        baseUrl(coreConfig.apiGraphhopperBaseUrl())
        client(okHttpClient)
        addConverterFactory(SimpleXmlConverterFactory.createNonStrict(Persister(AnnotationStrategy())))
    }

    @Provides
    @Singleton
    fun providesClientErrorManagerImpl(clientErrorManagerImpl: ClientErrorManagerImpl): IClientErrorHelper {
        return clientErrorManagerImpl
    }

    @Provides
    @Singleton
    fun providesGson(): Gson {
        return GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }

    @Provides
    @Singleton
    fun providesDeviceInfoHelper(deviceInfoHelperImpl: DeviceInfoHelperImpl): IDeviceInfoHelper {
        return deviceInfoHelperImpl
    }

    @Provides
    @Singleton
    fun providesLocationManagerImpl(locationManagerImpl: LocationManagerImpl): ILocationManager {
        return locationManagerImpl
    }
}