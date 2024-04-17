package co.nayan.c3v2.core.api

import co.nayan.c3v2.core.ApiException
import co.nayan.c3v2.core.AttendanceLockedException
import co.nayan.c3v2.core.DuplicateException
import co.nayan.c3v2.core.NotFoundException
import co.nayan.c3v2.core.ServerConnectionException
import co.nayan.c3v2.core.ServerStorageFullException
import co.nayan.c3v2.core.UnAuthorizedException
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.ATTENDANCE_LOCKED
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.BAD_GATEWAY
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.CLIENT_ERROR
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.DUPLICATE
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.NOT_FOUND
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.SERVER_ERROR
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.SERVER_STORAGE_FULL
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.SERVICE_UNAVAILABLE
import co.nayan.c3v2.core.utils.Constants.ApiResponseCode.UNAUTHORIZED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import retrofit2.Response

abstract class SafeApiRequest {
    /**
     * will return the api response wrapped with in coroutine Flow
     * Resource wrapping will now be done in view model.
     * This is the most recommended way of handling the response and catching the exceptions while using flow.
     *
     * ApiException to be collected in view model
     */
    suspend fun <T : Any> makeSafeRequestForFlow(request: suspend () -> Response<T>): Flow<T> =
        flow {
            val response = request.invoke()
            if (response.isSuccessful)
                emit(response.body()!!)
            else {
                val jsonObject = response.errorBody()?.string()?.let { JSONObject(it) }
                val errorMsg = jsonObject?.optString("message")
                val errorMessage = jsonObject?.optString("result")
                val errorCode = response.code()
                val error = jsonObject?.optJSONArray("errors")
                var responseMessage =
                    error?.let { "$errorMessage ${it.get(0)}".trim() } ?: run { errorMessage }
                if (responseMessage.isNullOrEmpty()) responseMessage = errorMsg

                val exceptionMessage =
                    responseMessage ?: "${response.message()} with error code $errorCode"
                throw when (errorCode) {
                    in CLIENT_ERROR -> setUpClientError(errorCode, exceptionMessage)
                    in SERVER_ERROR -> setUpServerError(errorCode, exceptionMessage)
                    else -> ApiException(exceptionMessage)
                }
            }
        }

    private fun setUpServerError(errorCode: Int, exceptionMessage: String): Exception {
        return when (errorCode) {
            SERVER_STORAGE_FULL -> ServerStorageFullException(exceptionMessage)
            BAD_GATEWAY -> ServerConnectionException(exceptionMessage)
            SERVICE_UNAVAILABLE -> ServerConnectionException(exceptionMessage)
            else -> ServerConnectionException(exceptionMessage)
        }
    }

    private fun setUpClientError(errorCode: Int, exceptionMessage: String): Exception {
        return when (errorCode) {
            UNAUTHORIZED -> UnAuthorizedException(exceptionMessage)
            NOT_FOUND -> NotFoundException(exceptionMessage)
            DUPLICATE -> DuplicateException(exceptionMessage)
            ATTENDANCE_LOCKED -> AttendanceLockedException(exceptionMessage)
            else -> ApiException(exceptionMessage)
        }
    }

    suspend fun <T : Any> makeSafeRequestForFlowToGraphHopper(request: suspend () -> Response<T>): Flow<T> =
        flow {
            val response = request.invoke()
            if (response.isSuccessful)
                emit(response.body()!!)
            else {
                val errorCode = response.code()
                throw ApiException(message = "${response.message()} with error code $errorCode")
            }
        }
}