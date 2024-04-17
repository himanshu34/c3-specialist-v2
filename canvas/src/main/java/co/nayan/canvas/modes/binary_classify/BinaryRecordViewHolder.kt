package co.nayan.canvas.modes.binary_classify

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.crop.CropPhotoView
import co.nayan.c3views.dragsplit.DragSplitPhotoView
import co.nayan.c3views.paint.PaintPhotoView
import co.nayan.c3views.polygon.PolygonPhotoView
import co.nayan.c3views.quadrilateral.QuadrilateralPhotoView
import co.nayan.c3views.utils.*
import co.nayan.canvas.R
import co.nayan.canvas.databinding.BncRecordLayoutBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import com.github.chrisbanes.photoview.PhotoView
import timber.log.Timber

class BinaryRecordViewHolder(
    val binding: BncRecordLayoutBinding,
    private val clickListener: View.OnClickListener,
    private val longClickListener: View.OnLongClickListener
) : RecyclerView.ViewHolder(binding.root) {

    private var record: Record? = null
    private var drawType: String? = null

    fun onBind(
        record: Record?,
        isInSelectionMode: Boolean,
        isSelected: Boolean,
        position: Int
    ) {
        this.record = record

        binding.photoViewContainer.removeAllViews()

        setupRecord()
        setupSelection(isSelected, isInSelectionMode)

        itemView.setOnClickListener(clickListener)
        itemView.setOnLongClickListener(longClickListener)

        itemView.setTag(R.id.position, record)
        itemView.setTag(R.id.position, position)

        binding.reloadIV.setOnClickListener { setupRecord() }
    }

    private fun setupSelection(isSelected: Boolean, isInSelectionMode: Boolean) {
        if (isSelected) binding.selectionView.visible()
        else binding.selectionView.gone()

        if (isInSelectionMode) {
            binding.recordCheckBox.isChecked = isSelected
            binding.recordCheckBox.visible()
        } else binding.recordCheckBox.gone()
    }

    private fun setupRecord() {
        binding.reloadIV.gone()
        binding.loaderIV.visible()
        binding.photoViewContainer.invisible()
        drawType = record?.drawType()
        record?.displayImage?.let { imageUrl ->
            Glide.with(itemView.context)
                .asBitmap()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .signature(ObjectKey(record?.id.toString()))
                .load(imageUrl)
                .into(target)
        }
    }

    private val target = object : CustomTarget<Bitmap>() {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            itemView.context?.let {
                binding.loaderIV.gone()
                binding.reloadIV.gone()
                binding.photoViewContainer.visible()
                setupAnswer(resource)
            }
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            super.onLoadFailed(errorDrawable)
            itemView.context?.let {
                binding.loaderIV.gone()
                binding.reloadIV.visible()
                binding.photoViewContainer.invisible()
            }
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            Timber.e("In Load Cleared ")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAnswer(bitmap: Bitmap) {
        val annotations = record?.annotations()
        if (annotations.isNullOrEmpty()) {
            val photoView = PhotoView(binding.photoViewContainer.context)
            photoView.setOnTouchListener { _, _ -> false }
            loadImage(bitmap, photoView)
        } else {
            when (drawType) {
                DrawType.BOUNDING_BOX -> {
                    val cropView = CropPhotoView(binding.photoViewContainer.context, null)
                    cropView.reset()
                    cropView.touchEnabled(false)
                    cropView.setBitmapAttributes(bitmap.width, bitmap.height)
                    cropView.crops.addAll(annotations.crops(bitmap))
                    loadImage(bitmap, cropView)
                }
                DrawType.QUADRILATERAL -> {
                    val quadrilateralView =
                        QuadrilateralPhotoView(binding.photoViewContainer.context, null)
                    quadrilateralView.reset()
                    quadrilateralView.touchEnabled(false)
                    quadrilateralView.quadrilaterals.addAll(annotations.quadrilaterals(bitmap))
                    loadImage(bitmap, quadrilateralView)
                }
                DrawType.POLYGON -> {
                    val polygonView = PolygonPhotoView(binding.photoViewContainer.context, null)
                    polygonView.reset()
                    polygonView.touchEnabled(false)
                    polygonView.points.addAll(annotations.polygonPoints(bitmap))
                    loadImage(bitmap, polygonView)
                }
                DrawType.CONNECTED_LINE -> {
                    val paintView = PaintPhotoView(binding.photoViewContainer.context, null)
                    paintView.reset()
                    paintView.touchEnabled(false)
                    paintView.setBitmapAttributes(bitmap.width, bitmap.height)
                    paintView.paintDataList.addAll(annotations.paintDataList(bitmap = bitmap))
                    loadImage(bitmap, paintView)
                }
                DrawType.SPLIT_BOX -> {
                    val dragSplitView = DragSplitPhotoView(binding.photoViewContainer.context, null)
                    dragSplitView.reset()
                    dragSplitView.touchEnabled(false)
                    dragSplitView.setBitmapAttributes(bitmap.width, bitmap.height)
                    dragSplitView.splitCropping.addAll(annotations.splitCrops(bitmap))
                    loadImage(bitmap, dragSplitView)
                }
            }
        }
    }

    private fun loadImage(bitmap: Bitmap, view: PhotoView) {
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        view.layoutParams = layoutParams
        view.setImageBitmap(bitmap)
        binding.photoViewContainer.addView(view)
    }
}