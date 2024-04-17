package co.nayan.c3specialist_v2.utils

import android.annotation.SuppressLint
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

object BindingAdapter {

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter("loadDrawableResource")
    fun setDrawableResource(imageView: ImageView, image: Int) {
        imageView.setImageResource(image)
    }

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter("loadImage")
    fun setImageThumb(imageView: ImageView, imageUrl: String?) {
        if (imageUrl.isNullOrEmpty().not())
            Glide.with(imageView)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(imageView)
    }

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter(value = ["fixed", "value"], requireAll = true)
    fun setTextString(textView: TextView, fixed: String, value: String) {
        textView.text = String.format(fixed, value)
    }
}