package co.nayan.c3v2.core

import android.os.Handler
import android.os.Looper
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.FileOutputStream
import kotlin.math.roundToInt

fun postDelayed(delayInMillis: Long, block: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed(Runnable(block), delayInMillis)
}

fun loggingIntercept(block: HttpLoggingInterceptor.() -> Unit): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply { block(this) }
}

inline fun debugMode(block: () -> Unit) {
    if (BuildConfig.DEBUG) {
        block()
    }
}

fun okHttp(builder: OkHttpClient.Builder.() -> Unit) =
    OkHttpClient.Builder().apply {
        builder(this)
    }.build()


fun retrofit(builder: Retrofit.Builder.() -> Unit): Retrofit {
    return Retrofit.Builder().apply {
        builder(this)
    }.build()
}

suspend fun downloadFile(
    url: String,
    outputStream: FileOutputStream,
    downloadProgressFun: ((bytesRead: Long, contentLength: Long, progress: Int) -> Unit)? = null
): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    try {
        val request = with(Request.Builder()) { url(url) }.build()
        val client = OkHttpClient.Builder().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody: ResponseBody = response.body

            val totalBytes = responseBody.contentLength()
            var bytesRead: Long = 0
            val bufferSize = 4096
            val buffer = ByteArray(bufferSize)

            var len: Int
            while (responseBody.byteStream().read(buffer).also { len = it } != -1) {
                outputStream.write(buffer, 0, len)
                bytesRead += len
                val progress = ((bytesRead.toFloat() / totalBytes) * 100).roundToInt()
                downloadProgressFun?.invoke(bytesRead, totalBytes, progress)
            }

            outputStream.close()

            Pair(true, "${response.code} File downloaded successfully")
        } else Pair(true, "${response.code} File Download failed")
    } catch (e: Exception) {
        Firebase.crashlytics.recordException(e)
        Pair(false, e.message.toString())
    }
}