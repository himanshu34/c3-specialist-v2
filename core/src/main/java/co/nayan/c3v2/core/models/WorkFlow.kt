package co.nayan.c3v2.core.models

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class WorkFlow(
    val id: Int,
    val name: String?,
    val priority: Int?,
    val restricted: Boolean?,
    val enabled: Boolean?,
    val wfSteps: List<WfStep>?
) : Parcelable