package com.nayan.nayancamv2.temperature

import android.content.Context
import co.nayan.nayancamv2.R

object TemperatureUtil {

    fun getTempMessage(
        context: Context,
        temperature: Float,
        driverLiteThreshold: Float,
        overHeatingThreshold: Float
    ): String {
        return if (temperature >= driverLiteThreshold && temperature < overHeatingThreshold) {
            context.getString(R.string.temp_state_4, temperature.toInt().toString())
        } else if (temperature >= overHeatingThreshold) {
            context.getString(R.string.temp_state_5, temperature.toInt().toString())
        } else context.getString(R.string.temp_state_1, temperature.toInt().toString())
    }
}