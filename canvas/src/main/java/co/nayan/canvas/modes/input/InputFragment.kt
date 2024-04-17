package co.nayan.canvas.modes.input

import android.annotation.SuppressLint
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationValue
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.utils.*
import co.nayan.canvas.AnnotationCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentInputBinding
import co.nayan.canvas.databinding.QuestionContainerBinding
import co.nayan.canvas.viewBinding
import co.nayan.canvas.views.getUserCategoryDrawable
import co.nayan.canvas.widgets.RecordInfoDialogFragment
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class InputFragment : AnnotationCanvasFragment(R.layout.fragment_input) {

    private var record: Record? = null
    private var isMultiInput = false
    private var userInput = ""
    private var drawType: String? = null
    private val binding by viewBinding(FragmentInputBinding::bind)
    private lateinit var questionnaireBinding: QuestionContainerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isMultiInput = it.getBoolean(IS_MULTI_INPUT, false)
        }
    }

    override fun resetViews(applicationMode: String?, correctAnnotationList: List<AnnotationData>) {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val questionnaireLayout = binding.root.findViewById<ConstraintLayout>(R.id.questionLayout)
        questionnaireBinding = DataBindingUtil.bind(questionnaireLayout)!!

        setupViews()
        setupClicks()
        if (canvasViewModel.isSandbox() && binding.helpBtnContainer.isVisible &&
            canvasViewModel.shouldPlayHelpVideo(canvasViewModel.applicationMode)
        ) binding.helpBtnContainer.performClick()
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
        binding.speakerContainer.gone()
        binding.keyboardView.visible()
        setupSpeechRecognizer()
    }

    override fun setupCanvasView() {
        if (showPreviousButton()) questionnaireBinding.prevRecordContainer.invisible()
        else questionnaireBinding.prevRecordContainer.visible()
    }

    override fun setupSandboxView(shouldEnableHintBtn: Boolean) {
        questionnaireBinding.prevRecordContainer.invisible()
        if (isAdminRole().not() && isMediaTypeImage()) {
            questionnaireBinding.sandboxProgress.visible()
        }
    }

    override fun setupHelpButton(applicationMode: String?) {
        if (applicationMode.isNullOrEmpty()) binding.helpBtnContainer.disabled()
        else binding.helpBtnContainer.enabled()
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

    private val micClickListener = View.OnClickListener {
        if (it.isSelected) {
            binding.keyboardView.visible()
            binding.speakerContainer.invisible()
            it.unSelected()
            stopListening()
        } else {
            binding.keyboardView.invisible()
            binding.speakerContainer.visible()
            it.selected()
            checkAudioPermission()
        }
    }

    private fun setupClicks() {
        binding.keyboardView.setKeyboardActionListener(keyboardActionListener)
        binding.junkBtnContainer.setOnClickListener { showJunkRecordDialog() }
        binding.submitBtnContainer.setOnClickListener { submitAnnotation(getCurrentAnnotation(true)) }
        questionnaireBinding.textToSpeechIv.setOnClickListener { speakOut(questionnaireBinding.questionTxt.text.toString()) }
        questionnaireBinding.prevRecordContainer.setOnClickListener {
            if (canvasViewModel.isSubmittingRecords)
                return@setOnClickListener
            undoAnnotation()
        }
        questionnaireBinding.contrastIv.setOnClickListener(contrastClickListener)
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangedListener)
        questionnaireBinding.backIv.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.reloadIV.setOnClickListener { reloadRecord() }
        binding.micIv.setOnClickListener(micClickListener)
        binding.micStateIv.setOnClickListener { checkAudioPermission() }
        binding.backspaceIv.setOnClickListener { deleteLastInput() }
        binding.helpBtnContainer.setOnClickListener { moveToLearningVideoScreen() }
        binding.sniffingBtnContainer.setOnClickListener(sniffingClickListener)
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

    @SuppressLint("ClickableViewAccessibility")
    private val infoTouchListener = View.OnTouchListener { _, event ->
        val drawableRight = 2
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (event.rawX >= questionnaireBinding.recordIdTxt.right - questionnaireBinding.recordIdTxt.compoundDrawables[drawableRight].bounds.width()) {
                record?.let { setupRecordInfoDialog(it) }
            }
        }
        true
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

    override fun appendInput(value: String) {
        value.forEach {
            if (isMultiInput && userInput.length < MAX_INPUT_LENGTH)
                userInput += it
            else userInput = it.toString()
            binding.answerEt.setText(userInput)
            setupJudgementButtons()
        }
    }

    override fun deleteLastInput() {
        if (isMultiInput) {
            if (userInput.isNotEmpty()) {
                userInput = userInput.substring(0, userInput.length - 1)
            }
        } else {
            userInput = ""
        }
        binding.answerEt.setText(userInput)
        setupJudgementButtons()
    }

    private fun hideAllPhotoViews() {
        binding.photoView.gone()
        binding.cropView.gone()
        binding.polygonView.gone()
        binding.quadrilateralView.gone()
        binding.dragSplitView.gone()
        binding.paintView.gone()
    }

    private fun resetViews() {
        hideAllPhotoViews()
        binding.reloadIV.gone()
        binding.loaderIV.visible()

        drawType.view().visible()
        drawType.view().setImageBitmap(null)

        userInput = ""
        binding.answerEt.setText(userInput)
        setupJudgementButtons()
    }

    override fun populateRecord(record: Record) {
        this.record = record
        drawType = record.drawType()
        resetViews()
        setupSniffingView(record.isSniffingRecord)
        setupHintData(record)
        questionnaireBinding.recordIdTxt.text = getRecordIdText(record)
        lifecycleScope.launch {
            loadImage(drawType.view(), record.displayImage)
            drawAnnotations(record.annotations())
        }
        setupInputView(record)
        setupJudgementButtons()
    }

    override fun addHintData(record: Record) {
        sniffingAnswer = record.answer()
    }

    private fun setupInputView(record: Record) {
        if (isSandbox()) {
            userInput = if (isAdminRole()) record.annotation.answer() else ""
            binding.answerEt.setText(userInput)
        }
    }

    override fun drawAnnotations(annotations: List<AnnotationData>) {
        if (annotations.isNotEmpty()) drawResults(annotations)
    }

    private fun drawResults(annotations: List<AnnotationData>) {
        if (isBitmapInitialized()) {
            when (drawType) {
                DrawType.BOUNDING_BOX -> {
                    binding.cropView.reset()
                    binding.cropView.editMode(isEnabled = false)
                    binding.cropView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.cropView.crops.addAll(annotations.crops(originalBitmap))
                    binding.cropView.invalidate()
                }

                DrawType.QUADRILATERAL -> {
                    binding.quadrilateralView.reset()
                    binding.quadrilateralView.editMode(isEnabled = false)
                    binding.quadrilateralView.quadrilaterals.addAll(
                        annotations.quadrilaterals(originalBitmap)
                    )
                    binding.quadrilateralView.invalidate()
                }

                DrawType.POLYGON -> {
                    binding.polygonView.reset()
                    binding.polygonView.editMode(isEnabled = false)
                    binding.polygonView.points.addAll(annotations.polygonPoints(originalBitmap))
                    binding.polygonView.invalidate()
                }

                DrawType.CONNECTED_LINE -> {
                    binding.paintView.reset()
                    binding.paintView.editMode(isEnabled = false)
                    binding.paintView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.paintView.paintDataList.addAll(annotations.paintDataList(bitmap = originalBitmap))
                    binding.paintView.invalidate()
                }

                DrawType.SPLIT_BOX -> {
                    binding.dragSplitView.reset()
                    binding.dragSplitView.editMode(isEnabled = false)
                    binding.dragSplitView.setBitmapAttributes(
                        originalBitmap.width,
                        originalBitmap.height
                    )
                    binding.dragSplitView.splitCropping.addAll(annotations.splitCrops(originalBitmap))
                    binding.dragSplitView.invalidate()
                }
            }
        }
    }

    override fun updateContrast(colorFilter: ColorMatrixColorFilter) {
        drawType.view().colorFilter = colorFilter
    }

    override suspend fun onBitmapLoadFailed() {
        withContext(Dispatchers.Main) {
            drawType.view().gone()
            binding.reloadIV.visible()
            binding.loaderIV.gone()
        }
    }

    override suspend fun onBitmapReady(colorFilter: ColorMatrixColorFilter, contrastProgress: Int) {
        withContext(Dispatchers.Main) {
            binding.reloadIV.gone()
            binding.loaderIV.gone()
            binding.contrastSlider.progress = contrastProgress
            drawType.view().colorFilter = colorFilter
        }
    }

    override fun getCurrentAnnotation(isSubmittingRecord: Boolean): List<AnnotationObjectsAttribute> {
        return listOf(AnnotationObjectsAttribute(AnnotationValue(answer = userInput)))
    }

    private fun setupJudgementButtons() {
        if (activity != null && this.isVisible) {
            if (userInput.isEmpty()) {
                binding.junkBtnContainer.enabled()
                binding.submitBtnContainer.disabled()
            } else {
                binding.junkBtnContainer.disabled()
                binding.submitBtnContainer.enabled()
            }
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

    override fun enabledJudgmentButtons() {}

    override fun disabledJudgmentButtons() {
        binding.submitBtnContainer.disabled()
        binding.junkBtnContainer.disabled()
    }

    override fun setupContrastSlider(progress: Int) {
        binding.contrastSlider.progress = progress
    }

    override fun enableEditMode() {
        binding.rootContainer.selected()
        binding.bottomButtonsContainer.selected()
        binding.backgroundContainer.upperBackgroundView.selected()
        questionnaireBinding.contrastIv.unSelected()
    }

    override fun endOfSpeech() {
        stopListening()
        binding.micAv.gone()
        binding.micStateIv.visible()
        binding.micStateIv.enabled()
        binding.speakNowTxt.gone()
        binding.tapToSpeakTxt.visible()
    }

    override fun readyForSpeech() {
        binding.micAv.visible()
        binding.micStateIv.gone()
        binding.micStateIv.disabled()
        binding.speakNowTxt.visible()
        binding.tapToSpeakTxt.gone()
    }

    override fun audioPermissionDenied() {
        binding.micIv.performClick()
        showMessage(getString(R.string.recording_audio_permission_is_denied))
    }

    override fun showMessage(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupSniffingView(isSniffing: Boolean?) {
        if (isSniffing == true && isQAEnvironment()) {
            binding.sniffingBtnContainer.visible()
            binding.helpBtnContainer.gone()
        } else {
            binding.sniffingBtnContainer.gone()
            binding.helpBtnContainer.visible()
        }
    }

    override fun updateCurrentStreak(streak: Int, maxStreak: Int) {
        questionnaireBinding.sandboxProgress.apply {
            max = maxStreak
            progress = streak
        }
    }

    companion object {
        const val MAX_INPUT_LENGTH = 15
        const val IS_MULTI_INPUT: String = "IS_MULTI_INPUT"

        fun newInstance(isMultiInput: Boolean): InputFragment {
            val arguments = Bundle()
            arguments.putBoolean(IS_MULTI_INPUT, isMultiInput)
            val newInstance = InputFragment()
            newInstance.arguments = arguments
            return newInstance
        }
    }
}