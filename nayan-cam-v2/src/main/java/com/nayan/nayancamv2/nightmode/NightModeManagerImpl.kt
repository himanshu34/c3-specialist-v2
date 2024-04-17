package com.nayan.nayancamv2.nightmode

import android.os.Handler
import com.nayan.nayancamv2.helper.GlobalParams.isNightModeActive
import timber.log.Timber
import java.util.Calendar

class NightModeManagerImpl(
    private val exposureCompensation: Pair<Int, Int>,
    private var nightModeHandler: Handler?,
    private val nightModeSession: NightModeSession,
    private val updateExposure: ((Int?) -> Unit?)? = null
) : INightModeManager {

    private val exposureValues = mutableListOf<Int>()
    private var index: Int = 0
    private var previousExposure: Int = 0
    private var exposureDelay: Float = 0.1F
    private var timeChange = 1.5F / exposureDelay
    private var tickSize: Int = 1
    private var shouldUpdateExposure: Boolean = true
    private val exposureBuffer = ArrayList<Int>()
    private var currentCameraISOExposure: Int? = null
    private var fixedCameraISOExposure: Int? = null

    init {
        for (i in exposureCompensation.first..exposureCompensation.second)
            exposureValues.add(i)
        tickSize = (exposureValues.size / timeChange).toInt()
        Timber.d("Tick Size : $tickSize")
        index = exposureValues.size / 2
        previousExposure = exposureValues[index]
    }

    override suspend fun reset() {
        shouldUpdateExposure = true
        exposureBuffer.clear()
        currentCameraISOExposure = null
        fixedCameraISOExposure = null
    }

    override suspend fun updateISOExposureBuffer() {
        if (shouldUpdateExposure && currentCameraISOExposure != null) {
            exposureBuffer.add(currentCameraISOExposure!!)
            if (exposureBuffer.size >= 3) {
                var averageExposure = 0
                exposureBuffer.map { averageExposure += it }
                averageExposure /= exposureBuffer.size
                shouldUpdateExposure = false
                fixedCameraISOExposure = averageExposure
            }
        }
    }

    override fun removeNightMode() {
        nightModeHandler?.removeCallbacks(nightModeChecker)
        nightModeHandler?.removeCallbacksAndMessages(null)
        nightModeHandler = null
    }

    override fun checkForNightMode() {
        if (nightModeSession.featureEnabled) nightModeHandler?.post(nightModeChecker)
    }

    override fun getNextExposureValue(): Int {
        rectifyIndex()
        val nextExposureValue = exposureValues[index]
        when (nextExposureValue) {
            exposureCompensation.second -> index -= tickSize
            exposureCompensation.first -> index += tickSize
            previousExposure -> index += tickSize
            else -> {
                if (previousExposure < nextExposureValue) index += tickSize
                else index -= tickSize
            }
        }
        previousExposure = nextExposureValue
        currentCameraISOExposure = nextExposureValue
        Timber.tag("NightModeManager").e("Exposure : $nextExposureValue")
        return nextExposureValue
    }

    private fun rectifyIndex() {
        if (index > (exposureValues.size - 1))
            index = exposureValues.size - 1
        else if (index < 0) index = 0
    }

    override fun enableNightMode(): Boolean {
        val now = Calendar.getInstance()
        val startTime = Calendar.getInstance()
        val endTime = Calendar.getInstance()

        if (nightModeSession.startTime.timeSet == "PM" && nightModeSession.startTime.hour != 12)
            startTime.set(Calendar.HOUR_OF_DAY, (nightModeSession.startTime.hour + 12))
        else startTime.set(Calendar.HOUR_OF_DAY, (nightModeSession.startTime.hour))
        startTime.set(Calendar.MINUTE, nightModeSession.startTime.minute)

        if (nightModeSession.endTime.timeSet == "PM" && nightModeSession.endTime.hour != 12)
            endTime.set(Calendar.HOUR_OF_DAY, (nightModeSession.endTime.hour + 12))
        else endTime.set(Calendar.HOUR_OF_DAY, (nightModeSession.endTime.hour))
        endTime.set(Calendar.MINUTE, nightModeSession.endTime.minute)
        endTime.set(Calendar.SECOND, 0)

        when {
            (now.get(Calendar.AM_PM) == Calendar.AM) -> {
                // StartTime to be subtracted by one since the current day is changed
                startTime.set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR) - 1)
            }

            (endTime.get(Calendar.AM_PM) == Calendar.AM) -> {
                // EndTime to be added by one since end time is assumed to be on next day irrespective of anything
                endTime.set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR) + 1)
            }

            (startTime.get(Calendar.AM_PM) == endTime.get(Calendar.AM_PM)) -> {
                if (endTime.get(Calendar.HOUR_OF_DAY) < startTime.get(Calendar.HOUR_OF_DAY)) {
                    // StartTime to be subtracted by one since both time are in evening
                    startTime.set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR) - 1)
                    // EndTime to be added by one since end time is assumed to be on next day irrespective of anything
                    endTime.set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR) + 1)
                } else {
                    // EndTime to be added by one since end time is assumed to be on next day irrespective of anything
                    endTime.set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR) + 1)
                }
            }
        }

        isNightModeActive = now.after(startTime) && now.before(endTime)
        return isNightModeActive
    }

    private val nightModeChecker = object : Runnable {
        override fun run() {
            if (shouldUpdateExposure.not() && fixedCameraISOExposure != null) {
                updateExposure?.invoke(fixedCameraISOExposure)
                isNightModeActive = false
            } else {
                if (enableNightMode()) updateExposure?.invoke(getNextExposureValue())
                else updateExposure?.invoke(0)
            }

            nightModeHandler?.postDelayed(this, (exposureDelay * 1000).toLong())
        }
    }
}