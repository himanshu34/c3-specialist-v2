package co.nayan.canvas.videoannotation

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3views.crop.Cropping
import co.nayan.canvas.databinding.LayoutCropAndTagBinding
import co.nayan.canvas.databinding.LayoutLandCropAndTagBinding
import co.nayan.canvas.videoannotation.viewholders.LandscapeCropAndTagViewHolder
import co.nayan.canvas.videoannotation.viewholders.PortraitCropAndTagViewHolder

class CropAndTagsAdapter(
    private val isLandscape: Boolean = false
) : RecyclerView.Adapter<CropAndTagViewHolder>() {

    private val cropping = mutableListOf<Cropping>()
    var originalBitmap: Bitmap? = null

    fun addTags(toAdd: List<Cropping>) {
        cropping.clear()
        cropping.addAll(toAdd)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CropAndTagViewHolder {
        return if (isLandscape) {
            LandscapeCropAndTagViewHolder(
                LayoutLandCropAndTagBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        } else {
            PortraitCropAndTagViewHolder(
                LayoutCropAndTagBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: CropAndTagViewHolder, position: Int) {
        if (isLandscape)
            (holder as LandscapeCropAndTagViewHolder).bind(cropping[position], originalBitmap)
        else (holder as PortraitCropAndTagViewHolder).bind(cropping[position], originalBitmap)
    }

    override fun getItemCount(): Int {
        return cropping.size
    }
}

abstract class CropAndTagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
