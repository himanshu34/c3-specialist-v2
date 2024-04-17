package com.nayan.nayancamv2.repository.repository_notification

import android.content.Intent
import android.view.View
import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState
import java.util.Queue

interface INotificationHelper {

    fun getNotificationState(): ActivityState?

    fun addNotification(intent: Intent)

    fun pollNotification()

    fun startEventAnimation(
        floatingView: View,
        angle: Float,
        pointsReceived: String,
        eventImage: String
    )

    fun startPointsAnimation(
        floatingView: View,
        angle: Float,
        pointsReceived: String,
    )

    fun startBonusAnimation(
        floatingView: View,
        angle: Float,
        amountReceived: String,
        amountImage: Int
    )

    fun subscribe(): MutableLiveData<Intent?>
}