package co.nayan.c3specialist_v2.utils

import android.view.View
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

class ZoomOutTransformation : ViewPager.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        when {
            position < -1 -> {
                page.alpha = 0f
            }
            position <= 1 -> {
                page.scaleX = MIN_SCALE.coerceAtLeast(1 - abs(position))
                page.scaleY = MIN_SCALE.coerceAtLeast(1 - abs(position))
                page.alpha = MIN_ALPHA.coerceAtLeast(1 - abs(position))
            }
            else -> {
                page.alpha = 0f
            }
        }
    }

    companion object {
        const val MIN_SCALE = 0.65f
        const val MIN_ALPHA = 0.3f
    }
}