package co.nayan.canvas.views.toast

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.R

enum class ToastyType {
    POSITIVE, NEGATIVE
}

class Toasty(
    val position: Int,
    context: Context,
    message: String,
    toastType: ToastyType
) :
    Toast(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.toasty, null)
        val ivPositive = view.findViewById<AppCompatImageView>(R.id.ivStatusPositive)
        val ivNegative = view.findViewById<AppCompatImageView>(R.id.ivStatusNegative)

        val textMessage = view.findViewById<TextView>(R.id.tvMessage)
        textMessage.text = message

        //0 for set gravity by default / 1 for BOTTOM/.
        if (position == 1)
            setGravity(Gravity.BOTTOM, 0, 0)

        if (toastType == ToastyType.POSITIVE) {
            ivNegative.invisible()
            ivPositive.visible()
        } else {
            ivNegative.visible()
            ivPositive.invisible()
        }

        setView(view)
        duration = LENGTH_SHORT
    }
}