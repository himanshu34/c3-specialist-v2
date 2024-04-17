package co.nayan.canvas.modes.quadrilateral

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.quadrilateral.QuadrilateralInteractionInterface
import co.nayan.c3views.quadrilateral.QuadrilateralPoint
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.quadrilaterals
import co.nayan.canvas.AnnotationCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentQuadrilateralBinding
import co.nayan.canvas.databinding.QuestionContainerBinding
import co.nayan.canvas.viewBinding
import co.nayan.canvas.views.getUserCategoryDrawable
import co.nayan.canvas.widgets.RecordInfoDialogFragment
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class QuadrilateralFragment : AnnotationCanvasFragment(R.layout.fragment_quadrilateral) {

    private var record: Record? = null
    private val binding by viewBinding(FragmentQuadrilateralBinding::bind)
    private lateinit var questionnaireBinding: QuestionContainerBinding

    override fun resetViews(applicationMode: String?, correctAnnotationList: List<AnnotationData>) {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val questionnaireLayout = binding.root.findViewById<ConstraintLayout>(R.id.questionLayout)
        questionnaireBinding = DataBindingUtil.bind(questionnaireLayout)!!

        setupViews()
        setupClicks()
        if (canvasViewModel.isSandbox() && binding.bottomLayout.helpBtnContainer.isVisible &&
            canvasViewModel.shouldPlayHelpVideo(canvasViewModel.applicationMode)
        )
            binding.bottomLayout.helpBtnContainer.performClick()
    }

    override fun setupViews() {
        super.setupViews()
        questionnaireBinding.questionTxt.text = getQuestion()
        questionnaireBinding.tvUserCategoryMedal.apply {
            text = canvasViewModel.userCategory
            val drawable = getUserCategoryDrawable(canvasViewModel.userCategory)
            if (drawable != null) {
                setCompoundDrawablesWithIntrinsicBounds(0, drawable, 0, 0)
                visible()
            } else gone()
        }
        binding.quadrilateralView.setQuadrilateralInteractionInterface(
            quadrilateralInteractionInterface
        )
        binding.quadrilateralView.setZoomView(binding.zoomView)
    }

    override fun setupCanvasView() {
        if (showPreviousButton()) questionnaireBinding.prevRecordContainer.invisible()
        else questionnaireBinding.prevRecordContainer.visible()
    }

    override fun setupSandboxView(shouldEnableHintBtn: Boolean) {
        questionnaireBinding.prevRecordContainer.invisible()
        if (isAdminRole().not() && isMediaTypeImage()) questionnaireBinding.sandboxProgress.visible()
    }

    override fun setupHelpButton(applicationMode: String?) {
        if (applicationMode.isNullOrEmpty()) binding.bottomLayout.helpBtnContainer.disabled()
        else binding.bottomLayout.helpBtnContainer.enabled()
    }

    private val submitClickListener = View.OnClickListener {
        if (isValidAnnotation()) submitAnnotation(getCurrentAnnotation(true))
        else showMessage(getString(R.string.crop_image_first))
    }

    private val contrastClickListener = View.OnClickListener {
        if (binding.contrastSlider.isVisible) {
            binding.contrastSlider.gone()
            questionnaireBinding.contrastIv.unSelected()
        } else {
            binding.contrastSlider.visible()
            questionnaireBinding.contrastIv.selected()
        }
    }

    private val editClickListener = View.OnClickListener {
        if (binding.quadModeIv.visibility == View.INVISIBLE) {
            binding.quadModeIv.visible()
            binding.quadModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()

            binding.quadrilateralView.editMode(isEnabled = true)
            enableEditMode()
        }
    }

    private val quadModeClickListener = View.OnClickListener {
        if (binding.editModeIv.visibility == View.INVISIBLE) {
            binding.editModeIv.visible()
            binding.editModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()

            binding.quadrilateralView.editMode(isEnabled = false)
            disableEditMode()
        }
    }

    private val undoClickListener = View.OnClickListener {
        binding.quadrilateralView.undo()
        setupJudgementButtons()
    }

    private fun setupClicks() {
        questionnaireBinding.approveOverlayIv.setOnClickListener { binding.quadrilateralView.unSelect() }
        questionnaireBinding.deleteOverlayIv.setOnClickListener { binding.quadrilateralView.delete() }
        binding.bottomLayout.submitBtnContainer.setOnClickListener(submitClickListener)
        questionnaireBinding.backIv.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.bottomLayout.junkBtnContainer.setOnClickListener { showJunkRecordDialog() }
        questionnaireBinding.textToSpeechIv.setOnClickListener { speakOut(questionnaireBinding.questionTxt.text.toString()) }
        questionnaireBinding.contrastIv.setOnClickListener(contrastClickListener)
        binding.reloadIV.setOnClickListener { reloadRecord() }
        binding.editModeIv.setOnClickListener(editClickListener)
        binding.quadModeIv.setOnClickListener(quadModeClickListener)
        binding.bottomLayout.undoBtnContainer.setOnClickListener(undoClickListener)
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangedListener)
        binding.bottomLayout.helpBtnContainer.setOnClickListener { moveToLearningVideoScreen() }
        binding.bottomLayout.sniffingBtnContainer.setOnTouchListener(onSniffingTouchListener)
        questionnaireBinding.recordIdTxt.apply {
            if (canvasViewModel.isSandbox())
                setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            else {
                setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    ContextCompat.getDrawable(context, R.drawable.ic_circle_info),
                    null
                )
                questionnaireBinding.recordIdTxt.setOnTouchListener(infoTouchListener)
            }
        }
    }

    private val infoTouchListener = object : View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            val drawableRight = 2
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (event.rawX >= questionnaireBinding.recordIdTxt.right - questionnaireBinding.recordIdTxt.compoundDrawables[drawableRight].bounds.width()) {
                    record?.let { setupRecordInfoDialog(it) }
                    return true
                }
            }
            return false
        }
    }

    private fun setupRecordInfoDialog(record: Record) = lifecycleScope.launch {
        childFragmentManager.fragments.forEach {
            if (it is RecordInfoDialogFragment) {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val recordInfoDialogFragment = RecordInfoDialogFragment.newInstance(record)
        recordInfoDialogFragment.show(
            childFragmentManager.beginTransaction(),
            getString(R.string.record_info)
        )
    }

    private val quadrilateralInteractionInterface = object : QuadrilateralInteractionInterface {
        override fun selected(status: Boolean) {
            if (status) {
                questionnaireBinding.overlayJudgmentContainer.visible()
                disabledJudgmentButtons()
            } else {
                questionnaireBinding.overlayJudgmentContainer.invisible()
                setupJudgementButtons()
            }
        }

        override fun onQuadrilateralDrawn() {
            setupJudgementButtons()
        }

        override fun onUpdateQuadrilaterals() {
            setAnnotationObjectiveAttributes(getCurrentAnnotation())
        }

        override fun setUpZoomView(isShowing: Boolean, x: Float, y: Float) {
            if (isShowing) {
                binding.zoomViewContainer.visible()
                setupZoomViewPosition(x, y)
            } else {
                binding.zoomViewContainer.invisible()
            }
        }
    }

    private fun resetViews() {
        binding.quadModeIv.performClick()
        binding.quadrilateralView.resetScale()
        binding.quadrilateralView.setImageBitmap(null)
        binding.quadrilateralView.invalidate()
        binding.quadrilateralView.reset()

        binding.reloadIV.gone()
        binding.loaderIV.visible()
    }

    override fun populateRecord(record: Record) {
        this.record = record
        resetViews()
        questionnaireBinding.recordIdTxt.text = getRecordIdText(record)
        lifecycleScope.launch {
            loadBitmap(binding.quadrilateralView, record.displayImage)
            withContext(Dispatchers.Main) {
                if (isBitmapInitialized()) setupQuadrilateralView(record)
            }
        }
        setupJudgementButtons()
    }

    /*private suspend fun checkOrInitOpenCv() {
        if (isOpenCvInitialized()) {
            imageScanner.initDetection(originalBitmap, getScreenSize())
        } else initOpenCv()
    }*/

    override fun populateBitmap(videoAnnotationData: VideoAnnotationData) {
        super.populateBitmap(videoAnnotationData)
        lifecycleScope.launch {
            if (isBitmapInitialized().not()) return@launch
//            checkOrInitOpenCv()
            binding.quadrilateralView.setBitmapAttributes(
                originalBitmap.width.toFloat(),
                originalBitmap.height.toFloat()
            )
            binding.zoomView.setImageAssets(originalBitmap)
            setupJudgementButtons()
            binding.reloadIV.gone()
            binding.loaderIV.gone()
            questionnaireBinding.prevRecordContainer.invisible()
            val quadrilaterals = videoAnnotationData.annotations.quadrilaterals(originalBitmap)
            binding.quadrilateralView.quadrilaterals.clear()
            binding.quadrilateralView.quadrilaterals.addAll(quadrilaterals)
            binding.quadrilateralView.resetScale()
            binding.quadrilateralView.invalidate()
            setAnnotationObjectiveAttributes(getCurrentAnnotation())
            Glide.with(binding.quadrilateralView.context).load(videoAnnotationData.bitmap)
                .into(binding.quadrilateralView)
        }
    }

    private fun setupQuadrilateralView(record: Record) {
        if (activity != null && this.isVisible) {
            binding.quadrilateralView.setBitmapAttributes(
                originalBitmap.width.toFloat(),
                originalBitmap.height.toFloat()
            )
            setupPreviousAnnotations(record)
            setupHintData(record)
            setupSniffingView(record.isSniffingRecord)
            binding.zoomView.setImageAssets(originalBitmap)
        }
    }

    override fun onOpenCVInitialized() {
        if (isBitmapInitialized()) {
            lifecycleScope.launch {
                imageScanner.initDetection(originalBitmap, getScreenSize())
            }
        }
    }

    override fun addHintData(record: Record) {
        binding.quadrilateralView.addHintData(
            record.annotations().quadrilaterals(originalBitmap)
        )
    }

    override fun setupPreviousAnnotations(record: Record) {
        val quadrilaterals = getAnnotationData(record).quadrilaterals(originalBitmap)
        binding.quadrilateralView.quadrilaterals.clear()
        binding.quadrilateralView.quadrilaterals.addAll(quadrilaterals)
        binding.quadrilateralView.invalidate()
        setAnnotationObjectiveAttributes(getCurrentAnnotation())
    }

    private fun setupJudgementButtons() {
        if (activity != null && this.isVisible) {
            binding.quadrilateralView.apply {
                if (this.quadrilaterals.isNullOrEmpty()) {
                    binding.bottomLayout.junkBtnContainer.enabled()
                    binding.bottomLayout.submitBtnContainer.disabled()
                    binding.bottomLayout.undoBtnContainer.disabled()
                } else {
                    binding.bottomLayout.junkBtnContainer.disabled()
                    binding.bottomLayout.submitBtnContainer.enabled()
                    binding.bottomLayout.undoBtnContainer.enabled()
                }
            }
        }
    }

    override fun getCurrentAnnotation(isSubmittingRecord: Boolean): List<AnnotationObjectsAttribute> {
        val quadrilaterals = if (isBitmapInitialized()) {
            binding.quadrilateralView.getAnnotatedQuadrilateral(
                originalBitmap.width.toFloat(),
                originalBitmap.height.toFloat()
            )
        } else emptyList()
        Timber.d(quadrilaterals.joinToString { "[$it]" })
        return quadrilaterals
    }

    override fun updateContrast(colorFilter: ColorMatrixColorFilter) {
        binding.quadrilateralView.colorFilter = colorFilter
    }

    override suspend fun onBitmapLoadFailed() {
        withContext(Dispatchers.Main) {
            binding.reloadIV.visible()
            binding.loaderIV.gone()
        }
    }

    override suspend fun onBitmapReady(colorFilter: ColorMatrixColorFilter, contrastProgress: Int) {
        withContext(Dispatchers.Main) {
            binding.reloadIV.gone()
            binding.loaderIV.gone()
            binding.contrastSlider.progress = contrastProgress
            binding.quadrilateralView.colorFilter = colorFilter
        }
    }

    override fun disabledJudgmentButtons() {
        binding.bottomLayout.submitBtnContainer.disabled()
        binding.bottomLayout.junkBtnContainer.disabled()
    }

    override fun setupContrastSlider(progress: Int) {
        binding.contrastSlider.progress = progress
    }

    override fun enableEditMode() {
        questionnaireBinding.recordIdTxt.invisible()
        questionnaireBinding.tvUserCategoryMedal.invisible()
        questionnaireBinding.questionContainer.invisible()
        questionnaireBinding.prevRecordContainer.invisible()
        binding.contrastSlider.gone()
        questionnaireBinding.sandboxProgress.gone()

        questionnaireBinding.contrastIv.unSelected()
        binding.rootContainer.selected()
        binding.bottomLayout.bottomButtonsContainer.selected()
        binding.backgroundContainer.upperBackgroundView.selected()
    }

    override fun disableEditMode() {
        questionnaireBinding.recordIdTxt.visible()
        questionnaireBinding.tvUserCategoryMedal.visible()
        questionnaireBinding.questionContainer.visible()
        if (isSandbox().not() && showPreviousButton()) {
            questionnaireBinding.prevRecordContainer.visible()
        } else {
            if (isAdminRole().not() && isMediaTypeImage()) {
                questionnaireBinding.sandboxProgress.visible()
            }
        }
        binding.contrastSlider.gone()

        binding.quadrilateralView.unSelect()
        questionnaireBinding.contrastIv.unSelected()
        binding.rootContainer.unSelected()
        binding.bottomLayout.bottomButtonsContainer.unSelected()
        binding.backgroundContainer.upperBackgroundView.unSelected()
    }

    override fun enableUndo(isEnabled: Boolean) {
        questionnaireBinding.prevRecordContainer.isEnabled = isEnabled
    }

    override fun showHint() {
        binding.quadrilateralView.showHint = true
        binding.quadrilateralView.invalidate()
    }

    override fun hideHint() {
        binding.quadrilateralView.showHint = false
        binding.quadrilateralView.invalidate()
    }

    override fun setupZoomView(params: ViewGroup.LayoutParams) {
        binding.zoomViewContainer.layoutParams = params
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.quadrilateralView, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun addBoundingBoxes(validObjects: ArrayList<Pair<List<RectF>, String?>>) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isVisible && isBitmapInitialized()) {
                validObjects.map { currentObject ->
                    binding.quadrilateralView.addQuadrilateralsIfNotExist(
                        currentObject.first.quadrilaterals(originalBitmap)
                    )
                }

                setAnnotationObjectiveAttributes(getCurrentAnnotation())
                binding.quadrilateralView.invalidate()
                setupJudgementButtons()
            }
        }
    }

    override fun onDetected(points: List<PointF>, screenSizeBitmap: Bitmap) {
        if (isVisible) {
            val quadrilateralPoints = mutableListOf<QuadrilateralPoint>()
            points.forEach {
                val quad = QuadrilateralPoint(it.x, it.y).apply {
                    transformWrtBitmap(screenSizeBitmap)
                }
                quadrilateralPoints.add(quad)
            }
            binding.quadrilateralView.addDetectedEdgePoints(quadrilateralPoints)
            binding.quadrilateralView.invalidate()
        }
    }

    private fun setupSniffingView(isSniffing: Boolean?) {
        if (isSniffing == true && isQAEnvironment()) {
            binding.bottomLayout.sniffingBtnContainer.visible()
            binding.bottomLayout.helpBtnContainer.gone()
        } else {
            binding.bottomLayout.sniffingBtnContainer.gone()
            binding.bottomLayout.helpBtnContainer.visible()
        }
    }

    override fun updateCurrentStreak(streak: Int, maxStreak: Int) {
        questionnaireBinding.sandboxProgress.apply {
            max = maxStreak
            progress = streak
        }
    }
}