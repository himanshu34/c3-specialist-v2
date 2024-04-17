package co.nayan.appsession

import com.google.gson.JsonObject
import kotlin.math.abs

data class Session(
    val startTime: Long,
    var endTime: Long,
    val activity: String? = null,
    val wfStepId: Int? = null,
    val workAssignmentId: Int? = null,
    val workType: String? = null,
    val phoneNumber: String? = null,
    val role: String? = null
) {
    fun toValueArray(): List<Any> {
        val valueList = mutableListOf<Any>()
        val metadata = JsonObject()
        metadata.addProperty("activity", activity)
        metadata.addProperty("phone_number", phoneNumber)
        if (wfStepId != null) metadata.addProperty("wf_step_id", wfStepId)
        if (!workType.isNullOrEmpty()) metadata.addProperty("work_type", workType)
        if (workAssignmentId != null) metadata.addProperty("work_assignment_id", workAssignmentId)
        if (!role.isNullOrEmpty()) metadata.addProperty("role", role)
        valueList.add(startTime)
        valueList.add(endTime)
        valueList.add(metadata)
        return valueList
    }

    fun isActive(): Boolean {
        return System.currentTimeMillis() - endTime < HEARTBEAT_DURATION + HEARTBEAT_BUFFER
    }

    fun update(): Session {
        val currentTime = System.currentTimeMillis()
        val diff = if (startTime < currentTime)
            startTime - currentTime
        else currentTime - startTime
        endTime = startTime + abs(diff)
        return Session(
            startTime = startTime,
            endTime = endTime,
            activity = activity,
            wfStepId = wfStepId,
            workAssignmentId = workAssignmentId,
            workType = workType,
            phoneNumber = phoneNumber,
            role = role
        )
    }

    fun isNewer(): Boolean {
        return System.currentTimeMillis() - endTime < SESSION_GAP
    }

    fun isForSameActivity(toCheck: String?) = activity == toCheck

    companion object {
        const val HEARTBEAT_DURATION = 60_000L
        private const val HEARTBEAT_BUFFER = 2_000L
        private const val SESSION_GAP = 5 * 60_000L

        fun newSession(
            activityName: String?,
            wfStepId: Int?,
            workAssignmentId: Int?,
            workType: String?,
            phoneNumber: String?,
            role: String?
        ) = Session(
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis(),
            activity = activityName,
            wfStepId = wfStepId,
            workAssignmentId = workAssignmentId,
            workType = workType,
            phoneNumber = phoneNumber,
            role = role
        )
    }
}
