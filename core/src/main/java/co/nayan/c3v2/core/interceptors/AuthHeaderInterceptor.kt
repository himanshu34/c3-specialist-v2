package co.nayan.c3v2.core.interceptors

import android.content.Context
import android.content.Intent
import co.nayan.c3v2.core.BuildConfig
import co.nayan.c3v2.core.DuplicateException
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.ServerStorageFullException
import co.nayan.c3v2.core.api.client_error.ClientErrorManagerImpl
import co.nayan.c3v2.core.di.preference.PreferenceHelper
import co.nayan.c3v2.core.models.HeaderAuthProvider
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.BAD_GATEWAY
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.CLIENT_ERROR
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.DUPLICATE
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.NOT_FOUND
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.OKAY
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.SERVER_ERROR
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.SERVER_STORAGE_FULL
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.SERVICE_UNAVAILABLE
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.UNAUTHORIZED
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.UNKNOWN_HOST
import co.nayan.c3v2.core.utils.Constants.Error.DATABASE_ERROR_TAG
import co.nayan.c3v2.core.utils.Constants.Error.INTERNAL_ERROR_TAG
import co.nayan.c3v2.core.utils.Constants.Error.NOT_FOUND_TAG
import co.nayan.c3v2.core.utils.Constants.Error.ON_SUCCESS
import co.nayan.c3v2.core.utils.Constants.Error.SERVER_STORAGE_FULL_TAG
import co.nayan.c3v2.core.utils.Constants.Error.UNAUTHORIZED_TAG
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthHeaderInterceptor @Inject constructor(
    @ApplicationContext val context: Context,
    private val clientErrorManagerImpl: ClientErrorManagerImpl,
    private val mPreferenceHelper: PreferenceHelper
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            val response = chain.proceed(chain.request())
            when (response.code) {
                OKAY -> {
                    if (mPreferenceHelper.getAuthenticationHeaders() == null)
                        saveAuthHeaders(response.headers)
                    clientErrorManagerImpl.success(Intent(ON_SUCCESS))
                }

                in CLIENT_ERROR -> {
                    setupClientError(response)
                }

                in SERVER_ERROR -> {
                    if (response.code == SERVER_STORAGE_FULL) {
                        logException(response.storageFullException())
                        clientErrorManagerImpl.catchError(Intent(SERVER_STORAGE_FULL_TAG))
                    } else {
                        logException(response.ioException())
                        clientErrorManagerImpl.catchError(Intent(INTERNAL_ERROR_TAG))
                    }
                }

                else -> {}
            }

            response
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            val errorMessage = when (e) {
                is UnknownHostException, is ConnectException -> context.getString(R.string.no_internet_message)
                is SocketTimeoutException -> context.getString(R.string.socket_timeout_connection)
                else -> "${e.message}"
            }

            val responseBody =
                "{\"status_code\":$UNKNOWN_HOST,\"message\":\"$errorMessage\"}".toResponseBody()

            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(UNKNOWN_HOST)
                .message(errorMessage)
                .body(responseBody).build()
        }
    }

    private fun saveAuthHeaders(responseHeaders: Headers) {
        val headerAuthProvider = responseHeaders.let {
            if (responseHeaders["access-token"] != null
                && responseHeaders["client"] != null
                && responseHeaders["expiry"] != null
                && responseHeaders["uid"] != null
            ) {

                HeaderAuthProvider(
                    access_token = responseHeaders["access-token"].toString(),
                    client = responseHeaders["client"].toString(),
                    expiry = responseHeaders["expiry"].toString(),
                    uid = responseHeaders["uid"].toString()
                )
            } else null
        }

        if (headerAuthProvider != null)
            mPreferenceHelper.saveAuthenticationHeaders(headerAuthProvider)
    }

    private fun logException(ex: Exception?) {
        if (BuildConfig.DEBUG || ex == null) return
        Firebase.crashlytics.recordException(ex)
    }

    private fun setupClientError(response: Response) {
        when (response.code) {
            DUPLICATE -> {
                logException(response.duplicateException())
                clientErrorManagerImpl.catchError(Intent(DATABASE_ERROR_TAG))
            }

            NOT_FOUND -> {
                clientErrorManagerImpl.catchError(Intent(NOT_FOUND_TAG))
            }

            UNAUTHORIZED -> {
                clientErrorManagerImpl.catchError(Intent(UNAUTHORIZED_TAG))
            }
        }
    }

    private fun Response.duplicateException(): DuplicateException {
        return DuplicateException("Error Code: $code ($message)")
    }

    private fun Response.storageFullException(): ServerStorageFullException {
        return ServerStorageFullException("Error Code: $code ($message)")
    }

    private fun Response.ioException(): IOException? {
        return if (listOf(BAD_GATEWAY, SERVICE_UNAVAILABLE).contains(code)) null
        else IOException("Error Code: $code ($message)\nRequest Body: $request\nError Message: ${body.source().buffer}")
    }
}