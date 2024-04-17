package co.nayan.c3specialist_v2.datarecords.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.databinding.LayoutDataRecordDialogFragmentBinding
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.utils.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.SocketException
import java.util.concurrent.ExecutionException

@AndroidEntryPoint
class DataRecordDialogFragment : DialogFragment() {

    private var record: Record? = null
    private var applicationMode: String? = null
    private var drawType: String? = null
    private lateinit var originalBitmap: Bitmap
    private var maskedBitmap: Bitmap? = null
    private lateinit var binding: LayoutDataRecordDialogFragmentBinding

    @SuppressLint("ClickableViewAccessibility")
    private val makeTransparentListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                when (drawType) {
                    DrawType.BOUNDING_BOX -> {
                        binding.cropView.showLabel = false
                        binding.cropView.invalidate()
                    }

                    DrawType.SPLIT_BOX -> {
                        binding.dragSplitView.showLabel = false
                        binding.dragSplitView.invalidate()
                    }

                    DrawType.MASK -> {
                        binding.photoView.setImageBitmap(originalBitmap)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                when (drawType) {
                    DrawType.BOUNDING_BOX -> {
                        binding.cropView.showLabel = true
                        binding.cropView.invalidate()
                    }

                    DrawType.SPLIT_BOX -> {
                        binding.dragSplitView.showLabel = true
                        binding.dragSplitView.invalidate()
                    }

                    DrawType.MASK -> {
                        binding.photoView.setImageBitmap(maskedBitmap)
                    }
                }
            }
        }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            record = it.parcelable(RECORD)
            applicationMode = it.getString(APPLICATION_MODE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutDataRecordDialogFragmentBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupData()
        binding.makeTransparent.setOnTouchListener(makeTransparentListener)
    }

    private fun setupData() {
        if (record == null) {
            showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            return
        }

        record?.let {
            loadImage(it)
        }

        binding.reloadIV.setOnClickListener {
            record?.let { toLoad ->
                loadImage(toLoad)
            }
        }
    }

    private fun loadImage(record: Record) {
        lifecycleScope.launch {
            binding.recordIdTxt.text = String.format(getString(co.nayan.canvas.R.string.record_id_text), record.id)
            drawType = record.currentAnnotation?.drawType()
            drawType.view().visible()
            val imageUrl = record.displayImage

            when {
                imageUrl?.contains(".gif") == true -> {
                    Glide.with(drawType.view())
                        .asGif()
                        .load(imageUrl)
                        .listener(gifRequestListener)
                        .into(drawType.view())
                }

                else -> {
                    if (drawType != DrawType.MASK) {
                        Glide.with(drawType.view().context)
                            .asBitmap()
                            .load(imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .listener(requestListener)
                            .into(drawType.view())
                    }

                    try {
                        originalBitmap = getOriginalBitmapFromUrl(imageUrl, drawType.view().context)
                        val annotations = record.annotations()
                        Timber.e(annotations.joinToString { "[$it]" })
                        if (annotations.isNotEmpty()) {
                            setupAnnotationsWithViews(annotations)
                        } else {
                            setupAnswer(record.answer())
                        }
                    } catch (e: ExecutionException) {
                        Firebase.crashlytics.recordException(e)
                        Timber.d(e)
                        binding.reloadIV.visible()
                        binding.loaderIV.gone()
                    } catch (e: SocketException) {
                        Firebase.crashlytics.recordException(e)
                        Timber.d(e)
                        binding.reloadIV.visible()
                        binding.loaderIV.gone()
                    }
                }
            }
        }
    }

    private fun setupAnnotationsWithViews(annotations: List<AnnotationData>) {
        binding.makeTransparent.gone()
        maskedBitmap = null
        when (drawType) {
            DrawType.BOUNDING_BOX -> {
                binding.cropView.editMode(false)
                binding.cropView.setBitmapAttributes(originalBitmap.width, originalBitmap.height)
                binding.cropView.crops.addAll(annotations.crops(originalBitmap))
                binding.makeTransparent.visibility = binding.cropView.getTransparentVisibility()
                binding.cropView.invalidate()
            }

            DrawType.QUADRILATERAL -> {
                binding.quadrilateralView.editMode(false)
                binding.quadrilateralView.quadrilaterals.addAll(
                    annotations.quadrilaterals(
                        originalBitmap
                    )
                )
                binding.quadrilateralView.invalidate()
            }

            DrawType.POLYGON -> {
                binding.polygonView.editMode(false)
                binding.polygonView.points.addAll(annotations.polygonPoints(originalBitmap))
                binding.polygonView.invalidate()
            }

            DrawType.CONNECTED_LINE -> {
                binding.paintView.editMode(false)
                binding.paintView.setBitmapAttributes(originalBitmap.width, originalBitmap.height)
                binding.paintView.paintDataList.addAll(annotations.paintDataList(bitmap = originalBitmap))
                binding.paintView.invalidate()
            }

            DrawType.SPLIT_BOX -> {
                binding.dragSplitView.editMode(false)
                binding.dragSplitView.setBitmapAttributes(
                    originalBitmap.width,
                    originalBitmap.height
                )
                binding.dragSplitView.splitCropping.addAll(annotations.splitCrops(originalBitmap))
                binding.makeTransparent.visibility =
                    binding.dragSplitView.getTransparentVisibility()
                binding.dragSplitView.invalidate()
            }

            DrawType.MASK -> {
                lifecycleScope.launch {
                    val imageUrls = annotations.map { it.maskUrl }
                    if (imageUrls.isNullOrEmpty()) {
                        onBitmapLoadSuccess()
                        binding.photoView.setImageBitmap(originalBitmap)
                    } else downloadMaskBitmaps(imageUrls)
                }
            }
        }
    }

    private suspend fun downloadMaskBitmaps(imageUrls: List<String?>) {
        val bitmaps = mutableListOf<Bitmap>()
        var success = true
        imageUrls.forEach { imageUrl ->
            try {
                val bitmap = getOriginalBitmapFromUrl(imageUrl, requireContext())
                bitmaps.add(bitmap)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                e.printStackTrace()
                success = false
            }
        }

        if (success) {
            onBitmapLoadSuccess()
            binding.makeTransparent.visible()
            if (this@DataRecordDialogFragment::originalBitmap.isInitialized) {
                maskedBitmap = originalBitmap.overlayBitmap(bitmaps)
                binding.photoView.setImageBitmap(maskedBitmap)
            }
        } else onMaskBitmapLoadFailed()
    }

    private fun setupAnswer(answer: String?) {
        if (answer == Judgment.JUNK) {
            binding.junkRecordIv.visible()
            binding.answerTxt.gone()
        } else {
            binding.junkRecordIv.gone()
            when (applicationMode) {
                Mode.INPUT, Mode.LP_INPUT, Mode.MULTI_INPUT, Mode.CLASSIFY, Mode.DYNAMIC_CLASSIFY, Mode.BINARY_CLASSIFY -> {
                    if (answer.isNullOrEmpty()) {
                        binding.answerTxt.gone()
                    } else {
                        binding.answerTxt.visible()
                        binding.answerTxt.text = answer
                    }
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.rootView, message, Snackbar.LENGTH_SHORT).show()
    }

    private val requestListener = object : RequestListener<Bitmap> {
        override fun onLoadFailed(
            e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch { onBitmapLoadFailed() }
            return false
        }

        override fun onResourceReady(
            resource: Bitmap,
            model: Any,
            target: Target<Bitmap>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch { onBitmapLoadSuccess() }
            return false
        }
    }

    private val gifRequestListener = object : RequestListener<GifDrawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<GifDrawable>,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch { onBitmapLoadFailed() }
            return false
        }

        override fun onResourceReady(
            resource: GifDrawable,
            model: Any,
            target: Target<GifDrawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch { onBitmapLoadSuccess() }
            return false
        }
    }

    private suspend fun getOriginalBitmapFromUrl(imageUrl: String?, context: Context): Bitmap =
        withContext(Dispatchers.IO) {
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .submit().get()
        }

    private fun String?.view(): PhotoView {
        return when (this) {
            DrawType.BOUNDING_BOX -> binding.cropView
            DrawType.QUADRILATERAL -> binding.quadrilateralView
            DrawType.POLYGON -> binding.polygonView
            DrawType.CONNECTED_LINE -> binding.paintView
            DrawType.SPLIT_BOX -> binding.dragSplitView
            else -> binding.photoView
        }
    }

    private suspend fun onBitmapLoadFailed() {
        withContext(Dispatchers.Main) {
            binding.reloadIV.visible()
            binding.loaderIV.gone()
        }
    }

    private suspend fun onMaskBitmapLoadFailed() {
        withContext(Dispatchers.Main) {
            binding.reloadIV.gone()
            binding.loaderIV.gone()
            binding.photoView.setImageBitmap(originalBitmap)
        }
    }

    private suspend fun onBitmapLoadSuccess() {
        withContext(Dispatchers.Main) {
            binding.reloadIV.gone()
            binding.loaderIV.gone()
        }
    }

    companion object {
        const val RECORD = "record"
        const val APPLICATION_MODE = "applicationMode"

        fun newInstance(record: Record, applicationMode: String?) =
            DataRecordDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(RECORD, record)
                    putString(APPLICATION_MODE, applicationMode)
                }
            }
    }
}