package co.nayan.c3specialist_v2.incorrectrecordsdetail

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityIncorrectAnnotationsDetailBinding
import co.nayan.c3specialist_v2.record_visualization.video_type_record.VideoTypeRecordActivity
import co.nayan.c3specialist_v2.utils.createDataRecord
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectAnnotation
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.utils.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
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
import javax.inject.Inject

@AndroidEntryPoint
class IncorrectAnnotationsDetailActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val incorrectRecordDetailsViewModel: IncorrectRecordDetailsViewModel by viewModels()
    private val binding: ActivityIncorrectAnnotationsDetailBinding by viewBinding(
        ActivityIncorrectAnnotationsDetailBinding::inflate
    )

    private var drawType: String? = null
    private lateinit var originalBitmap: Bitmap
    private var incorrectAnnotation: IncorrectAnnotation? = null

    private var userAnnotation: CurrentAnnotation? = null
    private var correctAnnotation: CurrentAnnotation? = null

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
                }
            }
        }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        title = getString(R.string.incorrect_annotation)

        binding.makeTransparent.setOnTouchListener(makeTransparentListener)
        incorrectRecordDetailsViewModel.state.observe(this, stateObserver)
        setupExtras()
        setupClicks()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                showProgressDialog(
                    getString(R.string.please_wait_fetching_records),
                    isCancelable = true
                )
                binding.recordContainer.gone()
            }

            is IncorrectRecordDetailsViewModel.RecordResultState -> {
                hideProgressDialog()
                binding.recordContainer.visible()
                setupData(it.record)
            }

            is ErrorState -> {
                hideProgressDialog()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupClicks() {
        binding.showUserAnnotation.setOnClickListener {
            it.selected()
            binding.showCorrectAnnotation.unSelected()
            if (incorrectAnnotation?.dataRecord?.mediaType == MediaType.VIDEO) {
                val record =
                    incorrectAnnotation?.dataRecord?.createDataRecord(incorrectAnnotation?.incorrectAnnotation)
                navigateToRecordVideoScreen(record)
            } else {
                loadRecordWithAnnotations(userAnnotation, "#FF0000")
            }
        }

        binding.showCorrectAnnotation.setOnClickListener {
            binding.showUserAnnotation.unSelected()
            it.selected()
            if (incorrectAnnotation?.dataRecord?.mediaType == MediaType.VIDEO) {
                val record = incorrectAnnotation?.dataRecord
                navigateToRecordVideoScreen(record)
            } else {
                loadRecordWithAnnotations(correctAnnotation, "#CDDC39")
            }
        }
    }

    private fun navigateToRecordVideoScreen(record: Record?) {
        Intent(
            this@IncorrectAnnotationsDetailActivity,
            VideoTypeRecordActivity::class.java
        ).apply {
            putExtra(Extras.RECORD, record)
            putExtra(Extras.QUESTION, incorrectAnnotation?.wfStep?.question)
            startActivity(this)
        }
    }

    private fun setupExtras() {
        incorrectAnnotation = intent.parcelable(Extras.INCORRECT_ANNOTATION)
        if (incorrectAnnotation == null) showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
        else incorrectRecordDetailsViewModel.fetchRecord(incorrectAnnotation?.dataRecord?.id)
    }

    private fun setupData(toSet: Record?) {
        incorrectAnnotation?.dataRecord = toSet
        incorrectAnnotation?.let { annotation ->
            binding.recordIdTxt.text = "${annotation.dataRecord?.id}"
            binding.questionTxt.text = "${annotation.wfStep?.question}"

            userAnnotation = annotation.incorrectAnnotation
            correctAnnotation = annotation.dataRecord?.currentAnnotation

            if (userAnnotation.annotations().isNullOrEmpty() &&
                correctAnnotation.annotations().isNullOrEmpty()
            ) {
                loadRecordWOAnnotations()
                binding.correctAnswerContainer.visible()
                binding.yourAnswerContainer.visible()
                binding.showUserAnnotation.gone()
                binding.showCorrectAnnotation.gone()
                binding.correctAnswer.text = correctAnnotation?.answer() ?: ""
                binding.yourAnswer.text = userAnnotation?.answer() ?: ""
            } else {
                binding.correctAnswerContainer.gone()
                binding.yourAnswerContainer.gone()
                binding.showUserAnnotation.visible()
                binding.showCorrectAnnotation.visible()
                binding.showUserAnnotation.selected()
                binding.showCorrectAnnotation.unSelected()
                loadRecordWithAnnotations(userAnnotation, "#FF0000")
            }
        }
    }

    private fun loadRecordWOAnnotations() {
        if (incorrectAnnotation?.dataRecord?.mediaType == MediaType.VIDEO) {
            binding.videoViewContainer.visible()
        } else {
            binding.videoViewContainer.gone()
            lifecycleScope.launch {
                val imageUrl = incorrectAnnotation?.dataRecord?.displayImage
                binding.photoView.apply {
                    visible()
                    try {
                        Glide.with(context).load(imageUrl).into(this)
                    } catch (e: Exception) {
                        Firebase.crashlytics.recordException(e)
                        onBitmapLoadFailed()
                    }
                }
            }
        }
    }

    private fun loadRecordWithAnnotations(annotation: CurrentAnnotation?, color: String) {
        if (incorrectAnnotation?.dataRecord?.mediaType == MediaType.VIDEO) {
            binding.videoViewContainer.visible()
        } else {
            binding.videoViewContainer.gone()
            lifecycleScope.launch {
                try {
                    drawType = annotation?.drawType()
                    drawType.view().visible()
                    drawType.view().setImageBitmap(null)

                    val imageUrl = incorrectAnnotation?.dataRecord?.displayImage
                    if (imageUrl?.contains("gif") == true) {
                        Glide.with(drawType.view())
                            .asGif()
                            .load(imageUrl)
                            .into(drawType.view())
                    } else {
                        Glide.with(drawType.view())
                            .asBitmap()
                            .load(imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .listener(requestListener)
                            .into(drawType.view())

                        originalBitmap = getOriginalBitmapFromUrl(imageUrl)
                        setupAnnotation(annotation, color)
                    }
                } catch (e: ExecutionException) {
                    Firebase.crashlytics.recordException(e)
                    Timber.d(e)
                    onBitmapLoadFailed()
                } catch (e: SocketException) {
                    Firebase.crashlytics.recordException(e)
                    Timber.d(e)
                    onBitmapLoadFailed()
                }
            }
        }
    }

    private fun resetAllViews() {
        binding.cropView.reset()
        binding.cropView.invalidate()
        binding.quadrilateralView.reset()
        binding.quadrilateralView.invalidate()
        binding.polygonView.reset()
        binding.polygonView.invalidate()
        binding.dragSplitView.reset()
        binding.dragSplitView.invalidate()
    }

    private fun setupAnnotation(annotation: CurrentAnnotation?, color: String) {
        val annotations = annotation?.annotations()
        if (annotations.isNullOrEmpty()) {
            val answer = annotation?.answer()
            if (answer == Judgment.JUNK) {
                binding.junkRecordIv.visible()
                binding.answerContainer.gone()
            } else {
                binding.junkRecordIv.gone()
                binding.answerContainer.visible()
            }
            resetAllViews()
        } else {
            binding.answerContainer.gone()
            binding.junkRecordIv.gone()
            drawAnnotations(annotations, color)
        }
    }

    private fun drawAnnotations(annotations: List<AnnotationData>, color: String) {
        if (isBitmapInitialized()) {
            when (drawType) {
                DrawType.BOUNDING_BOX -> {
                    binding.cropView.editMode(isEnabled = false)
                    binding.cropView.crops.clear()
                    binding.cropView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.cropView.crops.addAll(annotations.crops(originalBitmap, color))
                    binding.makeTransparent.visibility = binding.cropView.getTransparentVisibility()
                    binding.cropView.invalidate()
                }

                DrawType.QUADRILATERAL -> {
                    binding.quadrilateralView.editMode(isEnabled = false)
                    binding.quadrilateralView.quadrilaterals.clear()
                    binding.quadrilateralView.quadrilaterals.addAll(
                        annotations.quadrilaterals(originalBitmap, color)
                    )
                    binding.quadrilateralView.invalidate()
                }

                DrawType.POLYGON -> {
                    binding.polygonView.setOverlayColor(color)
                    binding.polygonView.editMode(isEnabled = false)
                    binding.polygonView.points.clear()
                    binding.polygonView.points.addAll(annotations.polygonPoints(originalBitmap))
                    binding.polygonView.invalidate()
                }

                DrawType.CONNECTED_LINE -> {
                    binding.paintView.editMode(isEnabled = false)
                    binding.paintView.paintDataList.clear()
                    binding.paintView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.paintView.paintDataList.addAll(
                        annotations.paintDataList(
                            color,
                            originalBitmap
                        )
                    )
                    binding.paintView.invalidate()
                }

                DrawType.SPLIT_BOX -> {
                    binding.dragSplitView.editMode(isEnabled = false)
                    binding.dragSplitView.splitCropping.clear()
                    binding.dragSplitView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.dragSplitView.splitCropping.addAll(
                        annotations.splitCrops(originalBitmap, color)
                    )
                    binding.makeTransparent.visibility =
                        binding.dragSplitView.getTransparentVisibility()
                    binding.dragSplitView.invalidate()
                }
            }
        }
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
            lifecycleScope.launch { onBitmapReady() }
            return false
        }
    }

    private suspend fun onBitmapLoadFailed() {
        withContext(Dispatchers.Main) {
            drawType.view().gone()
            binding.reloadIV.visible()
            binding.loaderIV.gone()
        }
    }

    private suspend fun onBitmapReady() {
        withContext(Dispatchers.Main) {
            drawType.view().visible()
            binding.reloadIV.gone()
            binding.loaderIV.gone()
        }
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

    private suspend fun getOriginalBitmapFromUrl(imageUrl: String?): Bitmap =
        withContext(Dispatchers.IO) {
            Glide.with(drawType.view().context)
                .asBitmap()
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .submit().get()
        }

    private fun isBitmapInitialized() =
        this@IncorrectAnnotationsDetailActivity::originalBitmap.isInitialized

    override fun showMessage(message: String) {
        Snackbar.make(binding.photoView, message, Snackbar.LENGTH_SHORT).show()
    }
}