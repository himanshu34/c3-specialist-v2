package co.nayan.canvas.utils

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import co.nayan.canvas.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.text.SimpleDateFormat
import java.util.*

object BindingAdapter {

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter("setRecordIdText")
    fun setRecordText(textView: TextView, recordId: Int) {
        textView.text = String.format(textView.context.getString(R.string.record_id_text), recordId)
    }

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter("setRecordedOnTimeText")
    fun setRecordedOnTimeText(textView: TextView, dateString: String?) {
        try {
            dateString?.let {
                val mInputDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val mParsedDate = mInputDateFormat.parse(it)
                val mOutputDateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }
                textView.text = mOutputDateFormat.format(mParsedDate!!)
            } ?: run { textView.text = "NA" }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter(value = ["imageUrl", "error"], requireAll = false)
    fun setTemplateImage(imageView: ImageView, imageUrl: String?, error: Drawable) {
        try {
            val circularProgressDrawable = CircularProgressDrawable(imageView.context)
            circularProgressDrawable.strokeWidth = 4f
            circularProgressDrawable.centerRadius = 10f
            circularProgressDrawable.start()
            if (imageUrl.isNullOrEmpty().not()) {
                Glide.with(imageView.context)
                    .load(imageUrl)
                    .placeholder(circularProgressDrawable)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            imageView.setImageDrawable(error)
                            return true
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            imageView.setImageDrawable(resource)
                            return true
                        }
                    }).into(imageView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}