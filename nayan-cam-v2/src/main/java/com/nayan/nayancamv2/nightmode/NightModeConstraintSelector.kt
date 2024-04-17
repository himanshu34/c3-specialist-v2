package com.nayan.nayancamv2.nightmode

import co.nayan.c3v2.core.fromPrettyJson
import co.nayan.c3v2.core.toPrettyJson
import com.nayan.nayancamv2.storage.SharedPrefManager

/**
 * Utility class to handle night mode related info
 *
 * @property preferenceProvider
 */
class NightModeConstraintSelector(
    private val preferenceProvider: SharedPrefManager
) {
    private val defaultStartTime = NightModeConstraint(6, 0, "PM", START_TIME)
    private val defaultEndTime = NightModeConstraint(6, 0, "AM", END_TIME)
    private val nightModeSession: NightModeSession

    init {
        val session = preferenceProvider.get(NIGHT_MODE_SESSION, "")
        nightModeSession = if (session.isEmpty())
            NightModeSession(defaultStartTime, defaultEndTime, true)
        else session.fromPrettyJson()
    }

    private fun saveNightModeSession() {
        preferenceProvider.getEditor()
            .putString(NIGHT_MODE_SESSION, nightModeSession.toPrettyJson()).apply()
    }

    fun updateNightModeConstraints(
        nightModeConstraint: NightModeConstraint,
        onTimeSelectionListener: OnTimeSelectionListener
    ) {
        when (nightModeConstraint.constraintType) {
            START_TIME -> {
                nightModeSession.startTime = nightModeConstraint
                saveNightModeSession()
                onTimeSelectionListener.success()
            }

            END_TIME -> {
                if (nightModeSession.startTime.getRawTime() == nightModeConstraint.getRawTime()
                    && nightModeSession.startTime.timeSet == nightModeConstraint.timeSet
                ) {
                    onTimeSelectionListener.failure(
                        "End Time should not be same as then start time."
                    )
                } else {
                    nightModeSession.endTime = nightModeConstraint
                    saveNightModeSession()
                    onTimeSelectionListener.success()
                }
            }
        }
    }

    fun getSession() = nightModeSession
    fun getStartTime() = nightModeSession.startTime.getTimeFormat()
    fun getEndTime() = nightModeSession.endTime.getTimeFormat()
    fun isFeatureEnabled() = nightModeSession.featureEnabled

    fun toggleFeatureStatus(status: Boolean) {
        nightModeSession.featureEnabled = status
        saveNightModeSession()
    }

    companion object {
        const val NIGHT_MODE_SESSION = "NightModeSession"
        const val START_TIME = "start_time"
        const val END_TIME = "end_time"
    }
}

interface OnTimeSelectionListener {
    fun success()
    fun failure(errorMessage: String)
}

data class NightModeSession(
    var startTime: NightModeConstraint,
    var endTime: NightModeConstraint,
    var featureEnabled: Boolean
)

data class NightModeConstraint(
    val hour: Int,
    val minute: Int,
    val timeSet: String,
    val constraintType: String
) {
    fun getRawTime() = "$hour${formatMinute()}".toInt()

    private fun formatMinute(): String {
        return if (minute < 10) "0$minute"
        else minute.toString()
    }

    fun getTimeFormat(): String {
        return if (minute < 10) "$hour:0$minute $timeSet"
        else "$hour:$minute $timeSet"
    }
}