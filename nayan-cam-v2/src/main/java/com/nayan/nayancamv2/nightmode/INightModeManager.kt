package com.nayan.nayancamv2.nightmode

interface INightModeManager {

    suspend fun reset()
    suspend fun updateISOExposureBuffer()
    fun removeNightMode()
    fun checkForNightMode()
    fun getNextExposureValue(): Int
    fun enableNightMode(): Boolean
}