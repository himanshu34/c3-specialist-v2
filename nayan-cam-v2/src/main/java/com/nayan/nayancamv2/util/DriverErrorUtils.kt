package com.nayan.nayancamv2.util

import android.content.Context
import co.nayan.c3v2.core.ApiException
import co.nayan.c3v2.core.DuplicateException
import co.nayan.c3v2.core.NotFoundException
import co.nayan.c3v2.core.R
import co.nayan.c3v2.core.ServerConnectionException
import co.nayan.c3v2.core.ServerStorageFullException
import co.nayan.c3v2.core.UnAuthorizedException
import co.nayan.c3v2.core.fromPrettyJson
import co.nayan.c3v2.core.models.ErrorModel
import co.nayan.c3v2.core.models.driver_module.VideoUploaderData
import com.google.gson.JsonSyntaxException
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverErrorUtils @Inject constructor(private val context: Context) {

    private lateinit var serverErrorCallback: ServerErrorCallback

    fun setCallback(serverErrorCallback: ServerErrorCallback) {
        this.serverErrorCallback = serverErrorCallback
    }

    fun parseExceptionMessage(
        throwable: Throwable,
        videoUploaderData: VideoUploaderData? = null
    ): String {
        return when (throwable) {
            is UnknownHostException, is ConnectException -> context.getString(R.string.no_internet_message)
            is SocketTimeoutException -> context.getString(R.string.socket_timeout_connection)
            is HttpException -> getMessage(throwable)
            is UnAuthorizedException -> {
                serverErrorCallback.unAuthorisedUserError()
                context.getString(co.nayan.nayancamv2.R.string.unauthorized_error_message)
            }

            is DuplicateException -> {
                serverErrorCallback.duplicateVideoFileError(videoUploaderData)
                context.getString(co.nayan.nayancamv2.R.string.duplicate_file_error)
            }

            is ServerStorageFullException -> context.getString(co.nayan.nayancamv2.R.string.server_storage_full)
            is ApiException, is ServerConnectionException, is IllegalArgumentException -> throwable.message
                ?: context.getString(R.string.something_went_wrong)

            is NotFoundException -> context.getString(co.nayan.nayancamv2.R.string.page_not_found)
            else -> context.getString(co.nayan.nayancamv2.R.string.upload_failed_message)
        }
    }

    private fun getMessage(exception: HttpException): String {
        return try {
            val errorDetail =
                exception.response()?.errorBody()?.string()?.fromPrettyJson<ErrorModel>()
            errorDetail?.message
                ?: errorDetail?.errors
                ?: context.getString(co.nayan.nayancamv2.R.string.upload_failed_message)
        } catch (e: JsonSyntaxException) {
            context.getString(co.nayan.c3v2.core.R.string.something_went_wrong)
        }
    }
}

interface ServerErrorCallback {
    fun unAuthorisedUserError()
    fun duplicateVideoFileError(videoUploaderData: VideoUploaderData?)
}