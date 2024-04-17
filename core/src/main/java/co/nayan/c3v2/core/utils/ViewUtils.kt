package co.nayan.c3v2.core.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import co.nayan.c3v2.core.R

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.enabled() {
    isEnabled = true
}

fun View.disabled() {
    isEnabled = false
}

fun View.selected() {
    isSelected = true
}

fun View.unSelected() {
    isSelected = false
}

fun LinearLayout.disabled() {
    isEnabled = false
    children.forEach { it.disabled() }
}

fun LinearLayout.enabled() {
    isEnabled = true
    children.forEach { it.enabled() }
}

fun View.rightSwipeVisibleAnimation() {
    startAnimation(AnimationUtils.loadAnimation(context, R.anim.rigth_visible_slide))
}

fun View.rightSwipeInvisibleAnimation() {
    startAnimation(AnimationUtils.loadAnimation(context, R.anim.right_invisible_slide))
}

fun View.leftSwipeVisibleAnimation() {
    startAnimation(AnimationUtils.loadAnimation(context, R.anim.left_visible_slide))
}

fun View.leftSwipeInvisibleAnimation() {
    startAnimation(AnimationUtils.loadAnimation(context, R.anim.left_invisible_slide))
}

fun View.scaleBy(scale: Float) {
    this.scaleX = scale
    this.scaleY = scale
}

fun View.swipeUpAnimation() {
    animate().translationY(0f).alpha(0f)
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
            }
        })
}

fun View.swipeDownAnimation() {
    visible()
    alpha = 0f
    animate().translationY(0f).alpha(1f).setListener(null)
}

fun TextView.spannableString(
    prefixString: String,
    postfixString: String,
    fakeBold: Boolean? = false,
    callback: (Int) -> Unit
) {
    val spanTxt = SpannableStringBuilder()
    spanTxt.append(prefixString)
    spanTxt.append(postfixString)
    spanTxt.setSpan(object : ClickableSpan() {
        override fun onClick(widget: View) {
            callback(0)
            widget.invalidate()
        }

        override fun updateDrawState(ds: TextPaint) {
            fakeBold?.let { ds.isFakeBoldText = it }
        }
    }, prefixString.length, spanTxt.length, 0)
    this.movementMethod = LinkMovementMethod.getInstance()
    this.setText(spanTxt, TextView.BufferType.SPANNABLE)
}

fun TextView.colorSpannableStringWithUnderLineOne(
    prefixString: String,
    postfixString: String,
    fakeBold: Boolean? = false,
    callback: (Int) -> Unit
) {
    val spanTxt = SpannableStringBuilder()
    spanTxt.append(prefixString)
    spanTxt.append(postfixString)
    spanTxt.setSpan(object : ClickableSpan() {
        override fun onClick(widget: View) {
            callback(0)
            widget.invalidate()
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.color = ContextCompat.getColor(context, R.color.link)
            fakeBold?.let { ds.isFakeBoldText = it } ?: run { ds.isUnderlineText = true }
        }
    }, prefixString.length, spanTxt.length, 0)
    this.movementMethod = LinkMovementMethod.getInstance()
    this.setText(spanTxt, TextView.BufferType.SPANNABLE)
}