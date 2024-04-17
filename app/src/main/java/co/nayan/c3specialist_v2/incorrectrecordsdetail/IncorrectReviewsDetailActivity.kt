package co.nayan.c3specialist_v2.incorrectrecordsdetail

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityIncorrectJudgmentsDetailBinding
import co.nayan.c3specialist_v2.record_visualization.video_type_record.VideoTypeRecordActivity
import co.nayan.c3specialist_v2.utils.createDataRecord
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectReview
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.utils.*
import co.nayan.canvas.utils.isVideo
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
class IncorrectReviewsDetailActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val incorrectRecordDetailsViewModel: IncorrectRecordDetailsViewModel by viewModels()
    private val binding: ActivityIncorrectJudgmentsDetailBinding by viewBinding(
        ActivityIncorrectJudgmentsDetailBinding::inflate
    )

    private var incorrectReview: IncorrectReview? = null
    private var drawType: String? = null
    private lateinit var originalBitmap: Bitmap
    private var maskedBitmap: Bitmap? = null

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
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        title = getString(R.string.incorrect_reviews)
        binding.makeTransparent.setOnTouchListener(makeTransparentListener)

        incorrectRecordDetailsViewModel.state.observe(this, stateObserver)
        setupExtras()
        binding.videoViewContainer.setOnClickListener(videoViewClickListener)
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                showProgressDialog(
                    getString(co.nayan.canvas.R.string.please_wait),
                    isCancelable = true
                )
                binding.recordContainer.gone()
            }

            is IncorrectRecordDetailsViewModel.RecordResultState -> {
                hideProgressDialog()
                binding.recordContainer.visible()
                setupRecord(it.record)
            }

            is ErrorState -> {
                hideProgressDialog()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private val videoViewClickListener = View.OnClickListener {
        incorrectReview?.let { review ->
            Intent(this@IncorrectReviewsDetailActivity, VideoTypeRecordActivity::class.java).apply {
                putExtra(Extras.APPLICATION_MODE, review.dataRecord?.applicationMode)
                putExtra(Extras.RECORD, review.dataRecord?.createDataRecord(review.annotation))
                putExtra(
                    Extras.QUESTION, review.wfStep?.question.question(review.annotation?.answer())
                )
                startActivity(this)
            }
        }
    }

    private fun setupExtras() {
        incorrectReview = intent.parcelable(Extras.INCORRECT_REVIEW)
        if (incorrectReview == null) showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
        else incorrectRecordDetailsViewModel.fetchRecord(incorrectReview?.dataRecord?.id)
    }

    private fun setupRecord(toSet: Record?) {
        incorrectReview?.dataRecord = toSet
        incorrectReview?.let { review ->
            lifecycleScope.launch {
                try {
                    binding.recordIdTxt.text = "${review.dataRecord?.id}"
                    binding.questionTxt.text =
                        String.format(review.wfStep?.question.question(review.annotation?.answer()))

                    if (review.dataRecord?.mediaType == MediaType.VIDEO) {
                        binding.videoViewContainer.visible()
                        binding.videoView.gone()
                    } else {
                        val url = if (review.dataRecord?.mediaType == MediaType.VIDEO)
                            review.dataRecord?.mediaUrl
                        else review.dataRecord?.displayImage ?: review.dataRecord?.mediaUrl
                        url?.let { mediaUrl ->
                            if (mediaUrl.isVideo()) {
                                onVideoReady(mediaUrl)
                            } else {
                                binding.videoViewContainer.gone()
                                binding.videoView.gone()
                                drawType = review.annotation?.drawType()
                                drawType.view().visible()
                                drawType.view().setImageBitmap(null)

                                val imageUrl = review.dataRecord?.displayImage

                                if (imageUrl?.contains("gif") == true) {
                                    Glide.with(drawType.view())
                                        .asGif()
                                        .load(imageUrl)
                                        .into(drawType.view())
                                } else {
                                    if (drawType != DrawType.MASK) {
                                        Glide.with(drawType.view())
                                            .asBitmap()
                                            .load(imageUrl)
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .listener(requestListener)
                                            .into(drawType.view())
                                    }
                                    originalBitmap =
                                        getOriginalBitmapFromUrl(review.dataRecord?.displayImage)
                                }
                                setupAnnotation(review.annotation)
                            }
                        }
                    }

                    binding.correctAnswer.text = review.getCorrectReview()
                    binding.yourAnswer.text = review.getUserReview()
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

    private fun onVideoReady(mediaUrl: String) {
        binding.videoViewContainer.gone()
        binding.videoView.visible()
        binding.reloadIV.gone()
        binding.loaderIV.gone()
        // Play Video in VideoView
        val mediaController = MediaController(this)
        binding.videoView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setVideoURI(Uri.parse(mediaUrl))
            setOnPreparedListener {
                binding.videoView.layoutParams = binding.videoView.layoutParams.apply {
                    val mediaProportion: Float =
                        it.videoHeight.toFloat() / it.videoWidth.toFloat()
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = (binding.videoView.width.toFloat() * mediaProportion).toInt()
                }
                it.start()
                it.isLooping = true
                it.setOnVideoSizeChangedListener { _, _, _ ->
                    binding.videoView.setMediaController(mediaController)
                    mediaController.setAnchorView(binding.videoView)
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

    private fun setupAnnotation(annotation: CurrentAnnotation?) {
        val annotations = annotation?.annotations()
        if (annotations.isNullOrEmpty()) {
            val answer = annotation?.answer()
            if (answer == Judgment.JUNK) {
                binding.junkRecordIv.visible()
                binding.answerTxt.gone()
            } else {
                binding.junkRecordIv.gone()
                binding.answerTxt.text = answer
                binding.answerTxt.visible()
            }
            resetAllViews()
        } else {
            binding.junkRecordIv.gone()
            binding.answerTxt.gone()
            drawAnnotations(annotations)
        }
    }

    private fun drawAnnotations(annotations: List<AnnotationData>) {
        binding.makeTransparent.gone()
        maskedBitmap = null
        if (isBitmapInitialized()) {
            when (drawType) {
                DrawType.BOUNDING_BOX -> {
                    binding.cropView.editMode(isEnabled = false)
                    binding.cropView.crops.clear()
                    binding.cropView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.cropView.crops.addAll(annotations.crops(originalBitmap))
                    binding.makeTransparent.visibility = binding.cropView.getTransparentVisibility()
                    binding.cropView.invalidate()
                }

                DrawType.QUADRILATERAL -> {
                    binding.quadrilateralView.editMode(isEnabled = false)
                    binding.quadrilateralView.quadrilaterals.clear()
                    binding.quadrilateralView.quadrilaterals.addAll(
                        annotations.quadrilaterals(originalBitmap)
                    )
                    binding.quadrilateralView.invalidate()
                }

                DrawType.POLYGON -> {
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
                    binding.paintView.paintDataList.addAll(annotations.paintDataList(bitmap = originalBitmap))
                    binding.paintView.invalidate()
                }

                DrawType.SPLIT_BOX -> {
                    binding.dragSplitView.editMode(isEnabled = false)
                    binding.dragSplitView.splitCropping.clear()
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
                            onBitmapReady()
                            binding.photoView.setImageBitmap(originalBitmap)
                        } else downloadMaskBitmaps(imageUrls)
                    }
                }
            }
        }
    }

    private suspend fun downloadMaskBitmaps(imageUrls: List<String?>) {
        val bitmaps = mutableListOf<Bitmap>()
        var success = true
        imageUrls.forEach { imageUrl ->
            try {
                val bitmap = getOriginalBitmapFromUrl(imageUrl)
                bitmaps.add(bitmap)
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                e.printStackTrace()
                success = false
            }
        }

        if (success && isBitmapInitialized()) {
            onBitmapReady()
            binding.makeTransparent.visible()
            maskedBitmap = originalBitmap.overlayBitmap(bitmaps)
            binding.photoView.setImageBitmap(maskedBitmap)
        } else onMaskBitmapLoadFailed()
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

    private suspend fun onMaskBitmapLoadFailed() {
        withContext(Dispatchers.Main) {
            drawType.view().visible()
            binding.reloadIV.gone()
            binding.loaderIV.gone()

            binding.photoView.setImageBitmap(originalBitmap)
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

    private fun String?.question(answer: String?): String {
        return if (this.isNullOrEmpty()) {
            ""
        } else {
            if (this.contains("%{answer}")) {
                val replaceBy = if (!answer.isNullOrEmpty()) {
                    answer
                } else {
                    "\" \""
                }
                this.replaceFirst("%{answer}", replaceBy, true)
            } else {
                this
            }
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
        this@IncorrectReviewsDetailActivity::originalBitmap.isInitialized

    override fun showMessage(message: String) {
        Snackbar.make(binding.photoView, message, Snackbar.LENGTH_SHORT).show()
    }
}