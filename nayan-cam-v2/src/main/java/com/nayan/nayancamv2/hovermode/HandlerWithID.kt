package com.nayan.nayancamv2.hovermode

import android.os.Handler
import android.os.Message

class HandlerWithID : Handler() {
    fun dropCameraDelay(runnableID: Int, r: Runnable?): Boolean {
        val m = Message.obtain(this, r)
        m.what = runnableID
        return sendMessageDelayed(m, 5000L)
    }

    fun tiltDelayed(runnableID: Int, r: Runnable?): Boolean {
        val m = Message.obtain(this, r)
        m.what = runnableID
        return sendMessageDelayed(m, 500L)
    }

    fun hasActiveRunnable(runnableID: Int) = hasMessages(runnableID)

    companion object {
        var runnableIDTilt = 0
        var runnableIDDropCamera = 1
        var runnableIDStartCamera = 2
    }
}