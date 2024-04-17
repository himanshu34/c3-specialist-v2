package co.nayan.canvas.utils

import android.graphics.Bitmap
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

open class GifRequestListener : RequestListener<GifDrawable> {
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<GifDrawable>,
        isFirstResource: Boolean
    ): Boolean {
        onFailed()
        e?.printStackTrace()
        return false
    }

    override fun onResourceReady(
        resource: GifDrawable,
        model: Any,
        target: Target<GifDrawable>?,
        dataSource: DataSource,
        isFirstResource: Boolean
    ): Boolean {
        onSuccess(resource)
        return false
    }

    open fun onFailed() = Unit
    open fun onSuccess(resource: GifDrawable?) = Unit
}

open class BitmapRequestListener : RequestListener<Bitmap> {
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<Bitmap>,
        isFirstResource: Boolean
    ): Boolean {
        onFailed()
        e?.printStackTrace()
        return false
    }

    override fun onResourceReady(
        resource: Bitmap,
        model: Any,
        target: Target<Bitmap>?,
        dataSource: DataSource,
        isFirstResource: Boolean
    ): Boolean {
        onSuccess(resource)
        return false
    }

    open fun onFailed() = Unit
    open fun onSuccess(resource: Bitmap?) = Unit
}