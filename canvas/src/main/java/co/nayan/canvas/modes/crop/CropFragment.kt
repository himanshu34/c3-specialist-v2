package co.nayan.canvas.modes.crop

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.*
import android.os.Bundle
import android.text.Editable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.Mode.INTERPOLATED_MCML
import co.nayan.c3v2.core.config.Mode.INTERPOLATED_MCMT
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.utils.*
import co.nayan.c3v2.core.widgets.CustomAlertDialogFragment
import co.nayan.c3v2.core.widgets.CustomAlertDialogListener
import co.nayan.c3views.crop.CropInteractionInterface
import co.nayan.c3views.crop.CropPoint
import co.nayan.c3views.crop.MaxCrops
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.crops
import co.nayan.c3views.utils.drawCrops
import co.nayan.canvas.AnnotationCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.config.Dictionary.dictionaryList
import co.nayan.canvas.databinding.FragmentCropBinding
import co.nayan.canvas.databinding.QuestionContainerBinding
import co.nayan.canvas.utils.TextChangeListener
import co.nayan.canvas.utils.cluster
import co.nayan.canvas.utils.extractBitmap
import co.nayan.canvas.utils.superButtons
import co.nayan.canvas.viewBinding
import co.nayan.canvas.views.getUserCategoryDrawable
import co.nayan.canvas.views.showToast
import co.nayan.canvas.views.toast.ToastyType
import co.nayan.canvas.widgets.ConfirmAnnotationsDialogFragment
import co.nayan.canvas.widgets.RecordInfoDialogFragment
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig
import java.util.*

@SuppressLint("NotifyDataSetChanged")
@AndroidEntryPoint
class CropFragment : AnnotationCanvasFragment(R.layout.fragment_crop) {

    private var record: Record? = null
    private var maximumCrops: Int = MaxCrops.MULTI_CROP
    private var isInput: Boolean = false
    private var isLabel: Boolean = false
    private var isMultiLabel: Boolean = false
    private var isInterpolation: Boolean = false
    private var selectedTags = mutableListOf<String>()
    private var labelList: List<Template> = listOf()
    private var labelAdapter: LabelsAdapter? = null
    private val binding by viewBinding(FragmentCropBinding::bind)
    private lateinit var questionnaireBinding: QuestionContainerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            maximumCrops = it.getInt(MAX_CROPS)
            isInput = it.getBoolean(IS_INPUT)
            isLabel = it.getBoolean(IS_LABEL)
            isMultiLabel = it.getBoolean(IS_MULTI_LABEL)
            isInterpolation = it.getBoolean(IS_INTERPOLATION)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val questionnaireLayout = binding.root.findViewById<ConstraintLayout>(R.id.questionLayout)
        questionnaireBinding = DataBindingUtil.bind(questionnaireLayout)!!

        setupViews()
        setupClicks()
        setUpObservers()
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

        questionnaireBinding.overlayTransparentIv.visible()

        binding.cropView.apply {
            this.maxCrops = maximumCrops
            setCropInteractionInterface(cropInteractionInterface)
            setZoomView(binding.zoomView)
            isAIEnabled = isImageProcessingEnabled()
        }.invalidate()

        if (isImageProcessingEnabled()) {
            questionnaireBinding.aiAssistSwitch.visible()
            questionnaireBinding.aiAssistSwitch.isChecked = isAIAssistEnabled()
        } else questionnaireBinding.aiAssistSwitch.gone()

        when {
            isInput -> {
                binding.keyboardView.setKeyboardActionListener(keyboardActionListener)
                questionnaireBinding.makeTransparentIv.visible()
                questionnaireBinding.overlayTransparentIv.gone()
            }

            isLabel || isMultiLabel || isInterpolation -> {
                labelList = listOf()
                setupTemplateObserver()
                fetchTemplates()
                questionnaireBinding.makeTransparentIv.visible()
                questionnaireBinding.doneSelectionContainer.gone()
                binding.templatesView.gone()
            }
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

    override fun resetViews(applicationMode: String?, correctAnnotationList: List<AnnotationData>) {
        lifecycleScope.launch {
            enableEditMode()
            if (isBitmapInitialized()) {
                val crops = correctAnnotationList.crops(originalBitmap)
                binding.cropView.apply {
                    this.selectedLabel = null
                    this.reset()
                    this.crops.addAll(crops)
                    this.resetScale()
                    labelAdapter?.selectedPosition = RecyclerView.NO_POSITION
                    labelAdapter?.updateAnnotations(this.crops)
                    setAnnotationObjectiveAttributes(getCurrentAnnotation())
                }.invalidate()
            }
            setupJudgementButtons()
        }
    }

    override fun setupHelpButton(applicationMode: String?) {
        if (applicationMode.isNullOrEmpty()) binding.bottomLayout.helpBtnContainer.disabled()
        else binding.bottomLayout.helpBtnContainer.enabled()
    }

    private val approveOverlayClickListener = View.OnClickListener {
        questionnaireBinding.overlayJudgmentContainer.invisible()
        setupJudgementButtons()
        when {
            isMultiLabel -> moveToMultiLabelScreen()
            isInterpolation -> {
                if (canvasViewModel.isInterpolatedMCMT() && canvasViewModel.activeAnnotationId == null)
                    moveToMultiLabelScreen()
                else addCropForInterpolationMode()
            }

            else -> binding.cropView.unSelect()
        }
        labelAdapter?.updateAnnotations(binding.cropView.crops)
    }

    private fun addCropForInterpolationMode() {
        val selectedCropping = binding.cropView.selectedCropping
        if (isInPreviewMode()) {
            if (selectedCropping?.id == null) {
                val uniqueInterpolations = fetchUniqueAnnotationList()
                if (uniqueInterpolations.isEmpty()) {
                    binding.cropView.crops.remove(selectedCropping)
                    binding.cropView.unSelect()
                } else showLabelDialog(uniqueInterpolations)
            } else {
                binding.cropView.unSelect()
                if (canvasViewModel.isInterpolatedMCML())
                    questionnaireBinding.classifyTv.performClick()
            }
        } else {
            if (binding.cropView.crops.size > 1) {
                selectedCropping?.let { cropping ->
                    binding.cropView.crops.clear()
                    binding.cropView.crops.add(cropping)
                    if (canvasViewModel.isInterpolatedMCMT()) {
                        canvasViewModel.activeAnnotationId = null
                        canvasViewModel.setActiveAnnotationState(false)
                        moveToMultiLabelScreen()
                    }
                }
            }
            setupInterpolationLinking()
        }
    }

    override fun onAddNewInterpolationAnnotation() {
        val selectedCrop = binding.cropView.selectedCropping
        selectedCrop?.id = if (isAdminRole().not() && isSandbox())
            canvasViewModel.activeSandboxAnnotationId
        else UUID.randomUUID().toString().replace("-", "")
        selectedCrop?.name = uniqueAnnotationNameCount()
        canvasViewModel.activeAnnotationId = selectedCrop?.id
        canvasViewModel.setActiveAnnotationState(true)
        selectedCrop?.paintColor = getRandomHexCode()
        selectedCrop?.annotationState = AnnotationState.MANUAL
        binding.cropView.invalidate()
        binding.cropView.unSelect()

        // Now classify this crop in INTERPOLATED_MCML mode
        if (canvasViewModel.isInterpolatedMCML())
            questionnaireBinding.classifyTv.performClick()
    }

    override fun onSelectInterpolationAnnotation(annotationData: AnnotationData) {
        val selectedCropping = binding.cropView.selectedCropping
        selectedCropping?.id = annotationData.objectIndex
        selectedCropping?.name = annotationData.objectName
        selectedCropping?.input = annotationData.input
        selectedCropping?.tags = annotationData.tags
        selectedCropping?.paintColor = annotationData.paintColor
        selectedCropping?.annotationState = AnnotationState.MANUAL
        binding.cropView.invalidate()
        binding.cropView.unSelect()
        canvasViewModel.setAnnotationObjectiveAttributes(getCurrentAnnotation())
    }

    private val deleteOverlayClickListener = View.OnClickListener {
        if (canvasViewModel.isInterpolatedMCMT()) {
            if (isInPreviewMode())
                canvasViewModel.activeAnnotationId = binding.cropView.selectedCropping?.id
            else canvasViewModel.activeAnnotationId = null
        }
        binding.cropView.delete()
        questionnaireBinding.overlayJudgmentContainer.invisible()
        setupJudgementButtons()
        labelAdapter?.updateAnnotations(binding.cropView.crops)
    }

    private val submitClickListener = View.OnClickListener {
        if (isInPreviewMode()) submitInterpolationAnnotations()
        else {
            if (isValidAnnotation()) {
                if (isInterpolationEnabled()) submitInterpolationAnnotations()
                else if (isInput.xor(isAllInputsSet()).not() || isLabel.xor(isAllInputsSet()).not())
                    submitAnnotation(getCurrentAnnotation(true))
                else showMessage(getString(R.string.set_all_label_first))
            } else showMessage(getString(R.string.crop_image_first))
        }
    }

    private fun submitInterpolationAnnotations() {
        when (canvasViewModel.applicationMode) {
            INTERPOLATED_MCMT -> {
                val isAllCropsTagged = binding.cropView.crops.all { it.tags.isNullOrEmpty().not() }
                if (isInterpolation.xor(isAllCropsTagged).not())
                    submitAnnotation(getCurrentAnnotation(true))
                else showMessage(getString(R.string.set_all_tags_first))
            }

            INTERPOLATED_MCML -> {
                if (isInterpolation.xor(isAllInputsSet()).not())
                    submitAnnotation(getCurrentAnnotation(true))
                else showMessage(getString(R.string.set_all_label_first))
            }
        }
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
        binding.cropView.undo()
        if (isInterpolationEnabled()) canvasViewModel.resetAnnotationsIfExists(getCurrentAnnotation())
        else canvasViewModel.setAnnotationObjectiveAttributes(getCurrentAnnotation())
        setupJudgementButtons()
    }

    private val editModeClickListener = View.OnClickListener {
        if (binding.cropModeIv.visibility == View.INVISIBLE) {
            binding.cropModeIv.visible()
            binding.cropModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()

            binding.cropView.editMode(isEnabled = true)
            enableEditMode()
        }
    }

    private val cropModeClickListener = View.OnClickListener {
        if (binding.editModeIv.visibility == View.INVISIBLE) {
            binding.editModeIv.visible()
            binding.editModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()

            binding.cropView.editMode(isEnabled = false)
            disableEditMode()
        }

        setupWalkThrough()
    }

    private val inputSelectionClickListener = View.OnClickListener {
        if (it.isSelected) {
            it.unSelected()
            binding.cropView.isInLabelingMode = false
            binding.cropView.unSelect()
            binding.keyboardView.gone()
        } else {
            it.selected()
            binding.cropView.isInLabelingMode = true
            binding.cropView.selectNext()
            binding.keyboardView.visible()
        }
    }

    private val classifySelectionClickListener = View.OnClickListener {
        if (isInterpolationEnabled()) {
            if (binding.cropView.crops.isEmpty()) {
                requireContext().showToast(
                    0,
                    getString(R.string.draw_crop_first),
                    ToastyType.NEGATIVE
                )
                return@OnClickListener
            }
        }
        if (it.isSelected) {
            it.unSelected()
            binding.cropView.isInLabelingMode = false
            binding.cropView.unSelect()
            binding.templatesView.gone()
        } else {
            it.selected()
            binding.cropView.isInLabelingMode = true
            binding.cropView.selectNext()
            binding.templatesView.visible()
        }
    }

    private val doneSelectionClickListener = View.OnClickListener {
        childFragmentManager.showDialogFragment(
            ConfirmAnnotationsDialogFragment(binding.cropView.selectedLabel?.templateName) {
                binding.cropView.unSelect()
                canvasViewModel.setSelectedTemplate(null)
            })
    }

    private val onBackClickListener = View.OnClickListener {
        if (selectedTags.isNotEmpty()) showAlert()
        else {
            binding.cropView.unSelect()
            binding.multiTagsContainer?.multiTagsContainer?.gone()
        }
    }

    private val customAlertDialogListener = object : CustomAlertDialogListener {
        override fun onPositiveBtnClick(shouldFinish: Boolean, tag: String?) {
            selectedTags.clear()
            binding.cropView.unSelect()
            binding.multiTagsContainer?.multiTagsContainer?.gone()
        }

        override fun onNegativeBtnClick(shouldFinish: Boolean, tag: String?) {}
    }

    private fun showAlert() {
        val tag = "Discard Changes"
        val message = "You will loose all your changes."
        val title = getString(R.string.are_you_sure)
        val positiveText = getString(R.string.ok)

        childFragmentManager.fragments.forEach {
            if (it is CustomAlertDialogFragment) {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val customAlertDialogFragment =
            CustomAlertDialogFragment.newInstance(customAlertDialogListener).apply {
                setTitle(title)
                setMessage(message)
                setPositiveBtnText(positiveText)
                showPositiveBtn(true)
                showNegativeBtn(true)
            }
        customAlertDialogFragment.show(childFragmentManager.beginTransaction(), tag)
    }

    private val onClearSearchListener = View.OnClickListener {
        binding.multiTagsContainer?.tagsFilterEt?.setText("")
    }

    private val submitTagsOnClickListener = View.OnClickListener {
        val tags = mutableListOf<String>()
        if (isInterpolationEnabled() && selectedTags.isEmpty()) {
            showMessage(getString(R.string.set_all_tags_first))
            return@OnClickListener
        }
        tags.addAll(selectedTags)
        selectedTags.clear()
        binding.cropView.selectedCropping?.tags = tags
        if (isInterpolation) addCropForInterpolationMode()
        else binding.cropView.unSelect()
        canvasViewModel.setAnnotationObjectiveAttributes(getCurrentAnnotation())
        binding.multiTagsContainer?.multiTagsContainer?.gone()
    }

    @SuppressLint("ClickableViewAccessibility")
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
        questionnaireBinding.inputSelectionContainer.setOnClickListener(inputSelectionClickListener)
        questionnaireBinding.classifyTv.setOnClickListener(classifySelectionClickListener)
        questionnaireBinding.doneTv.setOnClickListener(doneSelectionClickListener)
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangedListener)
        questionnaireBinding.makeTransparentIv.setOnTouchListener(makeTransparentListener)
        questionnaireBinding.overlayTransparentIv.setOnTouchListener(overlayTransparentListener)
        binding.bottomLayout.helpBtnContainer.setOnClickListener { moveToLearningVideoScreen() }
        binding.bottomLayout.sniffingBtnContainer.setOnTouchListener(onSniffingTouchListener)
        binding.multiTagsContainer?.backBtn?.setOnClickListener(onBackClickListener)
        binding.multiTagsContainer?.clearTagsFilterIv?.setOnClickListener(onClearSearchListener)
        binding.multiTagsContainer?.submitTagsBtn?.setOnClickListener(submitTagsOnClickListener)
        binding.recyclerViewTemplates.addOnScrollListener(onScrollChangeListener)
        questionnaireBinding.aiAssistSwitch.setOnCheckedChangeListener { _, isChecked ->
            canvasViewModel.isManualAIAssistEnabled = isChecked
            if (isChecked && isAIAssistEnabled()) detectObjects()
            else clearAIAssistAnnotations()
        }

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

    private val onScrollChangeListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                if (dx > 50 && canvasViewModel.user?.walkThroughEnabled == true) {
                    MaterialShowcaseView.Builder(requireActivity())
                        .setDismissOnTouch(true)
                        .setTargetTouchable(true)
                        .setDismissOnTargetTouch(true)
                        .setTarget(binding.rootContainer)
                        .setGravity(Gravity.CENTER)
                        .setDismissText("GOT IT")
                        .setContentText(getString(R.string.choose_class_first))
                        .singleUse("onScrolled")
                        .setMaskColour(Color.parseColor("#CC000000"))
                        .build()
                        .show(requireActivity())
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val overlayTransparentListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                binding.cropView.apply {
                    this.crops.clear()
                }.invalidate()
            }

            MotionEvent.ACTION_UP -> {
                val annotations = canvasViewModel.getAnnotationData()
                if (isBitmapInitialized()) {
                    binding.cropView.apply {
                        this.crops.addAll(annotations.crops(originalBitmap))
                    }.invalidate()
                }
            }
        }
        true
    }

    private val cropInteractionInterface = object : CropInteractionInterface {
        override fun selected(status: Boolean, isClassificationMode: Boolean) {
            if (status) {
                if (isClassificationMode.not()) {
                    questionnaireBinding.overlayJudgmentContainer.visible()
                    binding.bottomLayout.undoBtnContainer.disabled()
                    disabledJudgmentButtons()
                } else if (isLabel && isClassificationMode) {
                    binding.cropView.selectedLabel?.let {
                        binding.cropView.selectedCropping?.input = it.templateName
                        binding.cropView.invalidate()
                        questionnaireBinding.overlayJudgmentContainer.visible()
                        binding.bottomLayout.undoBtnContainer.enabled()
                        setupJudgementButtons()
                        labelAdapter?.updateAnnotations(binding.cropView.crops)
                    } ?: run {
                        requireContext().showToast(
                            0,
                            getString(R.string.choose_class_first),
                            ToastyType.NEGATIVE
                        )
                        binding.cropView.apply {
                            selectedCropping?.let {
                                this.unSelect()
                                this.crops.remove(it)
                            }
                        }.invalidate()
                    }
                }
            } else {
                questionnaireBinding.overlayJudgmentContainer.invisible()
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
        if (activity != null && this.isVisible) {
            binding.cropModeIv.performClick()
            binding.cropView.apply {
                this.reset()
                this.resetScale()
                setImageBitmap(null)
            }.invalidate()

            binding.reloadIV.gone()
            binding.loaderIV.visible()
            canvasViewModel.setSelectedTemplate(null)
            labelAdapter?.resetViews()
        }
    }

    override fun populateRecord(record: Record) {
        this.record = record
        resetViews()
        questionnaireBinding.recordIdTxt.text = getRecordIdText(record)
        lifecycleScope.launch {
            loadBitmap(binding.cropView, record.displayImage)
            withContext(Dispatchers.Main) {
                if (isBitmapInitialized()) setupCropView(record)
            }
        }
        setupJudgementButtons()
    }

    /*private suspend fun checkOrInitOpenCv() {
        if (isOpenCvInitialized())
         imageScanner.initDetection(originalBitmap, getScreenSize())
        else initOpenCv()
    }*/

    override fun populateBitmap(videoAnnotationData: VideoAnnotationData) {
        super.populateBitmap(videoAnnotationData)
        lifecycleScope.launch(Dispatchers.Main) {
            if (isBitmapInitialized().not()) return@launch
//            if (isLabel) checkOrInitOpenCv()
            binding.zoomView.setImageAssets(originalBitmap)
            setupJudgementButtons()
            binding.reloadIV.gone()
            binding.loaderIV.gone()
            questionnaireBinding.prevRecordContainer.invisible()
            val crops = if (isInterpolation && isInPreviewMode().not())
                videoAnnotationData.annotations.filter {
                    it.objectIndex == canvasViewModel.activeAnnotationId
                }.crops(originalBitmap)
            else videoAnnotationData.annotations.crops(originalBitmap)
            binding.cropView.apply {
                setBitmapAttributes(originalBitmap.width, originalBitmap.height)
                this.crops.clear()
                this.crops.addAll(crops)
                this.resetScale()

                Glide.with(context).load(videoAnnotationData.bitmap)
                    .into(this)
            }.invalidate()
            setAnnotationObjectiveAttributes(getCurrentAnnotation())
        }
    }

    private fun setupCropView(record: Record) {
        if (activity != null && this.isVisible) {
            binding.cropView.setBitmapAttributes(originalBitmap.width, originalBitmap.height)
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
        if (isBitmapInitialized())
            binding.cropView.addHintData(record.annotations().crops(originalBitmap))
    }

    override fun setupPreviousAnnotations(record: Record) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isBitmapInitialized()) {
                val crops = getAnnotationData(record).crops(originalBitmap)
                binding.cropView.apply {
                    this.crops.clear()
                    this.crops.addAll(crops)
                    setAnnotationObjectiveAttributes(getCurrentAnnotation())
                }.invalidate()
            }
        }
    }

    override fun getCurrentAnnotation(isSubmittingRecord: Boolean): List<AnnotationObjectsAttribute> {
        val allCropping = if (isBitmapInitialized()) {
            binding.cropView.getAnnotatedCrops(
                originalBitmap.width.toFloat(),
                originalBitmap.height.toFloat(),
                isSubmittingRecord
            )
        } else emptyList()
        Timber.tag("CropFragment").d(allCropping.joinToString { "[$it]" })
        return allCropping
    }

    private fun setupJudgementButtons() {
        if (activity != null && this.isVisible) {
            binding.cropView.apply {
                val isAllInputSet = this.crops.find { it.input.isNullOrEmpty() } == null
                val isAllCropsTagged = this.crops.all { it.tags.isNullOrEmpty().not() }
                val enableSubmit = if (isInput || isLabel) isAllInputSet
                else if (isMultiLabel) isAllCropsTagged
                else true
                if (isInPreviewMode() && enableSubmit) {
                    binding.bottomLayout.junkBtnContainer.disabled()
                    binding.bottomLayout.submitBtnContainer.enabled()
                    binding.bottomLayout.undoBtnContainer.enabled()
                } else {
                    if (this.crops.isNotEmpty() && enableSubmit) {
                        binding.bottomLayout.junkBtnContainer.disabled()
                        binding.bottomLayout.submitBtnContainer.enabled()
                        binding.bottomLayout.undoBtnContainer.enabled()
                    } else {
                        binding.bottomLayout.junkBtnContainer.enabled()
                        binding.bottomLayout.submitBtnContainer.disabled()
                        binding.bottomLayout.undoBtnContainer.disabled()
                    }
                }
            }
        }
    }

    private fun disableClassificationMode(isTemplateSelected: Boolean) {
        if (isTemplateSelected) {
            questionnaireBinding.doneSelectionContainer.gone()
        } else {
            questionnaireBinding.doneSelectionContainer.visible()
            if (canvasViewModel.user?.walkThroughEnabled == true)
                MaterialShowcaseView.Builder(requireActivity())
                    .setDismissOnTouch(false)
                    .setTargetTouchable(true)
                    .setDismissOnTargetTouch(true)
                    .setTarget(questionnaireBinding.doneTv)
                    .setGravity(Gravity.CENTER)
                    .setContentText("Click here to submit the violation for specific class.")
                    .singleUse("doneTv")
                    .setMaskColour(Color.parseColor("#CC000000"))
                    .build()
                    .show(requireActivity())
        }

        binding.templatesView.visible()
        binding.cropView.invalidate()
    }

    override fun updateContrast(colorFilter: ColorMatrixColorFilter) {
        binding.cropView.colorFilter = colorFilter
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
            binding.cropView.colorFilter = colorFilter

            if (canvasViewModel.user?.walkThroughEnabled == true)
                MaterialShowcaseView.Builder(requireActivity())
                    .setDismissOnTouch(true)
                    .setTargetTouchable(true)
                    .setDismissOnTargetTouch(true)
                    .setTarget(binding.rootContainer)
                    .setGravity(Gravity.CENTER)
                    .setDismissText("GOT IT")
                    .setContentText("Drag down to draw a box.")
                    .singleUse("rootContainer")
                    .setMaskColour(Color.parseColor("#CC000000"))
                    .build()
                    .show(requireActivity())
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.rootContainer, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun disabledJudgmentButtons() {
        binding.bottomLayout.submitBtnContainer.disabled()
        binding.bottomLayout.junkBtnContainer.disabled()
    }

    override fun setupContrastSlider(progress: Int) {
        binding.contrastSlider.progress = progress
    }

    override fun enableEditMode() {
        if (activity != null && isAdded) {
            questionnaireBinding.prevRecordContainer.gone()
            questionnaireBinding.aiAssistSwitch.gone()
            questionnaireBinding.recordIdTxt.invisible()
            questionnaireBinding.tvUserCategoryMedal.invisible()
            questionnaireBinding.questionContainer.invisible()
            binding.contrastSlider.gone()
            questionnaireBinding.sandboxProgress.gone()
            questionnaireBinding.overlayTransparentIv.gone()
            questionnaireBinding.contrastIv.unSelected()
            binding.rootContainer.selected()
            binding.bottomLayout.bottomButtonsContainer.selected()
            binding.backgroundContainer.upperBackgroundView.selected()
            questionnaireBinding.makeTransparentIv.gone()
            when {
                isInput -> {
                    binding.keyboardView.unSelected()
                    questionnaireBinding.inputSelectionContainer.visible()
                    questionnaireBinding.inputSelectionContainer.unSelected()
                }

                isLabel -> {
                    binding.templatesView.unSelected()
                    questionnaireBinding.doneSelectionContainer.gone()
                    questionnaireBinding.classifySelectionContainer.gone()
                    binding.cropView.isClassificationMode = true
                    binding.templatesView.visible()
                }

                isInterpolation -> {
                    binding.templatesView.unSelected()
                    questionnaireBinding.doneSelectionContainer.gone()
                    if (canvasViewModel.isInterpolatedMCML()) {
                        if (canvasViewModel.activeAnnotationId == null) {
                            questionnaireBinding.classifySelectionContainer.visible()
                            questionnaireBinding.classifySelectionContainer.unSelected()
                        } else questionnaireBinding.classifySelectionContainer.gone()
                    }
                }
            }
        }
    }

    override fun disableEditMode() {
        if (isImageProcessingEnabled()) questionnaireBinding.aiAssistSwitch.visible()
        else questionnaireBinding.aiAssistSwitch.gone()
        if (isSandbox().not() && showPreviousButton()) questionnaireBinding.prevRecordContainer.visible()
        else if (isAdminRole().not() && isMediaTypeImage()) {
            questionnaireBinding.sandboxProgress.visible()
        }
        questionnaireBinding.recordIdTxt.visible()
        questionnaireBinding.tvUserCategoryMedal.visible()
        questionnaireBinding.questionContainer.visible()
        binding.contrastSlider.gone()
        questionnaireBinding.overlayTransparentIv.visible()
        questionnaireBinding.contrastIv.unSelected()
        binding.rootContainer.unSelected()
        binding.bottomLayout.bottomButtonsContainer.unSelected()
        binding.backgroundContainer.upperBackgroundView.unSelected()
        binding.cropView.unSelect()

        when {
            isInput -> {
                questionnaireBinding.makeTransparentIv.visible()
                questionnaireBinding.overlayTransparentIv.gone()
                questionnaireBinding.inputSelectionContainer.gone()
                binding.keyboardView.gone()
                binding.cropView.apply {
                    isInLabelingMode = false
                    isClassificationMode = false
                }
            }

            isMultiLabel -> {
                questionnaireBinding.makeTransparentIv.visible()
            }

            isLabel || isInterpolation -> {
                questionnaireBinding.makeTransparentIv.visible()
                binding.cropView.apply {
                    isInLabelingMode = false
                    isClassificationMode = false
                }
                questionnaireBinding.classifySelectionContainer.gone()
                questionnaireBinding.doneSelectionContainer.gone()
                binding.templatesView.gone()
            }
        }
    }

    override fun enableUndo(isEnabled: Boolean) {
        if (isEnabled && isSandbox().not() && showPreviousButton())
            questionnaireBinding.prevRecordContainer.visible()
        questionnaireBinding.prevRecordContainer.isEnabled = isEnabled
    }

    override fun showHint() {
        binding.cropView.apply {
            showHint = true
        }.invalidate()
    }

    override fun hideHint() {
        binding.cropView.apply {
            showHint = false
        }.invalidate()
    }

    override fun showLabel() {
        binding.cropView.apply {
            showLabel = true
        }.invalidate()
    }

    override fun hideLabel() {
        binding.cropView.apply {
            showLabel = false
        }.invalidate()
    }

    override fun setupZoomView(params: ViewGroup.LayoutParams) {
        binding.zoomViewContainer.layoutParams = params
    }

    private fun isAllInputsSet(): Boolean {
        return binding.cropView.crops.find { it.input.isNullOrEmpty() } == null
    }

    override fun appendInput(value: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.cropView.apply {
                this.updateInputValue(value)
                this.selectNext()
            }.invalidate()

            setAnnotationObjectiveAttributes(getCurrentAnnotation())
            setupJudgementButtons()
        }
    }

    private val labelSelectionListener = object : LabelSelectionListener {
        override fun onSelect(template: Template) {
            if (isLabel) {
                template.isClicked = true
                binding.cropView.unSelect()
                canvasViewModel.setSelectedTemplate(template)
            } else {
                binding.cropView.apply {
                    this.updateInputValue(template.templateName)
                    this.selectNext()
                    labelAdapter?.updateAnnotations(this.crops)
                }.invalidate()
                setAnnotationObjectiveAttributes(getCurrentAnnotation())
                setupJudgementButtons()
            }
        }
    }

    override fun addTemplates(templates: List<Template>) {
        labelList = templates.sortedBy { it.templateName }
        when {
            isInterpolation -> {
                if (canvasViewModel.isInterpolatedMCMT()) {
                    setUpSuperButtons(labelList.superButtons())
                    setUpTagsList(labelList)
                    setupSelectedTags()
                } else setUpTemplates()
            }

            isLabel -> {
                setUpTemplates()
                canvasViewModel.setSelectedTemplate(null)
            }

            isMultiLabel -> {
                setUpSuperButtons(labelList.superButtons())
                setUpTagsList(labelList)
                setupSelectedTags()
            }
        }
    }

    private fun setUpTemplates() {
        binding.recyclerViewDictionary.apply {
            layoutManager = if (resources.configuration.orientation == ORIENTATION_LANDSCAPE)
                LinearLayoutManager(requireActivity())
            else LinearLayoutManager(requireActivity(), HORIZONTAL, false)
            setHasFixedSize(true)
            adapter = DictionaryAdapter(dictionaryList) { position ->
                val itemClicked = dictionaryList[position]
                val prefix = if (itemClicked.isSelected) itemClicked.alphabet else ""
                labelAdapter?.filterTemplates(prefix)
            }
        }

        // Labels Adapter
        labelAdapter = LabelsAdapter(labelSelectionListener)
        binding.recyclerViewTemplates.apply {
            layoutManager = if (resources.configuration.orientation == ORIENTATION_LANDSCAPE)
                LinearLayoutManager(requireActivity())
            else LinearLayoutManager(requireActivity(), HORIZONTAL, false)
            setHasFixedSize(true)
            adapter = labelAdapter
        }
        labelAdapter?.addAll(labelList)
    }

    override fun clearAIAssistAnnotations() {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.cropView.apply {
                val previousDrawnCrops = this.crops.filter { it.shouldRemove.not() }
                this.crops.clear()
                this.crops.addAll(previousDrawnCrops)

                setAnnotationObjectiveAttributes(getCurrentAnnotation())
                setupJudgementButtons()
            }.invalidate()
        }
    }

    override fun addBoundingBoxes(validObjects: ArrayList<Pair<List<RectF>, String?>>) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isVisible && isBitmapInitialized() && isAIAssistEnabled()) {
                validObjects.map { currentObject ->
                    val label = if (isLabel) currentObject.second else null
                    val aiAnnotatedCrops = currentObject.first.drawCrops(originalBitmap, label)
                    binding.cropView.addCropsIfNotExist(aiAnnotatedCrops)
                    label?.let { labelAdapter?.updateAnnotations(binding.cropView.crops) }
                }
                setAnnotationObjectiveAttributes(getCurrentAnnotation())
                binding.cropView.invalidate()
                setupJudgementButtons()
            }
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

    override fun onDetected(points: List<PointF>, screenSizeBitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isVisible) {
                val cropPoints = mutableListOf<CropPoint>()
                points.forEach {
                    val crop = CropPoint(it.x, it.y).apply {
                        transformWrtBitmap(screenSizeBitmap)
                    }
                    cropPoints.add(crop)
                }
                binding.cropView.addDetectedEdgePoints(cropPoints)
                binding.cropView.invalidate()
            }
        }
    }

    override fun updateCurrentStreak(streak: Int, maxStreak: Int) {
        questionnaireBinding.sandboxProgress.apply {
            max = maxStreak
            progress = streak
        }
    }

    private fun moveToMultiLabelScreen() {
        val selectedCropping = binding.cropView.selectedCropping
        selectedTags.clear()
        selectedTags.addAll(selectedCropping?.tags ?: emptyList())
        setSelectedTags()
        updateSuperButtons()
        updateTagsList()
        if (isBitmapInitialized()) {
            val extractedBitmap = originalBitmap.extractBitmap(selectedCropping)
            binding.multiTagsContainer?.extractedBitmapView?.setImageBitmap(extractedBitmap)
        }
        binding.multiTagsContainer?.multiTagsContainer?.visible()
    }

    private val selectedTagsAdapter = SelectedTagAdapter()

    private fun setupSelectedTags() {
        binding.multiTagsContainer?.selectedTagsLv?.adapter = selectedTagsAdapter
        binding.multiTagsContainer?.selectedTagsLv?.layoutManager = LinearLayoutManager(context)
        selectedTagsAdapter.notifyDataSetChanged()
    }

    private fun setSelectedTags() {
        binding.multiTagsContainer?.selectedTagsTitleTxt?.text = if (selectedTags.isEmpty())
            "No tag is selected" else getString(R.string.selected_tags)
        selectedTagsAdapter.addTags(selectedTags)
        selectedTagsAdapter.notifyDataSetChanged()
    }

    private val onTagsSelectedListener = object : OnTagsSelectedListener {
        override fun onSelected(tag: Template, selected: Boolean) {
            if (selected) selectedTags.add(tag.templateName)
            else selectedTags.remove(tag.templateName)
            setSelectedTags()
            updateSuperButtons()
        }
    }

    private val onTextChangeListener = object : TextChangeListener() {
        override fun afterTextChanged(s: Editable?) {
            tagsListAdapter.filter(s.toString())
        }
    }

    private val tagsListAdapter = TagsListAdapter(onTagsSelectedListener)

    private fun updateTagsList() {
        tagsListAdapter.update(selectedTags)
    }

    private fun setUpTagsList(templates: List<Template>) {
        val cluster = templates.cluster()
        tagsListAdapter.add(cluster)
        val clusterSize = cluster.size
        binding.multiTagsContainer?.tagsElv?.setAdapter(tagsListAdapter)
        for (i in 0 until clusterSize) {
            binding.multiTagsContainer?.tagsElv?.expandGroup(i)
        }
        binding.multiTagsContainer?.tagsElv?.deferNotifyDataSetChanged()
        binding.multiTagsContainer?.tagsFilterEt?.addTextChangedListener(onTextChangeListener)
    }

    private val onSuperButtonClickListener = object : OnSuperButtonClickListener {
        override fun onClicked(superButtons: List<Template>, tags: List<Template>) {
            selectedTags.removeAll { tags.map { tag -> tag.templateName }.contains(it) }
            selectedTags.addAll(superButtons.map { it.templateName })
            setSelectedTags()
            updateTagsList()
        }
    }

    private val superButtonsAdapter = SuperButtonsAdapter(onSuperButtonClickListener)

    private fun updateSuperButtons() {
        superButtonsAdapter.update(selectedTags)
        superButtonsAdapter.notifyDataSetChanged()
    }

    private fun setUpSuperButtons(templates: List<Template>) {
        binding.multiTagsContainer?.superButtonsView?.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = superButtonsAdapter
        }
        superButtonsAdapter.add(templates)
        superButtonsAdapter.notifyDataSetChanged()
    }

    companion object {
        const val MAX_CROPS = "maxCrops"
        const val IS_INPUT = "isInput"
        const val IS_LABEL = "isLabel"
        const val IS_MULTI_LABEL = "isMultiLabel"
        const val IS_INTERPOLATION = "isInterpolation"

        fun newInstance(
            maxCrops: Int,
            isInput: Boolean = false,
            isLabel: Boolean = false,
            isMultiLabel: Boolean = false,
            isInterpolation: Boolean = false
        ): CropFragment {
            val arguments = Bundle()
            arguments.putInt(MAX_CROPS, maxCrops)
            arguments.putBoolean(IS_INPUT, isInput)
            arguments.putBoolean(IS_LABEL, isLabel)
            arguments.putBoolean(IS_MULTI_LABEL, isMultiLabel)
            arguments.putBoolean(IS_INTERPOLATION, isInterpolation)
            val instance = CropFragment()
            instance.arguments = arguments
            return instance
        }
    }

    private fun setUpObservers() {
        canvasViewModel.template.observe(viewLifecycleOwner) {
            val isTemplateSelected = (it == null)
            labelAdapter?.updateAdapter(it)

            binding.cropView.selectedLabel = it
            if (binding.cropModeIv.visibility == View.VISIBLE)
                disableClassificationMode(isTemplateSelected)
        }
    }

    private fun setupWalkThrough() {
        if (canvasViewModel.user?.walkThroughEnabled == true) {
            try {
                val config = ShowcaseConfig()
                config.delay = 200
                config.renderOverNavigationBar = true
                val sequence = MaterialShowcaseSequence(
                    requireActivity(),
                    "CropFragment_InitialWalkThrough"
                )
                if (!sequence.hasFired()) {
                    sequence.setConfig(config)
                    sequence.addSequenceItem(
                        getShowCaseSeq(
                            binding.editModeIv,
                            "Click here to draw violation."
                        )
                    )
                    sequence.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getShowCaseSeq(
        target: View,
        text: String,
        delay: Int = 100,
        isFullScreen: Boolean = false
    ): MaterialShowcaseView {
        val materialShowcaseView = MaterialShowcaseView.Builder(requireActivity())
        if (isFullScreen) materialShowcaseView.setDismissText("GOT IT")
        materialShowcaseView.setTarget(target)
        materialShowcaseView.setTargetTouchable(!isFullScreen)
        materialShowcaseView.setDismissOnTargetTouch(!isFullScreen)
        materialShowcaseView.setDismissOnTouch(isFullScreen)
        materialShowcaseView.setGravity(Gravity.CENTER)
        materialShowcaseView.setContentText(text)
        materialShowcaseView.setSequence(true)
        materialShowcaseView.setDelay(delay)
        materialShowcaseView.setMaskColour(Color.parseColor("#CC000000"))
        return materialShowcaseView.build()
    }
}
