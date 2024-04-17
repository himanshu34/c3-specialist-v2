package co.nayan.c3v2.core.api.client_error

import android.content.Context
import co.nayan.c3v2.core.ApiException
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.ServerConnectionException
import co.nayan.c3v2.core.UnAuthorizedException
import co.nayan.c3v2.core.models.c3_module.WalletErrorModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    fun parseExceptionMessage(exception: Exception): String {
        return when (exception) {
            is UnknownHostException, is ConnectException -> context.getString(R.string.no_internet_message)
            is SocketTimeoutException -> context.getString(R.string.socket_timeout_connection)
            is HttpException -> getMessage(exception)
            is UnAuthorizedException, is ApiException, is ServerConnectionException, is IllegalArgumentException -> exception.message
                ?: context.getString(R.string.something_went_wrong)
            else -> context.getString(R.string.something_went_wrong)
        }
    }

    private fun getMessage(exception: HttpException): String {
        return try {
            val errorDetail = exception.response()?.errorBody()?.let {
                gson.fromJson(it.string(), ErrorModel::class.java)
            }

            errorDetail?.message
                ?: errorDetail?.errors?.joinToString(",")
                ?: context.getString(R.string.something_went_wrong)
        } catch (e: JsonSyntaxException) {
            context.getString(R.string.something_went_wrong)
        }
    }

    fun getWalletHttpError(exception: HttpException): WalletErrorModel? {
        return try {
            exception.response()?.errorBody()?.let {
                gson.fromJson(it.string(), WalletErrorModel::class.java)
            }
        } catch (e: JsonSyntaxException) {
            null
        }
    }
}

data class ErrorModel(
    val success: Boolean?,
    val message: String?,
    val errors: List<String>?
)