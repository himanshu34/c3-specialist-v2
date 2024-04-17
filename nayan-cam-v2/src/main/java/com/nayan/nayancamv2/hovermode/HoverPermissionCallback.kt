package com.nayan.nayancamv2.hovermode

import android.content.Intent

interface HoverPermissionCallback {
    fun onPermissionGranted()
    fun onPermissionDenied(intent: Intent)
    fun onPermissionDeniedAdditional(intent: Intent)
}