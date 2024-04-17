package com.nayan.nayancamv2.util

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T> : Resource<T>()
}

inline fun <T : Any> Resource<T>.success(action: (T?) -> Unit): Resource<T> {
    if (this is Resource.Success) action(data)
    return this
}

inline fun <T : Any> Resource<T>.fail(action: (error: String?, errorData: T?) -> Unit): Resource<T> {
    if (this is Resource.Error) action(message, data)
    return this
}

inline fun <T : Any> Resource<T>.loading(action: () -> Unit): Resource<T> {
    if (this is Resource.Loading) action()
    return this
}

const val API_RESULT_SUCCESS = 200
val API_RESULT_FAILURE = arrayOf(401, 500, 403, 203, 503)