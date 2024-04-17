package com.nayan.nayancamv2.util

import android.annotation.SuppressLint
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import co.nayan.nayancamv2.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

object BindingAdapter {

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter("loadEventImage")
    fun setEventImageThumb(imageView: ImageView, imageUrl: String?) {
        try {
            val circularProgressDrawable = CircularProgressDrawable(imageView.context)
            circularProgressDrawable.strokeWidth = 4f
            circularProgressDrawable.centerRadius = 10f
            circularProgressDrawable.start()

            if (imageUrl.isNullOrEmpty().not())
                Glide.with(imageView)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(circularProgressDrawable)
                    .error(R.drawable.no_preview)
                    .into(imageView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}