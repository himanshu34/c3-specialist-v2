package co.nayan.review.utils

import android.view.View
import com.yuyakaido.android.cardstackview.*

fun CardStackView.swipeRight() {
    this.layoutManager.apply {
        if (this is CustomCardStackLayoutManager) {
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Right)
                .setDuration(Duration.Slow.duration)
                .build()

            setSwipeAnimationSetting(setting)
        }
    }
    swipe()
}

fun CardStackView.swipeLeft() {
    this.layoutManager.apply {
        if (this is CustomCardStackLayoutManager) {
            val setting = SwipeAnimationSetting.Builder()
                .setDirection(Direction.Left)
                .setDuration(Duration.Slow.duration)
                .build()

            setSwipeAnimationSetting(setting)
        }
    }
    swipe()
}

fun CardStackView.enableSwipe() {
    this.layoutManager.apply {
        if (this is CardStackLayoutManager) {
            setCanScrollHorizontal(true)
        }
    }
}

fun CardStackView.disableSwipe() {
    this.layoutManager.apply {
        if (this is CardStackLayoutManager) {
            setCanScrollHorizontal(false)
        }
    }
}

open class CardSwipeListener : CardStackListener {
    override fun onCardDisappeared(view: View?, position: Int) {}
    override fun onCardDragging(direction: Direction?, ratio: Float) {}
    override fun onCardSwiped(direction: Direction?) {}
    override fun onCardCanceled() {}
    override fun onCardAppeared(view: View?, position: Int) {}
    override fun onCardRewound() {}
}