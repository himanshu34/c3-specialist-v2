package co.nayan.canvas.modes.drag_split

import android.annotation.SuppressLint
import android.graphics.ColorMatrixColorFilter
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
import co.nayan.c3views.crop.CropInteractionInterface
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.splitCrops
import co.nayan.canvas.AnnotationCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentDragSplitBinding
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
class DragSplitFragment : AnnotationCanvasFragment(R.layout.fragment_drag_split) {

    private var record: Record? = null
    private var isInput = true
    private val binding by viewBinding(FragmentDragSplitBinding::bind)
    private lateinit var questionnaireBinding: QuestionContainerBinding

    override fun resetViews(applicationMode: String?, correctAnnotationList: List<AnnotationData>) {
        lifecycleScope.launch {
            enableEditMode()
            val crops = correctAnnotationList.splitCrops(originalBitmap)
            binding.dragSplitView.apply {
                this.splitCropping.clear()
                this.splitCropping.addAll(crops)
                resetScale()
                invalidate()

                questionnaireBinding.inputSelectionContainer.unSelected()
                isInLabelingMode = false
                unSelect()
                binding.keyboardView.gone()
            }
            setAnnotationObjectiveAttributes(getCurrentAnnotation())
            enabledJudgmentButtons()
        }
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
        binding.dragSplitView.setCropInteractionInterface(cropInteractionInterface)
        binding.dragSplitView.setZoomView(binding.zoomView)

        if (isInput) {
            binding.keyboardView.setKeyboardActionListener(keyboardActionListener)
            questionnaireBinding.makeTransparentIv.visible()
        }
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

    private val approveOverlayClickListener = View.OnClickListener {
        binding.dragSplitView.unSelect()
        questionnaireBinding.overlayJudgmentContainer.invisible()
        setupJudgementButtons()
    }

    private val deleteOverlayClickListener = View.OnClickListener {
        binding.dragSplitView.delete()
        questionnaireBinding.overlayJudgmentContainer.invisible()
        setupJudgementButtons()
    }

    private val submitClickListener = View.OnClickListener {
        if (isValidAnnotation()) {
            if (isAllLabelsSet()) submitAnnotation(getCurrentAnnotation(true))
            else showMessage(getString(R.string.set_all_label_first))
        } else showMessage(getString(R.string.crop_image_first))
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
        binding.dragSplitView.undo()
        setupJudgementButtons()
    }

    private val editModeClickListener = View.OnClickListener {
        if (binding.cropModeIv.visibility == View.INVISIBLE) {
            binding.cropModeIv.visible()
            binding.cropModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()

            binding.dragSplitView.editMode(isEnabled = true)
            enableEditMode()
        }
    }

    private val cropModeClickListener = View.OnClickListener {
        if (binding.editModeIv.visibility == View.INVISIBLE) {
            binding.editModeIv.visible()
            binding.editModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()

            binding.dragSplitView.editMode(isEnabled = false)
            disableEditMode()
        }
    }

    private val labelSelectionClickListener = View.OnClickListener {
        if (it.isSelected) {
            it.unSelected()
            binding.dragSplitView.isInLabelingMode = false
            binding.dragSplitView.unSelect()
            binding.keyboardView.gone()
        } else {
            it.selected()
            binding.dragSplitView.isInLabelingMode = true
            binding.dragSplitView.unSelect()
            binding.dragSplitView.selectNext()
            binding.dragSplitView.invalidate()
            binding.keyboardView.visible()
        }
    }

    private fun setupClicks() {
        questionnaireBinding.approveOverlayIv.setOnClickListener(approveOverlayClickListener)
        questionnaireBinding.deleteOverlayIv.setOnClickListener(deleteOverlayClickListener)
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
        binding.cropModeIv.setOnClickListener(cropModeClickListener)
        questionnaireBinding.prevRecordContainer.setOnClickListener {
            if (canvasViewModel.isSubmittingRecords)
                return@setOnClickListener
            undoAnnotation()
        }
        binding.addCropBtn.setOnClickListener { binding.dragSplitView.addSegment() }
        binding.removeCropBtn.setOnClickListener { binding.dragSplitView.removeSegment() }
        questionnaireBinding.inputSelectionContainer.setOnClickListener(labelSelectionClickListener)
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangedListener)
        questionnaireBinding.makeTransparentIv.setOnTouchListener(makeTransparentListener)
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

    private val cropInteractionInterface = object : CropInteractionInterface {
        override fun selected(status: Boolean, isClassificationMode: Boolean) {
            if (status) {
                if (isClassificationMode.not()) {
                    questionnaireBinding.overlayJudgmentContainer.visible()
                    binding.splitCropContainer.visible()
                    binding.bottomLayout.undoBtnContainer.disabled()
                    disabledJudgmentButtons()
                }
            } else {
                questionnaireBinding.overlayJudgmentContainer.invisible()
                binding.splitCropContainer.gone()
                binding.bottomLayout.undoBtnContainer.enabled()
                setupJudgementButtons()
            }
        }

        override fun onCropDrawn() {
            setupJudgementButtons()
        }

        override fun showZoom(isShowing: Boolean, x: Float, y: Float) {
            if (isShowing) {
                setupZoomViewPosition(x, y)
                binding.zoomViewContainer.visible()
            } else binding.zoomViewContainer.invisible()
        }

        override fun updateCrops() {
            setAnnotationObjectiveAttributes(getCurrentAnnotation())
        }
    }

    private fun resetViews() {
        binding.cropModeIv.performClick()

        binding.dragSplitView.reset()
        binding.dragSplitView.invalidate()
        binding.dragSplitView.resetScale()
        binding.dragSplitView.setImageBitmap(null)

        binding.reloadIV.gone()
        binding.loaderIV.visible()
    }

    override fun populateRecord(record: Record) {
        this.record = record
        resetViews()
        questionnaireBinding.recordIdTxt.text = getRecordIdText(record)
        lifecycleScope.launch {
            loadBitmap(binding.dragSplitView, record.displayImage)
            withContext(Dispatchers.Main) {
                if (isBitmapInitialized()) setupDragSplitView(record)
            }
        }
        setupJudgementButtons()
    }

    override fun populateBitmap(videoAnnotationData: VideoAnnotationData) {
        super.populateBitmap(videoAnnotationData)
        if (isBitmapInitialized().not()) return
        binding.dragSplitView.setBitmapAttributes(originalBitmap.width, originalBitmap.height)
        binding.zoomView.setImageAssets(originalBitmap)
        setupJudgementButtons()
        binding.reloadIV.gone()
        binding.loaderIV.gone()
        questionnaireBinding.prevRecordContainer.invisible()
        val splitCrops = videoAnnotationData.annotations.splitCrops(originalBitmap)
        binding.dragSplitView.splitCropping.clear()
        binding.dragSplitView.splitCropping.addAll(splitCrops)
        binding.dragSplitView.resetScale()
        binding.dragSplitView.invalidate()
        setAnnotationObjectiveAttributes(getCurrentAnnotation())
        Glide.with(binding.dragSplitView.context).load(videoAnnotationData.bitmap)
            .into(binding.dragSplitView)
    }

    private fun setupDragSplitView(record: Record) {
        if (activity != null && this.isVisible) {
            binding.dragSplitView.setBitmapAttributes(originalBitmap.width, originalBitmap.height)
            setupPreviousAnnotations(record)
            setupHintData(record)
            setupSniffingView(record.isSniffingRecord)
            binding.zoomView.setImageAssets(originalBitmap)
        }
    }

    override fun addHintData(record: Record) {
        binding.dragSplitView.addHintData(record.annotations().splitCrops(originalBitmap))
    }

    override fun setupPreviousAnnotations(record: Record) {
        val crops = getAnnotationData(record).splitCrops(originalBitmap)
        binding.dragSplitView.splitCropping.clear()
        binding.dragSplitView.splitCropping.addAll(crops)
        binding.dragSplitView.invalidate()
        setAnnotationObjectiveAttributes(getCurrentAnnotation())
    }

    override fun getCurrentAnnotation(isSubmittingRecord: Boolean): List<AnnotationObjectsAttribute> {
        val croppingList = if (isBitmapInitialized()) {
            binding.dragSplitView.getAnnotatedCrops(
                originalBitmap.width.toFloat(),
                originalBitmap.height.toFloat()
            )
        } else emptyList()
        Timber.d(croppingList.joinToString { "[$it]" })
        return croppingList
    }

    private fun setupJudgementButtons() {
        if (activity != null && this.isVisible) {
            if (binding.dragSplitView.splitCropping.isNullOrEmpty()) {
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

    override fun updateContrast(colorFilter: ColorMatrixColorFilter) {
        binding.dragSplitView.colorFilter = colorFilter
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
            binding.dragSplitView.colorFilter = colorFilter
        }
    }

    override fun showMessage(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
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

        if (isInput) {
            binding.keyboardView.unSelected()
            questionnaireBinding.makeTransparentIv.gone()
            questionnaireBinding.inputSelectionContainer.visible()
            questionnaireBinding.inputSelectionContainer.unSelected()
        }
    }

    override fun disableEditMode() {
        if (isSandbox().not() && showPreviousButton()) {
            questionnaireBinding.prevRecordContainer.visible()
        } else {
            if (isAdminRole().not() && isMediaTypeImage()) {
                questionnaireBinding.sandboxProgress.visible()
            }
        }
        questionnaireBinding.recordIdTxt.visible()
        questionnaireBinding.tvUserCategoryMedal.visible()
        questionnaireBinding.questionContainer.visible()
        binding.contrastSlider.gone()

        binding.dragSplitView.unSelect()
        questionnaireBinding.contrastIv.unSelected()
        binding.rootContainer.unSelected()
        binding.bottomLayout.bottomButtonsContainer.unSelected()
        binding.backgroundContainer.upperBackgroundView.unSelected()

        if (isInput) {
            questionnaireBinding.makeTransparentIv.visible()
            questionnaireBinding.inputSelectionContainer.gone()
            binding.keyboardView.gone()
            binding.dragSplitView.isInLabelingMode = false
        }
    }

    override fun enableUndo(isEnabled: Boolean) {
        questionnaireBinding.prevRecordContainer.isEnabled = isEnabled
    }

    override fun showHint() {
        binding.dragSplitView.showHint = true
        binding.dragSplitView.invalidate()
    }

    override fun hideHint() {
        binding.dragSplitView.showHint = false
        binding.dragSplitView.invalidate()
    }

    override fun showLabel() {
        binding.dragSplitView.showLabel = true
        binding.dragSplitView.invalidate()
    }

    override fun hideLabel() {
        binding.dragSplitView.showLabel = false
        binding.dragSplitView.invalidate()
    }

    override fun setupZoomView(params: ViewGroup.LayoutParams) {
        binding.zoomViewContainer.layoutParams = params
    }

    override fun appendInput(value: String) {
        binding.dragSplitView.updateInputValue(value)
        binding.dragSplitView.selectNext()
        binding.dragSplitView.invalidate()

        setAnnotationObjectiveAttributes(getCurrentAnnotation())
        setupJudgementButtons()
    }

    private fun isAllLabelsSet(): Boolean {
        return binding.dragSplitView.splitCropping.none { cropping ->
            cropping.inputList.any { it.isEmpty() }
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

    override fun addBoundingBoxes(validObjects: ArrayList<Pair<List<RectF>, String?>>) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isVisible && isBitmapInitialized()) {
                validObjects.map { currentObject ->
                    binding.dragSplitView.addSplitCropIfNotExist(
                        currentObject.first.splitCrops(originalBitmap)
                    )
                }
                setAnnotationObjectiveAttributes(getCurrentAnnotation())
                binding.dragSplitView.invalidate()
                setupJudgementButtons()
            }
        }
    }

    override fun updateCurrentStreak(streak: Int, maxStreak: Int) {
        questionnaireBinding.sandboxProgress.apply {
            max = maxStreak
            progress = streak
        }
    }
}