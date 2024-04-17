package co.nayan.c3specialist_v2.utils

import android.content.Context
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import co.nayan.c3specialist_v2.R
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import dagger.hilt.android.qualifiers.ActivityContext
import java.util.*
import javax.inject.Inject

class ViewUtils @Inject constructor(@ActivityContext private val context: Context) {

    fun getErrorBalloon(message: String) =
        Balloon.Builder(context).apply {
            setWidthRatio(0.80f)
            setHeight(65)
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setMarginLeft(16)
            setMarginRight(16)
            setTextSize(15f)
            setCornerRadius(4f)
            setPaddingLeft(8)
            setPaddingRight(8)
            setAlpha(0.9f)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setBalloonAnimation(BalloonAnimation.FADE)
            setLifecycleOwner(lifecycleOwner)
            setText(message)
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary))
        }
}

fun ImageView.setRandomColor() {
    val random = Random()
    val color = Color.rgb(random.nextInt(256), random.nextInt(256), 128)
    setColorFilter(color)
}

fun LinearLayout.setChildren(id: Int) {
    children.forEach { view ->
        if (view is TextView) {
            if (view.id == id) {
                view.setTextColor(Color.parseColor("#FFFFFF"))
                view.elevation = 16f
                view.typeface = ResourcesCompat.getFont(context, co.nayan.canvas.R.font.rubik_medium)
                view.selected()
            } else {
                view.setTextColor(Color.parseColor("#666666"))
                view.elevation = 0f
                view.typeface = ResourcesCompat.getFont(context, co.nayan.canvas.R.font.rubik_regular)
                view.unSelected()
            }
        }
    }
}