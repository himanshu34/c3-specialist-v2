package co.nayan.c3v2.core.interceptors

import android.content.Context
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.ServerConnectionException
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketTimeOutInterceptor @Inject constructor(
    @ApplicationContext val context: Context
) : Interceptor {

    @Throws(ServerConnectionException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(chain.request().newBuilder().build())
        } catch (ex: SocketTimeoutException) {
            throw ServerConnectionException(context.getString(R.string.socket_timeout_connection))
        }
    }
}