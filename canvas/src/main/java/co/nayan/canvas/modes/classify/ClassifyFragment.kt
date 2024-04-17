package co.nayan.canvas.modes.classify

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.Mode.DYNAMIC_CLASSIFY
import co.nayan.c3v2.core.config.Mode.EVENT_VALIDATION
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.utils.*
import co.nayan.c3views.utils.*
import co.nayan.canvas.AnnotationCanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentClassifyBinding
import co.nayan.canvas.databinding.QuestionContainerBinding
import co.nayan.canvas.searchdialog.SearchDialogCompat
import co.nayan.canvas.utils.betterSmoothScrollToPosition
import co.nayan.canvas.utils.hideKeyBoard
import co.nayan.canvas.utils.isVideo
import co.nayan.canvas.viewBinding
import co.nayan.canvas.viewmodels.BaseCanvasViewModel
import co.nayan.canvas.views.getUserCategoryDrawable
import co.nayan.canvas.views.videoplayer.*
import co.nayan.canvas.widgets.RecordInfoDialogFragment
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig

@AndroidEntryPoint
class ClassifyFragment : AnnotationCanvasFragment(R.layout.fragment_classify) {

    private var selectedTemplate: Template? = null
    private var drawType: String? = null
    private val binding by viewBinding(FragmentClassifyBinding::bind)
    private lateinit var questionnaireBinding: QuestionContainerBinding
    private lateinit var templates: List<Template>
    private lateinit var templateAdapter: TemplateAdapter
    private var record: Record? = null
    private var exoplayerController: ExoplayerController? = null

    override fun resetViews(applicationMode: String?, correctAnnotationList: List<AnnotationData>) {

    }

    override fun onStop() {
        exoplayerController?.onStop()
        super.onStop()
    }

    override fun onStart() {
        exoplayerController?.onStart()
        super.onStart()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val questionnaireLayout = binding.root.findViewById<ConstraintLayout>(R.id.questionLayout)
        questionnaireBinding = DataBindingUtil.bind(questionnaireLayout)!!

        setupViews()
        setupViewModel()
        setupClicks()
        setupWalkThrough()
        setupExoPlayer()
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
        templates = listOf()
        templateAdapter = TemplateAdapter { template ->
            canvasViewModel.setSelectedTemplate(template)
        }
        binding.rvTemplateList.apply {
            setHasFixedSize(true)
            adapter = templateAdapter
        }
        if (canvasViewModel.applicationMode == DYNAMIC_CLASSIFY) binding.parentSearch.visible()
        else binding.parentSearch.gone()
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

    override fun enableUndo(isEnabled: Boolean) {
        questionnaireBinding.prevRecordContainer.isEnabled = isEnabled
    }

    override fun setupHelpButton(applicationMode: String?) {
        if (applicationMode.isNullOrEmpty()) binding.helpBtnContainer.disabled()
        else binding.helpBtnContainer.enabled()
    }

    private fun setupViewModel() {
        canvasViewModel.template.observe(viewLifecycleOwner) {
            val isTemplateSelected = (it == null)
            templateAdapter.selectedPosition = if (isTemplateSelected) RecyclerView.NO_POSITION
            else templates.indexOf(it)
            templateAdapter.notifyDataSetChanged()
            postDelayed(200) {
                if (templateAdapter.selectedPosition != RecyclerView.NO_POSITION)
                    binding.rvTemplateList.betterSmoothScrollToPosition(templateAdapter.selectedPosition)
            }

            if (it == null) disableEditMode()
            else enableEditMode()
            selectedTemplate = it
        }

        canvasViewModel.templateState.observe(viewLifecycleOwner, stateObserver)
        if (canvasViewModel.applicationMode.equals(EVENT_VALIDATION, ignoreCase = true))
            canvasViewModel.fetchStaticTemplates()
        else canvasViewModel.fetchTemplates()
    }

    private val stateObserver = Observer<ActivityState> {
        when (it) {
            ProgressState -> binding.rvTemplateList.gone()
            is ErrorState -> {
                binding.loaderIV.gone()
                binding.rvTemplateList.gone()
                showMessage(getString(R.string.something_went_wrong))
            }

            is BaseCanvasViewModel.TemplatesFailedState -> {
                binding.rvTemplateList.gone()
                showMessage(it.message ?: getString(R.string.something_went_wrong))
            }

            is BaseCanvasViewModel.TemplatesSuccessState -> {
                binding.rvTemplateList.visible()
                templates = if (canvasViewModel.applicationMode == DYNAMIC_CLASSIFY) {
                    getRecentSearchedTemplates(it.templates.toMutableList())
                } else it.templates
                templateAdapter.addAll(templates)

                val answer = if (canvasViewModel.currentRole() == Role.ADMIN)
                    record?.answer(canvasViewModel.isSandbox()) else null
                val label = if (it.addedLabel.isNullOrEmpty().not()) it.addedLabel
                else if (answer.isNullOrEmpty().not()) answer
                else null
                if (label.isNullOrEmpty().not()) {
                    templates.find { t ->
                        t.templateName.equals(label, ignoreCase = true)
                    }?.let { template ->
                        canvasViewModel.setSelectedTemplate(template)
                    }
                }
            }
        }
    }

    private fun getRecentSearchedTemplates(templates: MutableList<Template>): List<Template> {
        val sortedTemplates = templates
        val cachedTemplates = canvasViewModel.getRecentSearchedTemplate()
        cachedTemplates.forEachIndexed { index, template ->
            sortedTemplates.find { it.id == template.id }?.let {
                sortedTemplates.remove(it)
            } ?: run { cachedTemplates.removeAt(index) }
        }

        sortedTemplates.addAll(0, cachedTemplates)
        return sortedTemplates
    }

    private val junkBtnClickListener = View.OnClickListener {
        canvasViewModel.setSelectedTemplate(null)
        showJunkRecordDialog()
    }

    private val submitClickListener = View.OnClickListener {
        if (validTemplate()) {
            saveRecentSearchedTemplate(selectedTemplate)
            submitAnnotation(getCurrentAnnotation(true))
            canvasViewModel.setSelectedTemplate(null)
        } else showMessage(getString(R.string.please_select_template_first))
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

    private fun setupClicks() {
        binding.junkBtnContainer.setOnClickListener(junkBtnClickListener)
        questionnaireBinding.prevRecordContainer.setOnClickListener {
            if (canvasViewModel.isSubmittingRecords)
                return@setOnClickListener
            undoAnnotation()
        }
        binding.submitBtnContainer.setOnClickListener(submitClickListener)
        questionnaireBinding.textToSpeechIv.setOnClickListener {
            speakOut(questionnaireBinding.questionTxt.text.toString())
        }
        questionnaireBinding.contrastIv.setOnClickListener(contrastClickListener)
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangedListener)
        binding.reloadIV.setOnClickListener { reloadRecord() }
        questionnaireBinding.backIv.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.helpBtnContainer.setOnClickListener { moveToLearningVideoScreen() }
        binding.sniffingBtnContainer.setOnClickListener(sniffingClickListener)
        binding.parentSearch.setOnClickListener(searchClickListener)
        binding.etSearch.setOnClickListener(searchClickListener)
        binding.rvTemplateList.addOnScrollListener(onScrollChangeListener)
        binding.exoPlayerView.findViewById<ImageButton>(R.id.ivPlayPause).setOnClickListener {
            exoplayerController?.togglePlayback()
        }
        binding.reloadMediaIV.setOnClickListener { reloadMedia() }
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

    override fun showMessage(message: String) {
        Snackbar.make(binding.rootContainer, message, Snackbar.LENGTH_SHORT).show()
    }

    private val onScrollChangeListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (dy > 50 && canvasViewModel.user?.walkThroughEnabled == true) {
                MaterialShowcaseView.Builder(requireActivity())
                    .setDismissOnTouch(true)
                    .setTargetTouchable(true)
                    .setDismissOnTargetTouch(true)
                    .setTarget(binding.rvTemplateList)
                    .setGravity(Gravity.CENTER)
                    .setDismissText("GOT IT")
                    .setContentText("Click on one of the right answer.")
                    .singleUse("onScrolled")
                    .setMaskColour(Color.parseColor("#CC000000"))
                    .build()
                    .show(requireActivity())
            }
        }
    }

    private fun hideAllPhotoViews() {
        binding.photoView.gone()
        binding.cropView.gone()
        binding.polygonView.gone()
        binding.quadrilateralView.gone()
        binding.paintView.gone()
        binding.dragSplitView.gone()
    }

    private fun resetViews() {
        disableEditMode()
        hideAllPhotoViews()
        binding.exoPlayerView.gone()
        binding.reloadMediaIV.gone()
        selectedTemplate = null

        drawType.view().visible()
        drawType.view().setImageBitmap(null)

        binding.reloadIV.gone()
        binding.loaderIV.visible()
        if (canvasViewModel.applicationMode == DYNAMIC_CLASSIFY && templates.isNullOrEmpty()
                .not()
        ) {
            templates = getRecentSearchedTemplates(templates.toMutableList())
            templateAdapter.addAll(templates)
        }
    }

    override fun populateRecord(record: Record) {
        this.record = record
        drawType = record.drawType()
        resetViews()
        setupSniffingView(record.isSniffingRecord)
        setupHintData(record)
        questionnaireBinding.recordIdTxt.text = getRecordIdText(record)

        lifecycleScope.launch {
            val url = record.displayImage ?: record.mediaUrl
            url?.let { mediaUrl ->
                if (mediaUrl.isVideo()) loadVideo(mediaUrl)
                else {
                    loadImage(drawType.view(), mediaUrl)
                    drawAnnotations(record.annotations())
                }
            }
        }
    }

    override fun addHintData(record: Record) {
        sniffingAnswer = record.answer()
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
            binding.imageContainer.visible()
            binding.exoPlayerView.gone()
            binding.reloadMediaIV.gone()
            binding.reloadIV.visible()
            binding.loaderIV.gone()
        }
    }

    override suspend fun onBitmapReady(colorFilter: ColorMatrixColorFilter, contrastProgress: Int) {
        withContext(Dispatchers.Main) {
            binding.imageContainer.visible()
            binding.exoPlayerView.gone()
            binding.reloadMediaIV.gone()
            binding.reloadIV.gone()
            binding.loaderIV.gone()
            binding.contrastSlider.progress = contrastProgress
            drawType.view().colorFilter = colorFilter
        }
    }

    override suspend fun onVideoReady(
        contrastProgress: Int,
        mediaUrl: String
    ) {
        withContext(Dispatchers.Main) {
            binding.imageContainer.gone()
            binding.exoPlayerView.visible()
            binding.contrastSlider.progress = contrastProgress
            try {
                exoplayerController?.initializePlayer(mediaUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun reloadMedia() {
        record?.let {
            exoplayerController?.reloadPlayer(it.mediaUrl)
            binding.reloadMediaIV.gone()
        }
    }

    private fun setupExoPlayer() = lifecycleScope.launch {
        requireContext().let {
            binding.exoPlayerView.setZoomableViewInteractor(zoomableViewInteractor)
            exoplayerController = ExoplayerController(it, binding.exoPlayerView)
            requireActivity().lifecycle.addObserver(exoplayerController!!)
            exoplayerController?.exoPlaybackState?.observe(
                viewLifecycleOwner, exoPlaybackStateObserver
            )
        }
        setupExoPlayerControls()
    }

    private val zoomableViewInteractor = object : ZoomableViewInteractor {
        override fun onTapped() {
            binding.exoPlayerView.findViewById<ConstraintLayout>(R.id.exoPlayerControlButtonsView)
                .visible()
            exoplayerController?.togglePlayback()
        }
    }

    private val exoPlaybackStateObserver = Observer<ExoPlayerPlayBackState> {
        when (it) {
            is OnMediaPlaybackReady -> {
                exoplayerController?.startPlayback()
            }

            is OnMediaPlaybackStopped -> {
                binding.exoPlayerView.findViewById<ImageButton>(R.id.ivPlayPause).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_play))
                }
            }

            is OnMediaPlaybackStart -> {
                binding.exoPlayerView.findViewById<ImageButton>(R.id.ivPlayPause).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_pause))
                }
            }

            is OnMediaPlaybackError -> {
                binding.reloadMediaIV.visible()
            }
        }
    }

    private fun setupExoPlayerControls() = lifecycleScope.launch {
        binding.exoPlayerView.findViewById<ImageButton>(R.id.ivForward).setOnClickListener {
            exoplayerController?.forwardBy(500)
            binding.exoPlayerView.findViewById<ConstraintLayout>(R.id.exoPlayerControlButtonsView)
                .visible()
        }

        binding.exoPlayerView.findViewById<ImageButton>(R.id.ivRewind).setOnClickListener {
            exoplayerController?.rewindBy(500)
            binding.exoPlayerView.findViewById<ConstraintLayout>(R.id.exoPlayerControlButtonsView)
                .visible()
        }
    }

    private fun validTemplate() = selectedTemplate != null

    override fun getCurrentAnnotation(isSubmittingRecord: Boolean): List<AnnotationObjectsAttribute> {
        val templateName = selectedTemplate?.templateName?.trim()
        return listOf(AnnotationObjectsAttribute(AnnotationValue(answer = templateName)))
    }

    override fun enabledJudgmentButtons() {
        binding.junkBtnContainer.enabled()
    }

    override fun disabledJudgmentButtons() {
        binding.submitBtnContainer.disabled()
        binding.junkBtnContainer.disabled()
    }

    override fun setupContrastSlider(progress: Int) {
        binding.contrastSlider.progress = progress
    }

    private val searchClickListener = View.OnClickListener {
        if (templates.isNullOrEmpty()) return@OnClickListener

        childFragmentManager.showDialogFragment(
            SearchDialogCompat(canvasViewModel, templates) { selection ->
                hideKeyBoard()
                selection?.let { canvasViewModel.setSelectedTemplate(it) }
            })
    }

    override fun enableEditMode() {
        binding.rootContainer.selected()
        binding.bottomButtonsContainer.selected()
        binding.backgroundContainer.upperBackgroundView.selected()
        questionnaireBinding.prevRecordContainer.invisible()
        questionnaireBinding.contrastIv.unSelected()
        binding.contrastSlider.gone()
        questionnaireBinding.recordIdTxt.gone()
        questionnaireBinding.tvUserCategoryMedal.gone()
        binding.submitBtnContainer.enabled()
    }

    override fun disableEditMode() {
        if (isSandbox().not() && showPreviousButton())
            questionnaireBinding.prevRecordContainer.visible()
        binding.rootContainer.unSelected()
        binding.bottomButtonsContainer.unSelected()
        binding.backgroundContainer.upperBackgroundView.unSelected()
        questionnaireBinding.contrastIv.unSelected()
        binding.contrastSlider.gone()
        questionnaireBinding.recordIdTxt.visible()
        questionnaireBinding.tvUserCategoryMedal.visible()
        binding.submitBtnContainer.disabled()
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

    override fun updateCurrentStreak(streak: Int, maxStreak: Int) {
        questionnaireBinding.sandboxProgress.apply {
            max = maxStreak
            progress = streak
        }
    }

    private fun setupWalkThrough() {
        if (canvasViewModel.user?.walkThroughEnabled == true) {
            val config = ShowcaseConfig()
            config.delay = 1000
            config.renderOverNavigationBar = true

            val sequence =
                MaterialShowcaseSequence(
                    requireActivity(),
                    "ClassifyFragment_InitialWalkThrough"
                )

            if (!sequence.hasFired()) {
                sequence.setConfig(config)
                sequence.addSequenceItem(
                    getShowCaseSeq(
                        binding.helpBtnContainer,
                        "Click here for help.",
                        allowTargetTaps = false
                    )
                )
                sequence.addSequenceItem(
                    getShowCaseSeq(
                        binding.junkBtnContainer,
                        "Click here to mark as junk.",
                        allowTargetTaps = false
                    )
                )
                sequence.addSequenceItem(
                    getShowCaseSeq(
                        binding.submitIv,
                        "Click here to submit.",
                        allowTargetTaps = false
                    )
                )
                sequence.start()
            }
        }
    }

    private fun getShowCaseSeq(
        target: View,
        text: String,
        delay: Int = 100,
        allowTargetTaps: Boolean = true,
        withRectangleShape: Boolean = false
    ): MaterialShowcaseView {
        val materialShowcaseView = MaterialShowcaseView.Builder(requireActivity())
        materialShowcaseView.setTarget(target)
        materialShowcaseView.setTargetTouchable(allowTargetTaps)
        materialShowcaseView.setDismissOnTargetTouch(allowTargetTaps)
        materialShowcaseView.setDismissOnTouch(!allowTargetTaps)
        materialShowcaseView.setGravity(Gravity.CENTER)
        materialShowcaseView.setContentText(text)
        materialShowcaseView.setSequence(true)
        materialShowcaseView.setDelay(delay)
        materialShowcaseView.setMaskColour(Color.parseColor("#CC000000"))
        if (withRectangleShape) materialShowcaseView.withRectangleShape()
        return materialShowcaseView.build()
    }
}