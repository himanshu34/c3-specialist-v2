package co.nayan.c3specialist_v2.screen_sharing.models

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.JsonElement
import org.json.JSONObject

data class RoomChannelResponse(
    val from: String,
    val type: String,
    val payload: String
) {
    companion object {
        fun create(data: JsonElement?) =
            try {
                Gson().fromJson(data, RoomChannelResponse::class.java)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                null
            }
    }
}