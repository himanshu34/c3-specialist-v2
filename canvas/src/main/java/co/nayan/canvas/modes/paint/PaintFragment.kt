package co.nayan.canvas.modes.paint

import android.annotation.SuppressLint
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.paint.LineDrawnListener
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.paintDataList
import co.nayan.canvas.AnnotationCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentPaintBinding
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
class PaintFragment : AnnotationCanvasFragment(R.layout.fragment_paint) {

    private var record: Record? = null
    private val binding by viewBinding(FragmentPaintBinding::bind)
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
        ) binding.bottomLayout.helpBtnContainer.performClick()
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
        binding.paintView.setLineDrawnListener(lineDrawListener)
        binding.paintView.setZoomView(binding.zoomView)
    }

    override fun setupCanvasView() {
        if (showPreviousButton()) questionnaireBinding.prevRecordContainer.invisible()
        else questionnaireBinding.prevRecordContainer.visible()
    }

    override fun setupSandboxView(shouldEnableHintBtn: Boolean) {
        questionnaireBinding.prevRecordContainer.invisible()
        if (isAdminRole().not() && isMediaTypeImage())
            questionnaireBinding.sandboxProgress.visible()
    }

    override fun setupHelpButton(applicationMode: String?) {
        if (applicationMode.isNullOrEmpty()) binding.bottomLayout.helpBtnContainer.disabled()
        else binding.bottomLayout.helpBtnContainer.enabled()
    }

    private val lineDrawListener = object : LineDrawnListener {
        override fun onConnectedLineDrawn() {
            questionnaireBinding.overlayJudgmentContainer.visible()
            questionnaireBinding.nextLineTxt.invisible()
            setupJudgementButtons()
        }

        override fun onLineSelected(isSelected: Boolean) {
            if (isSelected) questionnaireBinding.overlayJudgmentContainer.visible()
            else questionnaireBinding.overlayJudgmentContainer.invisible()
        }

        override fun showZoom(shouldShow: Boolean, x: Float, y: Float) {
            if (shouldShow) {
                setupZoomViewPosition(x, y)
                binding.zoomViewContainer.visible()
            } else binding.zoomViewContainer.gone()
        }

        override fun onTouch() {
            if (questionnaireBinding.thicknessSlider.isVisible) {
                questionnaireBinding.thicknessSlider.gone()
                questionnaireBinding.thicknessIv.unSelected()
            }
        }
    }

    private val approveOverlayClickListener = View.OnClickListener {
        if (binding.lineModeIv.isVisible) {
            questionnaireBinding.nextLineTxt.visible()
            binding.paintView.closeCurrentLineStroke(isNewLineAdded = true)
        } else binding.paintView.cancelSelection()

        questionnaireBinding.overlayJudgmentContainer.invisible()
        setupJudgementButtons()
    }

    private val deleteOverlayClickListener = View.OnClickListener {
        if (binding.lineModeIv.isVisible) {
            questionnaireBinding.nextLineTxt.visible()
            binding.paintView.clearCurrentLineStroke()
        } else binding.paintView.deleteSelectedStrokes()

        questionnaireBinding.overlayJudgmentContainer.invisible()
        setupJudgementButtons()
    }

    private val nextLineClickListener = View.OnClickListener {
        binding.paintView.closeCurrentLineStroke()
        questionnaireBinding.nextLineTxt.invisible()
        setAnnotationObjectiveAttributes(getCurrentAnnotation())
        setupJudgementButtons()
    }

    private val curvedLineClickListener = View.OnClickListener {
        if (it.isSelected.not()) {
            it.selected()
            questionnaireBinding.straightLineIv.unSelected()
            binding.paintView.isCurveSelectedForConnectedLines = true
            binding.paintView.invalidate()
        }
    }

    private val straightLineClickListener = View.OnClickListener {
        if (it.isSelected.not()) {
            it.selected()
            questionnaireBinding.curvedLineIv.unSelected()
            binding.paintView.isCurveSelectedForConnectedLines = false
            binding.paintView.updateLineCurvePoints()
            binding.paintView.invalidate()
        }
    }

    private val submitClickListener = View.OnClickListener {
        if (isValidAnnotation()) submitAnnotation(getCurrentAnnotation(true))
        else showMessage(getString(R.string.draw_line_first))
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

    private val undoClickListener = View.OnClickListener {
        binding.paintView.undo()
        setupJudgementButtons()
    }

    private val editModeClickListener = View.OnClickListener {
        if (binding.lineModeIv.visibility == View.INVISIBLE) {
            binding.lineModeIv.visible()
            binding.lineModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()
            binding.toolTxt?.text = getString(R.string.line)

            enableEditMode()
            binding.paintView.setStrokeMode(DrawType.CONNECTED_LINE)
            questionnaireBinding.straightLineIv.selected()
            questionnaireBinding.curvedLineIv.unSelected()
            binding.paintView.isCurveSelectedForConnectedLines = false
        }
    }

    private val selectionModeClickListener = View.OnClickListener {
        if (binding.editModeIv.visibility == View.INVISIBLE) {
            binding.editModeIv.visible()
            binding.editModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()
            binding.toolTxt?.text = getString(R.string.edit)
            disableEditMode()
        }
    }

    private val lineModeClickListener = View.OnClickListener {
        if (binding.selectionModeIv.visibility == View.INVISIBLE) {
            questionnaireBinding.approveOverlayIv.performClick()
            questionnaireBinding.nextLineTxt.performClick()

            binding.selectionModeIv.visible()
            binding.selectionModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()
            binding.toolTxt?.text = getString(R.string.select)

            binding.paintView.setStrokeMode(DrawType.SELECT)
            questionnaireBinding.lineSelectorContainer.invisible()
        }
    }

    private val onThicknessButtonClickListener = View.OnClickListener {
        if (it.isSelected) questionnaireBinding.thicknessSlider.gone()
        else {
            questionnaireBinding.thicknessSlider.visible()
            questionnaireBinding.thicknessSlider.progress = binding.paintView.strokeWidth.toInt()
        }
        it.isSelected = !it.isSelected
    }

    private fun setupClicks() {
        questionnaireBinding.approveOverlayIv.setOnClickListener(approveOverlayClickListener)
        questionnaireBinding.deleteOverlayIv.setOnClickListener(deleteOverlayClickListener)
        questionnaireBinding.nextLineTxt.setOnClickListener(nextLineClickListener)
        questionnaireBinding.curvedLineIv.setOnClickListener(curvedLineClickListener)
        questionnaireBinding.straightLineIv.setOnClickListener(straightLineClickListener)
        binding.bottomLayout.submitBtnContainer.setOnClickListener(submitClickListener)
        binding.bottomLayout.junkBtnContainer.setOnClickListener { showJunkRecordDialog() }
        questionnaireBinding.textToSpeechIv.setOnClickListener { speakOut(questionnaireBinding.questionTxt.text.toString()) }
        questionnaireBinding.contrastIv.setOnClickListener(contrastClickListener)
        binding.reloadIV.setOnClickListener { reloadRecord() }
        questionnaireBinding.backIv.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.bottomLayout.undoBtnContainer.setOnClickListener(undoClickListener)
        binding.editModeIv.setOnClickListener(editModeClickListener)
        binding.lineModeIv.setOnClickListener(lineModeClickListener)
        binding.selectionModeIv.setOnClickListener(selectionModeClickListener)
        questionnaireBinding.prevRecordContainer.setOnClickListener {
            if (canvasViewModel.isSubmittingRecords)
                return@setOnClickListener
            undoAnnotation()
        }
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangedListener)
        questionnaireBinding.thicknessIv.setOnClickListener(onThicknessButtonClickListener)
        questionnaireBinding.thicknessSlider.setOnSeekBarChangeListener(onThickChangeListener)
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

    private fun resetViews() {
        if (binding.selectionModeIv.isVisible) {
            binding.selectionModeIv.performClick()
        } else if (binding.lineModeIv.isVisible) {
            binding.lineModeIv.performClick()
            binding.selectionModeIv.performClick()
        }
        binding.paintView.reset()
        binding.paintView.invalidate()
        binding.paintView.resetScale()
        binding.paintView.setImageBitmap(null)

        binding.reloadIV.gone()
        binding.loaderIV.visible()
    }

    override fun populateRecord(record: Record) {
        this.record = record
        resetViews()
        questionnaireBinding.recordIdTxt.text = getRecordIdText(record)
        lifecycleScope.launch {
            loadBitmap(binding.paintView, record.displayImage)
            withContext(Dispatchers.Main) {
                if (isBitmapInitialized()) setupPaintView(record)
            }
        }
        setupJudgementButtons()
    }


    override fun populateBitmap(videoAnnotationData: VideoAnnotationData) {
        super.populateBitmap(videoAnnotationData)
        if (isBitmapInitialized().not()) return
        binding.zoomView.setImageAssets(originalBitmap)
        binding.paintView.setBitmapAttributes(originalBitmap.width, originalBitmap.height)
        setupJudgementButtons()
        binding.reloadIV.gone()
        binding.loaderIV.gone()
        questionnaireBinding.prevRecordContainer.invisible()
        val paintDataList = videoAnnotationData.annotations
            .paintDataList(bitmap = originalBitmap)
        binding.paintView.paintDataList.clear()
        binding.paintView.paintDataList.addAll(paintDataList)
        binding.paintView.resetScale()
        binding.paintView.invalidate()
        setAnnotationObjectiveAttributes(getCurrentAnnotation())
        Glide.with(binding.paintView.context).load(videoAnnotationData.bitmap)
            .into(binding.paintView)
    }

    private fun setupPaintView(record: Record) {
        if (activity != null && this.isVisible) {
            binding.paintView.setBitmapAttributes(originalBitmap.width, originalBitmap.height)
            setupPreviousAnnotations(record)
            setupHintData(record)
            setupSniffingView(record.isSniffingRecord)
            binding.zoomView.setImageAssets(originalBitmap)
        }
    }

    override fun addHintData(record: Record) {
        if (isBitmapInitialized()) {
            binding.paintView.addHintData(
                record.annotations().paintDataList("#0099FF", originalBitmap)
            )
        }
    }

    override fun setupPreviousAnnotations(record: Record) {
        val paintDataList = getAnnotationData(record).paintDataList(bitmap = originalBitmap)
        binding.paintView.paintDataList.clear()
        binding.paintView.paintDataList.addAll(paintDataList)
        binding.paintView.invalidate()
        setAnnotationObjectiveAttributes(getCurrentAnnotation())
    }

    override fun getCurrentAnnotation(isSubmittingRecord: Boolean): List<AnnotationObjectsAttribute> {
        val paintDataList = if (isBitmapInitialized()) {
            binding.paintView.getAnnotatedLines(
                originalBitmap.width.toFloat(),
                originalBitmap.height.toFloat()
            )
        } else emptyList()
        Timber.d(paintDataList.joinToString { "[$it]" })
        return paintDataList
    }

    private fun setupJudgementButtons() {
        if (activity != null && this.isVisible) {
            binding.paintView.apply {
                if (this.paintDataList.isNullOrEmpty()) {
                    binding.bottomLayout.junkBtnContainer.enabled()
                    binding.bottomLayout.submitBtnContainer.disabled()
                    binding.bottomLayout.undoBtnContainer.disabled()
                } else {
                    binding.bottomLayout.junkBtnContainer.disabled()
                    binding.bottomLayout.submitBtnContainer.enabled()

                    if (isLineInEditMode) binding.bottomLayout.undoBtnContainer.disabled()
                    else binding.bottomLayout.undoBtnContainer.enabled()
                }
            }
        }
    }

    override fun updateContrast(colorFilter: ColorMatrixColorFilter) {
        binding.paintView.colorFilter = colorFilter
    }

    override suspend fun onBitmapLoadFailed() {
        super.onBitmapLoadFailed()
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
            binding.paintView.colorFilter = colorFilter
        }
    }

    override fun enabledJudgmentButtons() {
        binding.bottomLayout.submitBtnContainer.enabled()
        binding.bottomLayout.junkBtnContainer.enabled()
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
        questionnaireBinding.lineSelectorContainer.visible()
        questionnaireBinding.lineThicknessContainer.visible()
        questionnaireBinding.sandboxProgress.gone()

        questionnaireBinding.contrastIv.unSelected()
        binding.rootContainer.selected()
        binding.bottomLayout.bottomButtonsContainer.selected()
        binding.backgroundContainer.upperBackgroundView.selected()
        binding.paintView.editMode(isEnabled = true)
        questionnaireBinding.lineThicknessContainer.unSelected()
    }

    override fun disableEditMode() {
        questionnaireBinding.recordIdTxt.visible()
        questionnaireBinding.tvUserCategoryMedal.visible()
        questionnaireBinding.overlayJudgmentContainer.invisible()
        questionnaireBinding.questionContainer.visible()
        if (isSandbox().not() && showPreviousButton()) {
            questionnaireBinding.prevRecordContainer.visible()
        } else {
            if (isAdminRole().not() && isMediaTypeImage()) {
                questionnaireBinding.sandboxProgress.visible()
            }
        }
        binding.contrastSlider.gone()
        questionnaireBinding.lineSelectorContainer.invisible()
        questionnaireBinding.nextLineTxt.invisible()
        questionnaireBinding.lineThicknessContainer.invisible()

        questionnaireBinding.contrastIv.unSelected()
        binding.rootContainer.unSelected()
        binding.bottomLayout.bottomButtonsContainer.unSelected()
        binding.backgroundContainer.upperBackgroundView.unSelected()
        binding.paintView.editMode(isEnabled = false)
        binding.paintView.cancelSelection()
    }

    override fun showHint() {
        binding.paintView.showHint = true
        binding.paintView.invalidate()
    }

    override fun hideHint() {
        binding.paintView.showHint = false
        binding.paintView.invalidate()
    }

    override fun setupZoomView(params: ViewGroup.LayoutParams) {
        binding.zoomViewContainer.layoutParams = params
    }

    override fun updateThickness(value: Float) {
        binding.paintView.strokeWidth = value
        binding.paintView.updateThicknessRatio()
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.paintView, message, Snackbar.LENGTH_SHORT).show()
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