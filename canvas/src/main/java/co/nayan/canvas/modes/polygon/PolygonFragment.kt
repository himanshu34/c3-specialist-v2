package co.nayan.canvas.modes.polygon

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
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.VideoAnnotationData
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.polygon.PolygonInteractionInterface
import co.nayan.c3views.utils.annotations
import co.nayan.c3views.utils.polygonPoints
import co.nayan.canvas.AnnotationCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentPolygonBinding
import co.nayan.canvas.databinding.QuestionContainerBinding
import co.nayan.canvas.viewBinding
import co.nayan.canvas.views.getUserCategoryDrawable
import co.nayan.canvas.widgets.RecordInfoDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class PolygonFragment : AnnotationCanvasFragment(R.layout.fragment_polygon) {

    private var record: Record? = null
    private val binding by viewBinding(FragmentPolygonBinding::bind)
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
        binding.polygonView.setPolygonInteractionInterface(onPolygonInteractionInterface)
        binding.polygonView.setZoomView(binding.zoomView)
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
        if (applicationMode.isNullOrEmpty()) binding.bottomLayout.helpBtnContainer.disabled()
        else binding.bottomLayout.helpBtnContainer.enabled()
    }

    private val submitClickListener = View.OnClickListener {
        if (isValidAnnotation()) submitAnnotation(getCurrentAnnotation(true))
        else showMessage(getString(R.string.not_a_proper_polygon))
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
        if (binding.polygonModeIv.visibility == View.INVISIBLE) {
            binding.polygonModeIv.visible()
            binding.polygonModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()

            binding.polygonView.editMode(isEnabled = true)
            enableEditMode()
        }
    }

    private val polyModeClickListener = View.OnClickListener {
        if (binding.editModeIv.visibility == View.INVISIBLE) {
            binding.editModeIv.visible()
            binding.editModeIv.rightSwipeVisibleAnimation()
            it.rightSwipeInvisibleAnimation()
            it.invisible()

            binding.polygonView.editMode(isEnabled = false)
            disableEditMode()
        }
    }

    private fun setupClicks() {
        binding.bottomLayout.undoBtnContainer.setOnClickListener { binding.polygonView.undo() }
        binding.bottomLayout.junkBtnContainer.setOnClickListener { showJunkRecordDialog() }
        binding.bottomLayout.submitBtnContainer.setOnClickListener(submitClickListener)
        questionnaireBinding.textToSpeechIv.setOnClickListener { speakOut(questionnaireBinding.questionTxt.text.toString()) }
        questionnaireBinding.contrastIv.setOnClickListener(contrastClickListener)
        binding.editModeIv.setOnClickListener(editClickListener)
        binding.polygonModeIv.setOnClickListener(polyModeClickListener)
        binding.reloadIV.setOnClickListener { reloadRecord() }
        questionnaireBinding.prevRecordContainer.setOnClickListener {
            if (canvasViewModel.isSubmittingRecords)
                return@setOnClickListener
            undoAnnotation()
        }
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

    private val onPolygonInteractionInterface = object : PolygonInteractionInterface {
        override fun onDraw() {
            setupJudgmentButtons()
            setupControlButtons()
        }

        override fun setUpZoomView(isShowing: Boolean, x: Float, y: Float) {
            if (isShowing) {
                setupZoomViewPosition(x, y)
                binding.zoomViewContainer.visible()
            } else {
                binding.zoomViewContainer.invisible()
            }
        }

        override fun updatePoints() {
            setAnnotationObjectiveAttributes(getCurrentAnnotation())
        }
    }

    private fun resetViews() {
        binding.polygonModeIv.performClick()
        binding.polygonView.reset()
        binding.polygonView.invalidate()
        binding.polygonView.resetScale()
        binding.polygonView.setImageBitmap(null)

        binding.reloadIV.gone()
        binding.loaderIV.visible()
    }

    override fun populateRecord(record: Record) {
        this.record = record
        resetViews()
        questionnaireBinding.recordIdTxt.text = getRecordIdText(record)
        lifecycleScope.launch {
            loadBitmap(binding.polygonView, record.displayImage)
            withContext(Dispatchers.Main) {
                if (isBitmapInitialized()) setupPolygonView(record)
            }
        }
        setupJudgmentButtons()
    }

    override fun populateBitmap(videoAnnotationData: VideoAnnotationData) {
        super.populateBitmap(videoAnnotationData)
        if (isBitmapInitialized().not()) return
        binding.zoomView.setImageAssets(originalBitmap)
        setupJudgmentButtons()
        binding.reloadIV.gone()
        binding.loaderIV.gone()
        questionnaireBinding.prevRecordContainer.invisible()
        val polygonPoints = videoAnnotationData.annotations.polygonPoints(originalBitmap)
        binding.polygonView.points.clear()
        binding.polygonView.points.addAll(polygonPoints)
        binding.polygonView.invalidate()
        setAnnotationObjectiveAttributes(getCurrentAnnotation())
        binding.polygonView.setImageBitmap(videoAnnotationData.bitmap)
    }


    private fun setupPolygonView(record: Record) {
        if (activity != null && this.isVisible) {
            setupPreviousAnnotations(record)
            setupHintData(record)
            setupSniffingView(record.isSniffingRecord)
            binding.zoomView.setImageAssets(originalBitmap)
        }
    }

    override fun addHintData(record: Record) {
        binding.polygonView.addHintData(record.annotations().polygonPoints(originalBitmap))
    }

    override fun setupPreviousAnnotations(record: Record) {
        val points = getAnnotationData(record).polygonPoints(originalBitmap)
        binding.polygonView.points.clear()
        binding.polygonView.points.addAll(points)
        binding.polygonView.invalidate()
        setAnnotationObjectiveAttributes(getCurrentAnnotation())
    }

    override fun getCurrentAnnotation(isSubmittingRecord: Boolean): List<AnnotationObjectsAttribute> {
        val polygon = if (isBitmapInitialized()) {
            binding.polygonView.getPolygon(
                originalBitmap.width.toFloat(),
                originalBitmap.height.toFloat()
            )
        } else emptyList()
        Timber.d(polygon.joinToString { "[$it]" })
        return polygon
    }


    override fun updateContrast(colorFilter: ColorMatrixColorFilter) {
        binding.polygonView.colorFilter = colorFilter
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
            binding.polygonView.colorFilter = colorFilter
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

    private fun setupControlButtons() {
        if (binding.polygonView.points.isNotEmpty())
            binding.bottomLayout.undoBtnContainer.enabled()
        else binding.bottomLayout.undoBtnContainer.disabled()
    }

    override fun setupContrastSlider(progress: Int) {
        binding.contrastSlider.progress = progress
    }

    private fun setupJudgmentButtons() {
        if (activity != null && this.isVisible) {
            if (binding.polygonView.points.size > 3) {
                binding.bottomLayout.submitBtnContainer.enabled()
                binding.bottomLayout.junkBtnContainer.disabled()
            } else {
                binding.bottomLayout.submitBtnContainer.disabled()
                binding.bottomLayout.junkBtnContainer.enabled()
            }
        }
    }

    override fun enableUndo(isEnabled: Boolean) {
        questionnaireBinding.prevRecordContainer.isEnabled = isEnabled
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
        questionnaireBinding.contrastIv.unSelected()
        binding.rootContainer.unSelected()
        binding.bottomLayout.bottomButtonsContainer.unSelected()
        binding.backgroundContainer.upperBackgroundView.unSelected()
    }

    override fun showHint() {
        binding.polygonView.showHint = true
        binding.polygonView.invalidate()
    }

    override fun hideHint() {
        binding.polygonView.showHint = false
        binding.polygonView.invalidate()
    }

    override fun setupZoomView(params: ViewGroup.LayoutParams) {
        binding.zoomViewContainer.layoutParams = params
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.polygonView, message, Snackbar.LENGTH_SHORT).show()
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