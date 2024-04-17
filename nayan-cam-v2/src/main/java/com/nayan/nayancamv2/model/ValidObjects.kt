package com.nayan.nayancamv2.model

import android.graphics.Bitmap
import androidx.annotation.Keep
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.imageprocessing.classifiers.Recognition

@Keep
data class ValidObjects(
    val objectDetected: Recognition,
    val nextAiModel: CameraAIModel?,
    val cropBitmap: Bitmap
)
