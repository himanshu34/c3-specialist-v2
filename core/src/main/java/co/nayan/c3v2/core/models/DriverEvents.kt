package co.nayan.c3v2.core.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class Events(
    @SerializedName("workflow_id")
    val workflowId: Int,
    @SerializedName("event_type_id")
    val eventTypeId: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("event_name")
    val eventName: String?,
    @SerializedName("workflow_name")
    val workflowName: String?,
    @SerializedName("score")
    val score: String?
)
