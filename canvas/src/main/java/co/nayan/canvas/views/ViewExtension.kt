package co.nayan.canvas.views

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.constraintlayout.motion.widget.MotionLayout
import co.nayan.canvas.R
import co.nayan.canvas.views.toast.Toasty
import co.nayan.canvas.views.toast.ToastyType

fun MotionLayout.onEndTransitionCompleted(endConstraintSetId: Int, listener: () -> Unit) {
    val transitionListener = object : MotionLayout.TransitionListener {
        override fun onTransitionCompleted(p0: MotionLayout?, currentId: Int) {
            if (endConstraintSetId == currentId) { // trigger only when transition is completed from start to end ie. on click of searchLayout & not when back is pressed
                listener.invoke()
            }
        }

        override fun onTransitionTrigger(p0: MotionLayout?, p1: Int, p2: Boolean, p3: Float) {
        }

        override fun onTransitionStarted(p0: MotionLayout?, p1: Int, p2: Int) {
        }

        override fun onTransitionChange(p0: MotionLayout?, p1: Int, p2: Int, p3: Float) {
        }
    }
    this.setTransitionListener(transitionListener)
}

fun setTextWithSpan(
    text: String,
    spanText: String,
    style: StyleSpan
): SpannableStringBuilder {
    val sb = SpannableStringBuilder(text)
    if (spanText.isNotEmpty()) {
        val start: Int = text.indexOf(spanText)
        val end: Int = start + spanText.length
        sb.setSpan(style, start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
    }
    return sb
}

fun Context.showToast(position: Int, message: String, toastyType: ToastyType) {
    showToasty(position, this, message, toastyType)
}

private fun showToasty(position: Int, context: Context, message: String, toastyType: ToastyType) {
    Toasty(position, context, message, toastyType).show()
}

fun getUserCategoryDrawable(userCategory: String?): Int? {
    return when (userCategory) {
        "Gold" -> R.drawable.ic_gold_medal
        "Silver" -> R.drawable.ic_silver_medal
        "Bronze" -> R.drawable.ic_bronze_medal
        "New User" -> R.drawable.ic_blue_medal
        else -> null
    }
}