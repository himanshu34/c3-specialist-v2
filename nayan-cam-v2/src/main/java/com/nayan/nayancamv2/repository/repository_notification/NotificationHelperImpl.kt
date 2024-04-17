package com.nayan.nayancamv2.repository.repository_notification

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
import androidx.lifecycle.MutableLiveData
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.AnimatingState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.nayancamv2.R
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.getActualBitmapList
import com.nayan.nayancamv2.loadAmountImage
import com.nayan.nayancamv2.loadNotificationImage
import com.nayan.nayancamv2.textToBitmap
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Queue

object NotificationHelperImpl : INotificationHelper {

    private val _notificationState: MutableLiveData<ActivityState> = MutableLiveData(InitialState)
    private val _notificationIntent: MutableLiveData<Intent?> = MutableLiveData(null)
    private val notificationQueue: Queue<Intent> = LinkedList()

    private val animationJob = SupervisorJob()
    private val animationScope = CoroutineScope(Dispatchers.Main + animationJob)

    override fun getNotificationState(): ActivityState? {
        return _notificationState.value
    }

    override fun addNotification(intent: Intent) {
        notificationQueue.add(intent)
        getNotificationState()?.let { state -> if (state == InitialState) pollNotification() }
    }

    override fun pollNotification() {
        val intent = notificationQueue.poll()
        if (intent == null) _notificationState.postValue(InitialState)
        else {
            _notificationState.postValue(ProgressState)
            _notificationIntent.postValue(intent)
        }
    }

    override fun startEventAnimation(
        floatingView: View,
        angle: Float,
        pointsReceived: String,
        eventImage: String
    ) {
        startIconAnimation(floatingView, angle, pointsReceived, eventImage)
    }

    override fun startPointsAnimation(floatingView: View, angle: Float, pointsReceived: String) {
        startIconAnimation(floatingView, angle, pointsReceived, false)
    }

    override fun startBonusAnimation(
        floatingView: View,
        angle: Float,
        amountReceived: String,
        amountImage: Int
    ) {
        startIconAnimation(floatingView, angle, amountReceived, amountImage)
    }

    override fun subscribe() = _notificationIntent

    private fun startIconAnimation(
        floatingView: View,
        angle: Float,
        textString: String,
        image: Any
    ) = animationScope.launch {
        // Change Animation State
        _notificationState.postValue(AnimatingState)

        try {
            val rootView = floatingView.findViewById<ConstraintLayout>(R.id.clRoot)
            val hoverIcon = floatingView.findViewById<ConstraintLayout>(R.id.flRoot)
            val rootViewParams = rootView.layoutParams as WindowManager.LayoutParams
            val hoverIconParams = hoverIcon.layoutParams as ConstraintLayout.LayoutParams
            if (angle >= 300F) {
                hoverIconParams.endToEnd = rootView.id
                hoverIconParams.startToStart = UNSET
            } else {
                hoverIconParams.startToStart = rootView.id
                hoverIconParams.endToEnd = UNSET
            }

            val res = floatingView.context.resources
            val notificationSize = res.getDimension(R.dimen.hover_notifications_size).toInt()
            val radius = res.getDimension(R.dimen.hover_notifications_animation_radius).toInt()
            rootView.layoutParams = rootViewParams
            hoverIcon.layoutParams = hoverIconParams

            // Dynamic Circle ImageView
            val pointsImageView = CircleImageView(floatingView.context).apply {
                borderColor = Color.BLACK
                borderWidth = 5
                layoutParams =
                    ConstraintLayout.LayoutParams(notificationSize, notificationSize).apply {
                        circleAngle = angle
                        circleRadius = radius
                        if (angle >= 300F) {
                            bottomToBottom = hoverIcon.id
                            topToBottom = hoverIcon.id
                            endToStart = hoverIcon.id
                        } else {
                            startToEnd = hoverIcon.id
                            topToBottom = hoverIcon.id
                            bottomToBottom = hoverIcon.id
                        }
                    }
            }
            rootView.addView(pointsImageView)

            val bitmapJob = async {
                when (image) {
                    is String -> floatingView.context.loadNotificationImage(image, notificationSize)
                    is Int -> floatingView.context.loadAmountImage(image, notificationSize)
                    else -> null
                }
            }

            val textAsBitmapJob = async {
                val textSize =
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18F, res.displayMetrics)
                textString.textToBitmap(
                    notificationSize,
                    notificationSize,
                    textSize,
                    textColor = Color.WHITE,
                    backgroundColor = Color.BLACK
                )
            }

            val (notificationBitmap, textStringBitmap) = awaitAll(bitmapJob, textAsBitmapJob)
            val bitmapsList = getActualBitmapList(notificationBitmap, textStringBitmap)
            startFadeInOutAnimation(pointsImageView, bitmapsList, 0) {
                rootView.removeView(pointsImageView)
                pollNotification()
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            pollNotification()
        }
    }

    private fun startFadeInOutAnimation(
        imageView: ImageView,
        images: MutableList<Bitmap>,
        imageIndex: Int,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        val fadeIn = ObjectAnimator.ofFloat(imageView, "alpha", .1f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        val fadeOut = ObjectAnimator.ofFloat(imageView, "alpha", 1f, .1f).apply {
            duration = 300
            interpolator = AccelerateInterpolator()
        }
        val mAnimationSet = AnimatorSet().apply {
            play(fadeOut).after(fadeIn)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    imageView.setImageBitmap(images[imageIndex])
                }

                override fun onAnimationEnd(animation: Animator, isReverse: Boolean) {
                    super.onAnimationEnd(animation, isReverse)
                    if (imageIndex < images.size - 1)
                        startFadeInOutAnimation(imageView, images, imageIndex + 1, onAnimationEnd)
                    else onAnimationEnd?.invoke()
                }
            })
        }
        mAnimationSet.start()
    }
}