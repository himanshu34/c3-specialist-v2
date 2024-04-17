package co.nayan.canvas.utils

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.yuyakaido.android.cardstackview.*

class CustomCardStackLayoutManager(context: Context?, cardSwipeListener: CardSwipeListener) :
    CardStackLayoutManager(context, cardSwipeListener) {

    init {
        val setting = SwipeAnimationSetting.Builder().apply {
            setVisibleCount(1)
            setStackFrom(StackFrom.Top)
            setSwipeableMethod(SwipeableMethod.Automatic)
            setDuration(Duration.Slow.duration)
            setSwipeThreshold(0.3f)
        }.build()

        setSwipeAnimationSetting(setting)
    }

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler?,
        s: RecyclerView.State?
    ): Int {
        val visibleCount = cardStackSetting.visibleCount
        val state = cardStackState
        return try {
            if (state.topPosition + visibleCount <= itemCount) {
                super.scrollHorizontallyBy(dx, recycler, s)
            } else 0
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            0
        }
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler?,
        s: RecyclerView.State?
    ): Int {
        val visibleCount = cardStackSetting.visibleCount
        val state = cardStackState
        return try {
            if (state.topPosition + visibleCount <= itemCount) {
                return super.scrollVerticallyBy(dy, recycler, s)
            } else 0
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            0
        }
    }
}