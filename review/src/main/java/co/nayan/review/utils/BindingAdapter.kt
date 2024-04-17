package co.nayan.review.utils

import android.annotation.SuppressLint
import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.text.SimpleDateFormat
import java.util.*

object BindingAdapter {

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter("setRecordedOnTimeText")
    fun setRecordedOnTimeText(textView: TextView, dateString: String?) {
        try {
            dateString?.let {
                val mInputDateFormat =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                mInputDateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val mOutputDateFormat =
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val mParsedDate = mInputDateFormat.parse(it)
                mOutputDateFormat.timeZone = TimeZone.getDefault()
                textView.text = mOutputDateFormat.format(mParsedDate!!)
            } ?: run {
                textView.text = "NA"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}