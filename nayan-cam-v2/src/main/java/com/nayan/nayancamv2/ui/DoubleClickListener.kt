package com.nayan.nayancamv2.ui

import android.view.MotionEvent
import android.view.View
import timber.log.Timber


abstract class DoubleClickListener : View.OnTouchListener {

    private val DOUBLE_CLICK_TIME_DELTA: Long = 500

    var lastClickTime: Long = 0

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        val clickTime = System.currentTimeMillis()
        val diff = clickTime - lastClickTime
        Timber.e("diff: $diff")
        if (diff < DOUBLE_CLICK_TIME_DELTA) {
            onDoubleClick(v)
            lastClickTime = 0
        } else {
            onSingleClick(v)
        }
        lastClickTime = clickTime

        return false
    }

    abstract fun onSingleClick(v: View?)
    abstract fun onDoubleClick(v: View?)
}