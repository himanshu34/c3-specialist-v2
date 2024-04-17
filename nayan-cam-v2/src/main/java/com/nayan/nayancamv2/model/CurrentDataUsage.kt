package com.nayan.nayancamv2.model

import androidx.annotation.Keep

@Keep
data class CurrentDataUsage(
    val data: Double,
    val timestamp: Long
)