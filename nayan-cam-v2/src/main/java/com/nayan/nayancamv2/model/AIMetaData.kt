package com.nayan.nayancamv2.model

import androidx.annotation.Keep

@Keep
data class AIMetaData(
    val modelName: String,
    val labelName: String,
    val confidence: String,
    val objectFoundAt: Long,
    val aiProcessTime: String,
    val temperatureAt: Float
)
