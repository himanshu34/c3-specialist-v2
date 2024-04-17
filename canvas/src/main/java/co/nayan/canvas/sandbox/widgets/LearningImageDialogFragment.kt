package co.nayan.canvas.sandbox.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Judgment
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.utils.*
import co.nayan.canvas.R
import co.nayan.canvas.databinding.LayoutLearningImageDialogFragmentBinding
import co.nayan.canvas.sandbox.SandboxFailureListener
import co.nayan.canvas.sandbox.models.FilteredAnnotations
import co.nayan.canvas.sandbox.models.FilteredAnswers
import co.nayan.canvas.sandbox.models.LearningImageData
import co.nayan.canvas.utils.LearningDataUtils
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketException
import java.util.concurrent.ExecutionException

class LearningImageDialogFragment : DialogFragment() {

    companion object {
        fun newInstance(
            learningImageData: LearningImageData,
            applicationMode: String?,
            confirmationListener: SandboxFailureListener
        ): LearningImageDialogFragment {
            val f = LearningImageDialogFragment()
            val args = Bundle()
            args.putParcelable("learningImageData", learningImageData)
            args.putString("applicationMode", applicationMode)
            f.callback = confirmationListener
            f.arguments = args
            return f
        }
    }

    private var annotations: FilteredAnnotations? = null
    private var learningImageData: LearningImageData? = null
    private var applicationMode: String? = null
    private var drawType: String? = null
    private var callback: SandboxFailureListener? = null
    private lateinit var originalBitmap: Bitmap
    private lateinit var binding: LayoutLearningImageDialogFragmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        setStyle(STYLE_NORMAL, R.style.DialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutLearningImageDialogFragmentBinding.inflate(inflater, container, false).apply {
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
        arguments?.let {
            learningImageData = it.parcelable("learningImageData")
            applicationMode = it.getString("applicationMode")
        }
        setupData()
        setupClicks()
    }

    private fun resetAllViews() {
        binding.cropView.reset()
        binding.polygonView.reset()
        binding.quadrilateralView.reset()
        binding.paintView.reset()
        binding.dragSplitView.reset()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClicks() {
        binding.okBtn.setOnClickListener {
            callback?.onConfirm(applicationMode, annotations?.correctAnnotations)
            dismiss()
        }

        binding.correctAnnotationsBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    annotations?.let {
                        resetAllViews()
                        setupAnnotationsWithViews(it.correctAnnotations, "#4CAF50")
                    }
                }

                MotionEvent.ACTION_UP -> {
                    annotations?.let {
                        resetAllViews()
                        setupAnnotationData(it)
                    }
                }
            }
            true
        }

        binding.incorrectAnnotationsBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    annotations?.let {
                        resetAllViews()
                        setupAnnotationsWithViews(it.incorrectAnnotations, "#DD3A2E")
                    }
                }

                MotionEvent.ACTION_UP -> {
                    annotations?.let {
                        resetAllViews()
                        setupAnnotationData(it)
                    }
                }
            }
            true
        }

        binding.missingAnnotationsBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    annotations?.let {
                        resetAllViews()
                        setupAnnotationsWithViews(it.missingAnnotations, "#FFC107")
                    }
                }

                MotionEvent.ACTION_UP -> {
                    annotations?.let {
                        resetAllViews()
                        setupAnnotationData(it)
                    }
                }
            }
            true
        }

        binding.reloadIV.setOnClickListener {
            setupData()
        }
    }

    private fun setupData() {
        val data = learningImageData
        if (data == null) {
            showMessage(getString(R.string.something_went_wrong))
            return
        }

        val record = data.record
        if (record == null) {
            showMessage(getString(R.string.something_went_wrong))
            return
        }

        drawType = record.annotation?.drawType()
            ?: data.filteredAnnotations?.incorrectAnnotations?.firstOrNull()?.type

        if (record.mediaType == MediaType.CLASSIFICATION_VIDEO) {
            binding.annotationsBtnContainer.gone()
            if (data.filteredAnswers?.isEmpty() == true) binding.answerContainer.gone()
            else binding.answerContainer.visible()
            loadVideoThumb(
                record = record,
                filteredAnswers = data.filteredAnswers,
                applicationMode = applicationMode
            )
        } else {
            val url = record.displayImage ?: record.mediaUrl
            url?.let { mediaUrl ->
                when {
                    mediaUrl.isVideo() -> {
                        binding.annotationsBtnContainer.gone()
                        if (data.filteredAnswers?.isEmpty() == true) binding.answerContainer.gone()
                        else binding.answerContainer.visible()
                        loadVideoThumb(
                            record = record,
                            filteredAnswers = data.filteredAnswers,
                            applicationMode = applicationMode
                        )
                    }

                    else -> {
                        if (data.filteredAnnotations == null) {
                            binding.annotationsBtnContainer.gone()
                            binding.answerContainer.visible()
                            loadImage(
                                record = record,
                                filteredAnswers = data.filteredAnswers,
                                applicationMode = applicationMode
                            )
                        } else {
                            binding.annotationsBtnContainer.visible()
                            if (data.filteredAnswers?.isEmpty() == true) binding.answerContainer.gone()
                            else binding.answerContainer.visible()
                            loadImage(
                                record,
                                data.filteredAnnotations,
                                data.filteredAnswers,
                                applicationMode
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadVideoThumb(
        record: Record,
        filteredAnswers: FilteredAnswers? = null,
        applicationMode: String?
    ) {
        lifecycleScope.launch {
            try {
                binding.reloadIV.gone()
                binding.loaderIV.visible()
                drawType.view().visible()
                val imageUrl = record.mediaUrl ?: record.displayImage
                Glide.with(drawType.view().context)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            if (dialog != null) {
                                binding.reloadIV.visible()
                                binding.loaderIV.gone()
                            }
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            if (dialog != null) {
                                binding.reloadIV.gone()
                                binding.loaderIV.gone()
                            }
                            return false
                        }

                    }).into(drawType.view())

                filteredAnswers?.let {
                    val annotations = record.currentAnnotation?.annotations() ?: emptyList()
                    if (annotations.isNotEmpty()) setupAnnotationsWithViews(annotations)
                    setupAnswers(it, applicationMode)
                }
            } catch (e: ExecutionException) {
                Firebase.crashlytics.recordException(e)
                if (dialog != null) {
                    binding.reloadIV.visible()
                    binding.loaderIV.gone()
                }
            } catch (e: SocketException) {
                Firebase.crashlytics.recordException(e)
                if (dialog != null) {
                    binding.reloadIV.visible()
                    binding.loaderIV.gone()
                }
            }
        }
    }

    private fun loadImage(
        record: Record,
        filteredAnnotations: FilteredAnnotations? = null,
        filteredAnswers: FilteredAnswers? = null,
        applicationMode: String?
    ) {
        lifecycleScope.launch {
            try {
                binding.reloadIV.gone()
                binding.loaderIV.visible()
                drawType.view().visible()
                val imageUrl = record.displayImage
                Glide.with(drawType.view().context)
                    .asBitmap()
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(requestListener)
                    .into(drawType.view())

                originalBitmap = getOriginalBitmapFromUrl(imageUrl, drawType.view().context)

                filteredAnnotations?.let { setupAnnotationData(it) }
                filteredAnswers?.let {
                    val annotations = record.currentAnnotation?.annotations() ?: emptyList()
                    if (annotations.isNotEmpty()) setupAnnotationsWithViews(annotations)
                    setupAnswers(it, applicationMode)
                }
            } catch (e: ExecutionException) {
                Firebase.crashlytics.recordException(e)
                if (dialog != null) {
                    binding.reloadIV.visible()
                    binding.loaderIV.gone()
                }
            } catch (e: SocketException) {
                Firebase.crashlytics.recordException(e)
                if (dialog != null) {
                    binding.reloadIV.visible()
                    binding.loaderIV.gone()
                }
            }
        }
    }

    private fun setupAnnotationData(filteredAnnotations: FilteredAnnotations) {
        annotations = filteredAnnotations
        setupAnnotationsWithViews(
            filteredAnnotations.correctAnnotations, "#4CAF50"
        )
        setupAnnotationsWithViews(
            filteredAnnotations.incorrectAnnotations, "#DD3A2E"
        )
        setupAnnotationsWithViews(
            filteredAnnotations.missingAnnotations, "#FFC107"
        )
    }

    private fun setupAnnotationsWithViews(
        annotations: List<AnnotationData>, color: String? = null
    ) {
        if (isBitmapInitialized()) {
            when (drawType) {
                DrawType.BOUNDING_BOX -> {
                    binding.cropView.editMode(false)
                    binding.cropView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.cropView.crops.addAll(annotations.crops(originalBitmap, color))
                    binding.cropView.invalidate()
                }

                DrawType.QUADRILATERAL -> {
                    binding.quadrilateralView.editMode(false)
                    binding.quadrilateralView.setBitmapAttributes(
                        originalBitmap.width.toFloat(), originalBitmap.height.toFloat()
                    )
                    binding.quadrilateralView.quadrilaterals.addAll(
                        annotations.quadrilaterals(originalBitmap, color)
                    )
                    binding.quadrilateralView.invalidate()
                }

                DrawType.POLYGON -> {
                    binding.polygonView.editMode(false)
                    color?.let { binding.polygonView.setOverlayColor(it) }
                    binding.polygonView.points.addAll(annotations.polygonPoints(originalBitmap))
                    binding.polygonView.invalidate()
                }

                DrawType.CONNECTED_LINE -> {
                    binding.paintView.editMode(false)
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
                    binding.dragSplitView.editMode(false)
                    binding.dragSplitView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.dragSplitView.splitCropping.addAll(
                        annotations.splitCrops(
                            originalBitmap,
                            color
                        )
                    )
                    binding.dragSplitView.invalidate()
                }
            }
        }
    }

    private fun setupAnswers(filteredAnswers: FilteredAnswers, applicationMode: String?) {
        binding.correctAnswerHead.visible()
        binding.userAnswerHead.visible()
        val correctAnswer = filteredAnswers.correctAnswer
        val userAnswer = filteredAnswers.userAnswer
        when (applicationMode) {
            Mode.INPUT, Mode.MULTI_INPUT -> {
                binding.correctAnswerTxt.visible()
                binding.userAnswerTxt.visible()
                binding.correctAnswerTxt.text =
                    LearningDataUtils.getFormattedCorrectAnswer(correctAnswer, userAnswer)
                binding.userAnswerTxt.text =
                    LearningDataUtils.getFormattedUserAnswer(correctAnswer, userAnswer)
            }

            Mode.LP_INPUT -> {
                binding.correctLpInputContainer.visible()
                binding.userLpInputContainer.visible()
                setupLpInputModeAnswers(correctAnswer, userAnswer)
            }

            Mode.CLASSIFY, Mode.DYNAMIC_CLASSIFY, Mode.EVENT_VALIDATION -> {
                binding.correctAnswerTxt.visible()
                binding.userAnswerTxt.visible()
                binding.correctAnswerTxt.text = correctAnswer
                binding.userAnswerTxt.text = userAnswer
            }

            else -> {
                if (correctAnswer == Judgment.JUNK) {
                    binding.correctAnswerTxt.visible()
                    binding.userAnswerHead.gone()
                    binding.userAnswerTxt.gone()
                    binding.correctAnswerTxt.text = correctAnswer
                }

                if (userAnswer == Judgment.JUNK) {
                    binding.userAnswerTxt.visible()
                    binding.correctAnswerHead.gone()
                    binding.correctAnswerTxt.gone()
                    binding.userAnswerTxt.text = userAnswer
                }
            }
        }
    }

    private fun setupLpInputModeAnswers(correctAnswer: String, userAnswer: String) {
        val correctLpData = LearningDataUtils.getLpData(correctAnswer)
        val userLpData = LearningDataUtils.getLpData(userAnswer)

        binding.userFirstInputTxt.text = LearningDataUtils.getFormattedUserAnswer(
            correctLpData.firstInput, userLpData.firstInput
        )
        binding.userSecondInputTxt.text = LearningDataUtils.getFormattedUserAnswer(
            correctLpData.secondInput, userLpData.secondInput
        )
        binding.userThirdInputTxt.text = LearningDataUtils.getFormattedUserAnswer(
            correctLpData.thirdInput, userLpData.thirdInput
        )
        binding.correctFirstInputTxt.text = LearningDataUtils.getFormattedCorrectAnswer(
            correctLpData.firstInput, userLpData.firstInput
        )
        binding.correctSecondInputTxt.text = LearningDataUtils.getFormattedCorrectAnswer(
            correctLpData.secondInput, userLpData.secondInput
        )
        binding.correctThirdInputTxt.text = LearningDataUtils.getFormattedCorrectAnswer(
            correctLpData.thirdInput, userLpData.thirdInput
        )
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.rootView, message, Snackbar.LENGTH_SHORT).show()
    }

    private val requestListener = object : RequestListener<Bitmap> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Bitmap>,
            isFirstResource: Boolean
        ): Boolean {
            if (dialog != null) {
                binding.reloadIV.visible()
                binding.loaderIV.gone()
            }
            return false
        }

        override fun onResourceReady(
            resource: Bitmap,
            model: Any,
            target: Target<Bitmap>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            if (dialog != null) {
                binding.reloadIV.gone()
                binding.loaderIV.gone()
            }
            return false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("learningImageData", learningImageData)
        outState.putString("applicationMode", applicationMode)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        learningImageData = savedInstanceState?.parcelable("learningImageData")
        applicationMode = savedInstanceState?.getString("applicationMode")
    }

    private fun isBitmapInitialized() =
        this@LearningImageDialogFragment::originalBitmap.isInitialized

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
}