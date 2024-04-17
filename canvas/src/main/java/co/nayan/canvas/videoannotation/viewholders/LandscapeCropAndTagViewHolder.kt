package co.nayan.canvas.videoannotation.viewholders

import android.graphics.Bitmap
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import co.nayan.c3views.crop.Cropping
import co.nayan.canvas.databinding.LayoutLandCropAndTagBinding
import co.nayan.canvas.utils.extractBitmap
import co.nayan.canvas.videoannotation.CropAndTagViewHolder

class LandscapeCropAndTagViewHolder(
    val binding: LayoutLandCropAndTagBinding
) : CropAndTagViewHolder(binding.root) {

    fun bind(cropping: Cropping, originalBitmap: Bitmap?) {
        val croppedBitmap = originalBitmap?.extractBitmap(cropping)
        binding.extractedBitmapView.setImageBitmap(croppedBitmap)
        setupTags(binding.tagsListContainer, cropping.tags)
    }

    private fun setupTags(tagsContainer: LinearLayout, tags: List<String>?) {
        tagsContainer.removeAllViews()
        tags?.forEach {
            val textView = TextView(tagsContainer.context)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(12, 12, 12, 12)
            textView.layoutParams = layoutParams
            textView.textSize = 16.0f
            textView.setTextColor(Color.parseColor("#80FFFFFF"))
            textView.text = it
            tagsContainer.addView(textView)
        }
    }
}