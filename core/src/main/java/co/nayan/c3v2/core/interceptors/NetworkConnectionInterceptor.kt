package co.nayan.c3v2.core.interceptors

import android.content.Context
import co.nayan.c3v2.core.NoNetworkException
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.hasNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkConnectionInterceptor @Inject constructor(
    @ApplicationContext val context: Context
) : Interceptor {

    @Throws(NoNetworkException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!context.hasNetwork()) throw NoNetworkException(context.getString(R.string.no_internet_message))
        return chain.proceed(chain.request())
    }
}