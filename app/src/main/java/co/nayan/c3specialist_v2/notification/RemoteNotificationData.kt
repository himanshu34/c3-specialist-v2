package co.nayan.c3specialist_v2.notification

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException

data class RemoteNotificationData(
    val body: String?,
    val title: String?,
    val userId: Int?
) {
    companion object {
        fun createData(data: String?): RemoteNotificationData? {
            return try {
                val gson = GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create()
                gson.fromJson(data, RemoteNotificationData::class.java)
            } catch (e: JsonSyntaxException) {
                Firebase.crashlytics.recordException(e)
                null
            }
        }
    }
}