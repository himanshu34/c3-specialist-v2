package co.nayan.canvas.videoannotation

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.utils.disabled
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3v2.core.widgets.CustomAlertDialogFragment
import co.nayan.c3v2.core.widgets.CustomAlertDialogListener
import co.nayan.c3views.utils.*
import co.nayan.canvas.CanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.databinding.FragmentVideoAnnotationBinding
import co.nayan.canvas.utils.SimpleDividerItemDecoration
import co.nayan.canvas.utils.getBitmapFromDirectory
import co.nayan.canvas.utils.getFormattedTimeStamp
import co.nayan.canvas.viewBinding
import co.nayan.canvas.viewmodels.SandboxViewModel
import co.nayan.canvas.views.getUserCategoryDrawable
import co.nayan.canvas.views.showToast
import co.nayan.canvas.views.toast.ToastyType
import co.nayan.canvas.widgets.JunkDialogListener
import co.nayan.canvas.widgets.JunkRecordDialogFragment
import co.nayan.canvas.widgets.RecordInfoDialogFragment
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig
import java.io.File

@AndroidEntryPoint
class VideoAnnotationFragment : CanvasFragment(R.layout.fragment_video_annotation) {

    private val binding by viewBinding(FragmentVideoAnnotationBinding::bind)
    private var record: Record? = null
    private var isMediaPlaying = false
    private var frameCount = 0
    private var nonActiveDots: Drawable? = null
    private var activeDot: Drawable? = null
    private var dots: Array<ImageView?>? = null
    private var isAnswerJunk: Boolean = false
    private var sandboxAnnotations = mutableListOf<AnnotationObjectsAttribute>()
    private var isSandboxPause = false
    var isMediaPlaybackCompleted = false

    private val exoplayerControlsHandler = Handler(Looper.getMainLooper())
    private val hideExoPlayerControlsRunnable = Runnable {
        if (isSandboxPause.not() && isWalkThroughRunning.not())
            hideExoplayerControls()
    }

    private fun hideExoplayerControls() {
        binding.parentAnnotationHintContainer.gone()
        binding.exoPlayerController.exoPlayerControlButtonsView.gone()
        binding.questionTxt.gone()
        binding.tvUserCategoryMedal.gone()
        binding.recordTxt.gone()
        if (isAnswerJunk) binding.junkAnnotationIv.gone()
        if (shouldUseSandboxProgress()) binding.sandboxProgress.gone()
    }

    private val onVideoAnnotationClickListener = object : OnVideoAnnotationClickListener {
        override fun onClick(videoAnnotationData: VideoAnnotationData) {
            if (videoAnnotationData.bitmap == null) {
                requireContext().showToast(
                    0,
                    getString(R.string.preview_not_available),
                    ToastyType.NEGATIVE
                )
            } else {
                when (canvasViewModel.getVideoModeState()) {
                    PARENT_STEP_SANDBOX,
                    PARENT_STEP_VIDEO_ANNOTATION -> {
                        canvasViewModel.previewAnnotatedFrame(videoAnnotationData)
                    }

                    CHILD_STEP_SANDBOX,
                    CHILD_STEP_VIDEO_ANNOTATION -> {
                        canvasViewModel.annotateChildWithRespectToParent(videoAnnotationData)
                            ?.let { parentAnnotations ->
                                val hasChildAnnotation = parentAnnotations.find { !it.isParent }
                                showExoplayerControls()
                                if (canvasViewModel.getVideoModeState() == CHILD_STEP_VIDEO_ANNOTATION) {
                                    binding.exoPlayerController.ivCapture.visible()
                                    isSandboxPause = canvasViewModel.isSandbox()
                                } else frameCount = 0
                                binding.exoPlayerController.junkIV.gone()
                                binding.exoPlayerController.submitVideoIV.gone()
                                binding.drawerLayout.closeDrawer(GravityCompat.END)
                                hasChildAnnotation?.let {
                                    canvasViewModel.previewAnnotatedFrame(it)
                                }
                            }
                    }

                    PARENT_STEP_VIDEO_VALIDATION,
                    CHILD_STEP_VIDEO_VALIDATION -> {
                        updateFrameMonitor(videoAnnotationData)
                        binding.drawerLayout.closeDrawer(GravityCompat.END)
                    }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateFrameMonitor(selected: VideoAnnotationData) {
        binding.frameMonitorContainer.visible()
        binding.sliderDotsPanel.removeAllViews()
        val frames = when (canvasViewModel.getVideoModeState()) {
            PARENT_STEP_VIDEO_VALIDATION -> canvasViewModel.getVideoAnnotationDataForFrameMonitor()
            CHILD_STEP_VIDEO_VALIDATION ->
                canvasViewModel.getParentChildAnnotationDataForFrameMonitor(selected)

            else -> mutableListOf()
        }

        if (frames.isEmpty()) {
            requireContext().showToast(
                0,
                getString(R.string.preview_not_available),
                ToastyType.NEGATIVE
            )
            return
        }

        val frameCount = frames.size
        dots = arrayOfNulls(frameCount)
        for (i in 0 until frameCount) {
            dots!![i] = ImageView(context)
            dots!![i]?.setImageDrawable(nonActiveDots)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            binding.sliderDotsPanel.addView(dots!![i], params)
        }
        frameMonitorAdapter.add(frames)
        val selectedIndex = frames.indexOf(selected)
        binding.frameMonitor.setCurrentItem(selectedIndex, true)
        dots!![selectedIndex]?.setImageDrawable(activeDot)
        frameMonitorAdapter.notifyDataSetChanged()
    }

    private val videoAnnotationViewAdapter =
        VideoAnnotationViewAdapter(onVideoAnnotationClickListener)
    private val workflowStepAnnotationAdapter =
        WorkflowStepAnnotationAdapter(onVideoAnnotationClickListener)
    private var videoAnnotationSandboxAdapter: VideoAnnotationSandboxAdapter? = null

    private val frameMonitorAdapter = FrameMonitorAdapter()
    private val onPageChangeCallback =
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (canvasViewModel.getVideoModeState()) {
                    PARENT_STEP_VIDEO_VALIDATION -> {
                        val frame =
                            canvasViewModel.getVideoAnnotationDataForFrameMonitor()[position]
                        frameCount = frame.frameCount ?: 0
                        playNext()
                        canvasViewModel.selectVideoFrame(frame)
                    }

                    CHILD_STEP_VIDEO_VALIDATION -> {
                    }
                }

                for (i in 0 until frameMonitorAdapter.itemCount) {
                    dots?.get(i)?.setImageDrawable(nonActiveDots)
                }
                dots?.get(position)?.setImageDrawable(activeDot)
            }
        }

    private val junkDialogListener = object : JunkDialogListener {
        override fun junkRecord() {
            canvasViewModel.submitVideoAnnotation()
        }
    }

    private val customAlertDialogListener = object : CustomAlertDialogListener {
        override fun onPositiveBtnClick(shouldFinish: Boolean, tag: String?) {
            when (tag) {
                APPROVE_VIDEO -> {
                    canvasViewModel.submitVideoJudgement(true)
                }

                REJECT_VIDEO -> {
                    canvasViewModel.submitVideoJudgement(false)
                }

                SUBMIT_VIDEO -> {
                    pausePlayer()
                    canvasViewModel.submitVideoAnnotation()
                }

                ANNOTATING_CHILD -> {
                    canvasViewModel.exitChildAnnotationMode()
                    binding.exoPlayerController.ivCapture.gone()
                    if (canvasViewModel.isSandbox()) binding.exoPlayerController.junkIV.gone()
                    else binding.exoPlayerController.junkIV.visible()
                    binding.exoPlayerController.submitVideoIV.visible()
                    showExoplayerControls()
                    binding.drawerLayout.openDrawer(GravityCompat.END)
                }

                INTERPOLATION_SANDBOX -> {
                    canvasViewModel.setupInterpolationSandBoxData()
                    resetPlayer(true)
                }
            }
        }

        override fun onNegativeBtnClick(shouldFinish: Boolean, tag: String?) {
            when (tag) {
                INTERPOLATION_LINKING -> canvasViewModel.discardCurrentAnnotation()
                INTERPOLATION_SANDBOX -> {
                    canvasViewModel.setupInterpolationSandBoxData()
                    resetPlayer(true)
                }
            }
        }
    }

    private fun resetPlayer(status: Boolean? = false) {
        if (status == true) frameCount = 1
        setupTimeBar()
        resumePlayer()
    }

    private var delayMillis: Long = 30
    private val exoPlayerProgressHandler = Handler(Looper.getMainLooper())
    private val exoPlayerProgressRunnable = object : Runnable {
        override fun run() {
            if (isMediaPlaying) {
                if (frameCount < canvasViewModel.totalFrames) {
                    frameCount += 1
                    setupTimeBar()
                    canvasViewModel.monitorFrames(frameCount)
                } else {
                    pausePlayer()
                    canvasViewModel.setMediaPlaybackStatus(true)
                }
            }
            exoPlayerProgressHandler.postDelayed(this, delayMillis)
        }
    }

    private fun playNext() {
        canvasViewModel.framesDir?.let {
            val file = File(it, "out$frameCount.jpg")
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                binding.framesView.setImageURI(uri)
            } else {
                frameCount = 0
                pausePlayer()
            }
        }
    }

    private fun setupTimeBar() {
        binding.exoPlayerController.exoProgress.progress = frameCount * 30
    }

    private val drawerLayoutListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerStateChanged(newState: Int) {}

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

        override fun onDrawerClosed(drawerView: View) {
            binding.openAnnotationSlider.visible()
        }

        override fun onDrawerOpened(drawerView: View) {
            binding.openAnnotationSlider.gone()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupPlayerControls()
        setupClickListeners()
        setupObserver()
    }

    private fun setupViews() {
        binding.framesView.isInterpolationEnabled = isInterpolationEnabled()
        if (isInterpolationEnabled()) {
            binding.cropView.visible()
            binding.openAnnotationSlider.gone()
            binding.cropView.touchEnabled(false)
            if (canvasViewModel.workType != WorkType.ANNOTATION) {
                binding.objectDoneBtn.gone()
                binding.interpolationSwitchBtn.gone()
                updateExoPlayerControls()
            }
        }
    }

    private fun updateExoPlayerControls() {
        binding.exoPlayerController.ivRewindByFrame.gone()
        binding.exoPlayerController.ivForwardByFrame.gone()
        binding.exoPlayerController.ivRewind.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_slow_backward
            )
        )
        binding.exoPlayerController.ivForward.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_fast_forward
            )
        )
    }

    private fun setupObserver() {
        canvasViewModel.activeAnnotationObserver.observe(viewLifecycleOwner) {
            if (isInterpolationEnabled()) {
                if (it) {
                    binding.interpolationSwitchBtn.gone()
                    binding.objectDoneBtn.visible()
                } else if (canvasViewModel.workType == WorkType.ANNOTATION) {
                    binding.interpolationSwitchBtn.visible()
                    binding.objectDoneBtn.gone()
                    resetPlayer()
                }
            }
        }

        canvasViewModel.isMediaPlaybackStatus.observe(viewLifecycleOwner) {
            isMediaPlaybackCompleted = it
        }

        canvasViewModel.isPreviewModeStatus.observe(viewLifecycleOwner) {
            canvasViewModel.isInPreviewMode = it
            binding.interpolationSwitchBtn.apply {
                isChecked = it.not()
                text = if (isChecked) getString(R.string.interpolate)
                else getString(R.string.preview)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exoPlayerProgressHandler.removeCallbacks(exoPlayerProgressRunnable)
        exoPlayerProgressHandler.post(exoPlayerProgressRunnable)
    }

    override fun onPause() {
        super.onPause()
        exoPlayerProgressHandler.removeCallbacks(exoPlayerProgressRunnable)
    }

    override fun setupHelpButton(applicationMode: String?) {}

    private fun setupVideoMode() {
        when (canvasViewModel.getVideoModeState()) {
            PARENT_STEP_SANDBOX -> {
                binding.exoPlayerController.ivCapture.gone()
                binding.exoPlayerController.submitVideoIV.visible()
                binding.exoPlayerController.junkIV.gone()
                binding.exoPlayerController.approveTv.gone()
                binding.exoPlayerController.rejectTv.gone()
                setupJudgementButton()
                setupVideoSandboxResultView()
                if (canvasViewModel.isAdminRole().not()) binding.sandboxProgress.visible()
            }

            PARENT_STEP_VIDEO_ANNOTATION -> {
                binding.exoPlayerController.ivCapture.visible()
                isSandboxPause = canvasViewModel.isSandbox()
                binding.exoPlayerController.submitVideoIV.visible()
                if (canvasViewModel.isSandbox()) binding.exoPlayerController.junkIV.gone()
                else binding.exoPlayerController.junkIV.visible()
                binding.exoPlayerController.approveTv.gone()
                binding.exoPlayerController.rejectTv.gone()
                setupJudgementButton()
            }

            CHILD_STEP_SANDBOX,
            CHILD_STEP_VIDEO_ANNOTATION -> {
                binding.exoPlayerController.ivCapture.gone()
                binding.exoPlayerController.submitVideoIV.visible()
                binding.exoPlayerController.approveTv.gone()
                binding.exoPlayerController.rejectTv.gone()
                setupJudgementButton()

                if (canvasViewModel.isSandbox()) {
                    binding.exoPlayerController.junkIV.gone()
                    if (canvasViewModel.isAdminRole().not())
                        binding.sandboxProgress.visible()
                    else binding.sandboxProgress.gone()
                    setupVideoSandboxResultView()
                } else binding.exoPlayerController.junkIV.visible()
            }

            PARENT_STEP_VIDEO_VALIDATION,
            CHILD_STEP_VIDEO_VALIDATION -> {
                binding.exoPlayerController.ivCapture.gone()
                binding.exoPlayerController.submitVideoIV.gone()
                binding.exoPlayerController.junkIV.gone()
                binding.exoPlayerController.approveTv.visible()
                binding.exoPlayerController.rejectTv.visible()
                setupFrameMonitor()
            }
        }
        binding.frameMonitorContainer.gone()
    }

    private fun setupVideoSandboxResultView() {
        val linearLayoutManager = LinearLayoutManager(requireContext())
        linearLayoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.sandboxResultList.layoutManager = linearLayoutManager
        binding.sandboxResultList.addItemDecoration(
            SimpleDividerItemDecoration(
                ContextCompat.getDrawable(requireContext(), R.drawable.line_divider)
            )
        )
        videoAnnotationSandboxAdapter = VideoAnnotationSandboxAdapter()
        binding.sandboxResultList.adapter = videoAnnotationSandboxAdapter
    }

    private fun setupFrameMonitor() {
        requireContext().let {
            nonActiveDots = ContextCompat.getDrawable(it, R.drawable.non_active_dots)
            activeDot = ContextCompat.getDrawable(it, R.drawable.active_dots)
        }
        binding.frameMonitor.registerOnPageChangeCallback(onPageChangeCallback)
        binding.frameMonitor.adapter = frameMonitorAdapter
    }

    private fun showNavigationBarControls() {
        if (binding.exoPlayerController.exoPlayerControlButtonsView.isVisible.not()) {
            showExoplayerControls()
            return
        }

        if (frameCount == canvasViewModel.totalFrames) {
            isMediaPlaying = false
            binding.exoPlayerController.ivPlayPause.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.ic_play
                )
            )
        } else {
            val drawableId =
                if (isMediaPlaying) R.drawable.ic_play else R.drawable.ic_pause
            isMediaPlaying = !isMediaPlaying
            binding.exoPlayerController.ivPlayPause.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    drawableId
                )
            )
        }
    }

    private fun setupClickListeners() {
        binding.framesView.onCaptureReleasePoint = {
            if (activity != null && this.isVisible) showNavigationBarControls()
        }

        binding.exoPlayerController.ivPlayPause.setOnClickListener {
            if (frameCount == canvasViewModel.totalFrames) {
                frameCount = 0
                resetPlayer()
            } else {
                val drawableId = if (isMediaPlaying) R.drawable.ic_play else R.drawable.ic_pause
                isMediaPlaying = !isMediaPlaying
                binding.exoPlayerController.ivPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        drawableId
                    )
                )
            }
        }

        binding.openAnnotationSlider.setOnClickListener {
            if (canvasViewModel.isInChildParentAssociationMode()) {
                showAlert(
                    ANNOTATING_CHILD, getString(R.string.exit_child_annotation),
                    getString(R.string.child_annotation), getString(R.string.ok)
                )
            } else {
                if (canvasViewModel.hasVideoAnnotationData()) {
                    if (canvasViewModel.workType == WorkType.ANNOTATION) {
                        requireContext().showToast(
                            0,
                            getString(R.string.please_annotate_video_frames),
                            ToastyType.NEGATIVE
                        )
                    } else {
                        requireContext().showToast(
                            0,
                            getString(R.string.no_annotated_video_frame_found),
                            ToastyType.NEGATIVE
                        )
                    }
                } else refreshVideoAnnotationSlider()
            }
        }

        binding.closeAnnotationSlider.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            resumePlayer()
        }

        binding.parentAnnotationHintContainer.setOnClickListener {
            canvasViewModel.getLastParentAnnotationHint()?.let {
                frameCount = it.frameCount ?: 0
                playNext()
            }
        }

        binding.closeFrameMonitor.setOnClickListener { binding.frameMonitorContainer.gone() }

        binding.exoPlayerController.approveTv.setOnClickListener {
            if (isMediaPlaybackCompleted) {
                showAlert(
                    APPROVE_VIDEO,
                    getString(R.string.are_you_sure),
                    getString(R.string.approve_video),
                    getString(R.string.approve)
                )
            } else requireContext().showToast(
                0,
                getString(R.string.please_watch_complete_video),
                ToastyType.NEGATIVE
            )
        }

        binding.exoPlayerController.rejectTv.setOnClickListener {
            if (isMediaPlaybackCompleted) {
                showAlert(
                    REJECT_VIDEO,
                    getString(R.string.are_you_sure),
                    getString(R.string.reject_video),
                    getString(R.string.reject)
                )
            } else requireContext().showToast(
                0,
                getString(R.string.please_watch_complete_video),
                ToastyType.NEGATIVE
            )
        }

        binding.exoPlayerController.ivCapture.setOnClickListener {
            isSandboxPause = false
            pausePlayer()
            val bitmap = getBitmapFromDirectory(
                canvasViewModel.totalFrames, frameCount,
                canvasViewModel.framesDir
            )
            canvasViewModel.extractFrame(bitmap, frameCount)
        }

        binding.exoPlayerController.submitVideoIV.setOnClickListener {
            if (isMediaPlaybackCompleted) {
                showAlert(
                    SUBMIT_VIDEO,
                    getString(R.string.are_you_sure),
                    getString(R.string.submit_video),
                    getString(R.string.submit)
                )
            } else requireContext().showToast(
                0,
                getString(R.string.please_watch_complete_video),
                ToastyType.NEGATIVE
            )
        }

        binding.exoPlayerController.junkIV.setOnClickListener {
            if (isMediaPlaybackCompleted) showJunkRecordDialog()
            else requireContext().showToast(
                0,
                getString(R.string.please_watch_complete_video),
                ToastyType.NEGATIVE
            )
        }

        binding.sandboxOkBtn.setOnClickListener {
            binding.sandboxResultContainer.gone()
            if (isInterpolationEnabled()) return@setOnClickListener
            (canvasViewModel as SandboxViewModel).submitAndFetchNext(false, sandboxAnnotations)
        }

        binding.objectDoneBtn.setOnClickListener {
            if (canvasViewModel.checkMinimumCriteria() > 1) {
                canvasViewModel.interpolateObject()
                if (canvasViewModel.isSandbox())
                    canvasViewModel.setPreviewStatus(true)
                setupJudgementButton()
                showPreviewWalkThrough()
            } else {
                showAlert(
                    INTERPOLATION_LINKING,
                    getString(R.string.interpolation_criteria_msg),
                    getString(R.string.alert),
                    getString(R.string.continue_txt),
                    getString(R.string.discard_changes_txt)
                )
            }
        }

        binding.interpolationSwitchBtn.setOnCheckedChangeListener { buttonView, isChecked ->
            buttonView.text = if (isChecked) getString(R.string.interpolate)
            else getString(R.string.preview)
            if (buttonView.isPressed) {
                canvasViewModel.setPreviewStatus(isChecked.not())
                resetPlayer()
            }
        }

        binding.recordTxt.setOnTouchListener(infoTouchListener)
    }

    private val infoTouchListener = object : View.OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            val drawableRight = 2
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (event.rawX >= binding.recordTxt.right - binding.recordTxt.compoundDrawables[drawableRight].bounds.width()) {
                    record?.let { setupRecordInfoDialog(it) }
                    return true
                }
            }
            return false
        }
    }

    private fun setupRecordInfoDialog(record: Record) = lifecycleScope.launch {
        pausePlayer()
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

    override fun annotateNextSandbox() {
        canvasViewModel.activeAnnotationId = null
        canvasViewModel.setActiveAnnotationState(false)
        canvasViewModel.setPreviewStatus(false)
        val objectToTrack = canvasViewModel.distinctDataObjects
        if (objectToTrack.isNullOrEmpty().not()) {
            pausePlayer()
            canvasViewModel.setupInterpolationSandBoxData()
            showAlert(
                INTERPOLATION_SANDBOX,
                getString(R.string.interpolation_next_sandbox_msg),
                getString(R.string.interpolation),
                getString(R.string.ok)
            )
        }
    }

    private fun pausePlayer() {
        if (isMediaPlaying) {
            isMediaPlaying = !isMediaPlaying
            binding.exoPlayerController.ivPlayPause.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_play
                )
            )
        }
        if (isSandboxPause) setupWalkThrough()
    }

    private fun resumePlayer() {
        if (isMediaPlaying.not()) {
            isMediaPlaying = !isMediaPlaying
            binding.exoPlayerController.ivPlayPause.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_pause
                )
            )
        }
    }

    private fun setupVideoAnnotationSlider() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        binding.drawerLayout.addDrawerListener(drawerLayoutListener)
        workflowStepAnnotationAdapter.setVideoAnnotationViewAdapterMode(canvasViewModel.getVideoModeState())
        when (canvasViewModel.getVideoModeState()) {
            PARENT_STEP_SANDBOX,
            PARENT_STEP_VIDEO_ANNOTATION,
            PARENT_STEP_VIDEO_VALIDATION -> {
                val gridLayoutManager = GridLayoutManager(requireContext(), 3)
                gridLayoutManager.orientation = LinearLayoutManager.VERTICAL
                binding.videoAnnotationRecyclerView.layoutManager = gridLayoutManager
                binding.videoAnnotationRecyclerView.adapter = videoAnnotationViewAdapter
            }

            CHILD_STEP_SANDBOX,
            CHILD_STEP_VIDEO_ANNOTATION,
            CHILD_STEP_VIDEO_VALIDATION -> {
                val linearLayoutManager = LinearLayoutManager(requireContext())
                linearLayoutManager.orientation = LinearLayoutManager.VERTICAL
                binding.videoAnnotationRecyclerView.layoutManager = linearLayoutManager
                binding.videoAnnotationRecyclerView.adapter = workflowStepAnnotationAdapter
                binding.videoAnnotationRecyclerView.addItemDecoration(
                    SimpleDividerItemDecoration(
                        ContextCompat.getDrawable(requireContext(), R.drawable.line_divider)
                    )
                )
            }
        }
    }

    private fun setupPlayerControls() {
        binding.exoPlayerController.ivForward.setOnClickListener {
            if (isInterpolationEnabled() && canvasViewModel.workType != WorkType.ANNOTATION) {
                if (delayMillis > 30) delayMillis /= 2
            } else {
                val totalFrames = canvasViewModel.totalFrames
                val toSet = if (frameCount + 3 >= totalFrames)
                    totalFrames else frameCount + 3
                binding.exoPlayerController.exoProgress.progress = toSet * 30
                showExoplayerControls()
            }

            canvasViewModel.refreshAnnotations(frameCount)
        }

        binding.exoPlayerController.ivRewind.setOnClickListener {
            if (isInterpolationEnabled() && canvasViewModel.workType != WorkType.ANNOTATION) {
                if (delayMillis in 30..239) delayMillis *= 2
            } else {
                val totalFrames = canvasViewModel.totalFrames
                val toSet = when {
                    frameCount > totalFrames -> totalFrames - 3
                    frameCount <= 3 -> 0
                    else -> frameCount - 3
                }
                binding.exoPlayerController.exoProgress.progress = toSet * 30
                showExoplayerControls()
            }

            canvasViewModel.refreshAnnotations(frameCount)
        }

        binding.exoPlayerController.ivForwardByFrame.setOnClickListener {
            val totalFrames = canvasViewModel.totalFrames
            val toSet = if (frameCount + 1 >= totalFrames)
                totalFrames else frameCount + 1
            binding.exoPlayerController.exoProgress.progress = toSet * 30
            showExoplayerControls()
            canvasViewModel.refreshAnnotations(frameCount)
        }

        binding.exoPlayerController.ivRewindByFrame.setOnClickListener {
            val totalFrames = canvasViewModel.totalFrames
            val toSet = when {
                frameCount > totalFrames -> totalFrames - 1
                frameCount <= 1 -> 0
                else -> frameCount - 1
            }
            binding.exoPlayerController.exoProgress.progress = toSet * 30
            showExoplayerControls()
            canvasViewModel.refreshAnnotations(frameCount)
        }

        binding.exoPlayerController.exoProgress.setOnSeekBarChangeListener(onSeekBarChangeListener)
    }

    private val onSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            var toSet = progress / 30
            val totalFrames = canvasViewModel.totalFrames
            if (toSet >= totalFrames) {
                toSet = totalFrames
                pausePlayer()
                canvasViewModel.setMediaPlaybackStatus(true)
            }
            frameCount = toSet
            binding.exoPlayerController.exoPosition.text = frameCount.getFormattedTimeStamp()
            playNext()
            if (fromUser) showExoplayerControls()
            canvasViewModel.refreshAnnotations(frameCount)
            if (frameCount == totalFrames && binding.exoPlayerController.exoPlayerControlButtonsView.isVisible.not())
                showExoplayerControls()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            pausePlayer()
        }
    }

    override fun toggleScreenCaptureState(status: Boolean) {
        when (canvasViewModel.getVideoModeState()) {
            PARENT_STEP_SANDBOX -> {
                if (status) extractFrameForParentSandboxResult()
                else binding.exoPlayerController.ivCapture.gone()
            }

            CHILD_STEP_SANDBOX -> {
                if (canvasViewModel.isInChildParentAssociationMode()) {
                    if (status) extractFrameForChildSandboxResults()
                    else binding.exoPlayerController.ivCapture.gone()
                } else if (status) refreshVideoAnnotationSlider()
            }
        }
    }

    private fun extractFrameForParentSandboxResult() {
        isSandboxPause = canvasViewModel.isSandbox()
        pausePlayer()
        canvasViewModel.getSandBoxCorrectVideoAnnotationList().forEach {
            if (it.bitmap == null && frameCount == it.frameCount) {
                frameCount = it.frameCount ?: 0
                it.bitmap = getBitmapFromDirectory(
                    canvasViewModel.totalFrames,
                    frameCount,
                    canvasViewModel.framesDir
                )
            }
        }
        binding.exoPlayerController.ivCapture.visible()
        showExoplayerControls()
    }

    private fun extractFrameForChildSandboxResults() {
        isSandboxPause = canvasViewModel.isSandbox()
        pausePlayer()
        canvasViewModel.getSandboxParentAnnotations()?.last()?.let {
            frameCount = it.frameCount ?: 0
            it.bitmap = getBitmapFromDirectory(
                canvasViewModel.totalFrames,
                frameCount,
                canvasViewModel.framesDir
            )
        }
        binding.exoPlayerController.ivCapture.visible()
        showExoplayerControls()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun refreshVideoAnnotationSlider() {
        if (isWalkThroughRunning.not()) {
            setupJudgementButton()
            hideExoplayerControls()
            when (canvasViewModel.getVideoModeState()) {
                PARENT_STEP_SANDBOX,
                PARENT_STEP_VIDEO_ANNOTATION,
                PARENT_STEP_VIDEO_VALIDATION -> {
                    managePreviewForParentStep()
                }

                CHILD_STEP_SANDBOX,
                CHILD_STEP_VIDEO_VALIDATION,
                CHILD_STEP_VIDEO_ANNOTATION -> {
                    setupChildAnnotationButton()
                    val previewFrames = canvasViewModel.getVideoParentAnnotationDataForPreview()
                    pausePlayer()

                    previewFrames.forEach { stepAnnotations ->
                        stepAnnotations.forEach {
                            if (it.bitmap == null && frameCount == it.frameCount) {
                                frameCount = it.frameCount ?: 0
                                it.bitmap = getBitmapFromDirectory(
                                    canvasViewModel.totalFrames,
                                    frameCount,
                                    canvasViewModel.framesDir
                                )
                            }
                        }
                    }

                    workflowStepAnnotationAdapter.add(previewFrames)
                    workflowStepAnnotationAdapter.notifyDataSetChanged()
                    if (binding.frameMonitorContainer.visibility == View.GONE)
                        binding.drawerLayout.openDrawer(GravityCompat.END)
                }
            }
        }
    }

    private fun setupChildAnnotationButton() {
        if (canvasViewModel.getVideoModeState() == CHILD_STEP_VIDEO_ANNOTATION
            || canvasViewModel.getVideoModeState() == CHILD_STEP_SANDBOX
        ) {
            if (canvasViewModel.isInChildParentAssociationMode()) {
                binding.exoPlayerController.ivCapture.visible()
                isSandboxPause = canvasViewModel.isSandbox()
                binding.exoPlayerController.junkIV.gone()
                binding.exoPlayerController.submitVideoIV.gone()
            } else {
                binding.exoPlayerController.ivCapture.gone()
                if (canvasViewModel.isSandbox()) binding.exoPlayerController.junkIV.gone()
                else binding.exoPlayerController.junkIV.visible()
                binding.exoPlayerController.submitVideoIV.visible()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun managePreviewForParentStep() {
        if (canvasViewModel.getVideoAnnotationData().isEmpty()) {
            videoAnnotationViewAdapter.add(listOf())
            videoAnnotationViewAdapter.notifyDataSetChanged()
        } else {
            pausePlayer()
            val previewFrames =
                canvasViewModel.getVideoAnnotationData()

            previewFrames.forEach {
                if (it.bitmap == null && frameCount == it.frameCount) {
                    frameCount = it.frameCount ?: 0
                    it.bitmap = getBitmapFromDirectory(
                        canvasViewModel.totalFrames,
                        frameCount,
                        canvasViewModel.framesDir
                    )
                }
            }
            videoAnnotationViewAdapter.add(previewFrames)
            videoAnnotationViewAdapter.notifyDataSetChanged()
            if (binding.frameMonitorContainer.visibility == View.GONE) {
                binding.drawerLayout.openDrawer(GravityCompat.END)
            }
        }
    }

    private fun setupJudgementButton() {
        when (canvasViewModel.getVideoModeState()) {
            CHILD_STEP_SANDBOX -> {
                if (canvasViewModel.hasAnnotatedAllSandboxChildAnnotation())
                    binding.exoPlayerController.submitVideoIV.enabled()
                else binding.exoPlayerController.submitVideoIV.disabled()
            }

            CHILD_STEP_VIDEO_ANNOTATION -> {
                if (canvasViewModel.isSandbox() && canvasViewModel.isAdminRole()) {
                    if (canvasViewModel.hasAnnotatedAllSandboxChildAnnotation())
                        binding.exoPlayerController.submitVideoIV.enabled()
                    else binding.exoPlayerController.submitVideoIV.disabled()
                } else {
                    if (canvasViewModel.hasAnnotatedChildAnnotation()) {
                        binding.exoPlayerController.junkIV.disabled()
                        binding.exoPlayerController.submitVideoIV.enabled()
                    } else {
                        binding.exoPlayerController.junkIV.enabled()
                        binding.exoPlayerController.submitVideoIV.disabled()
                    }
                }
            }

            PARENT_STEP_SANDBOX,
            PARENT_STEP_VIDEO_ANNOTATION -> {
                if (canvasViewModel.getVideoAnnotationData().isNotEmpty()) {
                    binding.exoPlayerController.junkIV.disabled()
                    binding.exoPlayerController.submitVideoIV.enabled()
                } else {
                    binding.exoPlayerController.junkIV.enabled()
                    binding.exoPlayerController.submitVideoIV.disabled()
                }
            }
        }
    }

    override fun populateVideoRecord(record: Record) {
        canvasViewModel.resetVideoModeData()
        if (record.mediaUrl == null) requireContext().showToast(
            0,
            getString(R.string.video_url_not_found),
            ToastyType.NEGATIVE
        ) else {
            this.record = record
            binding.exoPlayerController.exoProgress.setOnSeekBarChangeListener(null)
            initFramesPlayer(record)
            binding.exoPlayerController.exoProgress.setOnSeekBarChangeListener(
                onSeekBarChangeListener
            )
            val recordId = getRecordIdText(record)
            binding.recordTxt.text = recordId
            Timber.d(record.toString())
            videoAnnotationViewAdapter.clear()
            binding.questionTxt.text = canvasViewModel.question
            binding.tvUserCategoryMedal.apply {
                text = canvasViewModel.userCategory
                val drawable = getUserCategoryDrawable(canvasViewModel.userCategory)
                if (drawable != null) {
                    setCompoundDrawablesWithIntrinsicBounds(0, drawable, 0, 0)
                    visible()
                } else gone()
            }
            binding.previewQuestionTxt.text = canvasViewModel.question
            canvasViewModel.populateVideoAnnotation(record)
            setupVideoMode()
            setupVideoAnnotationSlider()
            if (isInterpolationEnabled()) resetInterpolationMode()
            setupJunk()
        }
    }

    private fun resetInterpolationMode() {
        canvasViewModel.activeAnnotationId = null
        canvasViewModel.setActiveAnnotationState(false)
        canvasViewModel.setPreviewStatus(false)
        if (canvasViewModel.isSandbox() && canvasViewModel.isAdminRole().not()) {
            pausePlayer()
            val objectsToTrack = canvasViewModel.distinctDataObjects
            val messageInitial = when {
                objectsToTrack.isNullOrEmpty() -> getString(R.string.no_interpolation_sandbox_msg)
                (objectsToTrack.size == 1) -> String.format(
                    getString(R.string.interpolation_sandbox_msg),
                    getString(R.string.one_object)
                )

                else -> {
                    val objectString = String.format(
                        getString(R.string.multiple_object),
                        objectsToTrack.size
                    )
                    String.format(getString(R.string.interpolation_sandbox_msg), objectString)
                }
            }
            val message = getString(R.string.interpolation_first_sandbox_msg)
            canvasViewModel.setupInterpolationSandBoxData()
            showAlert(
                INTERPOLATION_SANDBOX,
                "$messageInitial \n\n $message",
                getString(R.string.interpolation),
                getString(R.string.ok)
            )
        } else resetPlayer()
    }

    private fun setupJunk() {
        val annotations = record.annotations()
        isAnswerJunk = canvasViewModel.isSandbox().not() &&
                annotations.isNotEmpty() && annotations.all { DrawType.JUNK == it.type }

        if (isAnswerJunk) binding.junkAnnotationIv.visible()
        else binding.junkAnnotationIv.gone()
    }

    private fun initFramesPlayer(record: Record) {
        frameCount = 0
        canvasViewModel.framesDir = File(requireContext().filesDir, "NayanVideoMode/${record.id}")
        canvasViewModel.totalFrames = canvasViewModel.framesDir?.listFiles()?.size ?: 1
        binding.exoPlayerController.exoProgress.apply {
            progress = 0
            max = canvasViewModel.totalFrames * 30
        }
        isMediaPlaying = true
        binding.exoPlayerController.ivPlayPause.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(), R.drawable.ic_pause
            )
        )
        showExoplayerControls()
        setupWalkThrough()
    }

    private var isWalkThroughRunning = false
    private fun showExoplayerControls() {
        binding.exoPlayerController.exoPlayerControlButtonsView.visible()
        if (canvasViewModel.isInChildParentAssociationMode()) {
            binding.questionTxt.gone()
            binding.recordTxt.gone()
            binding.tvUserCategoryMedal.gone()
            setupParentContainerHint()
        } else {
            binding.questionTxt.visible()
            binding.recordTxt.visible()
            if (canvasViewModel.userCategory.isNullOrEmpty().not())
                binding.tvUserCategoryMedal.visible()
            binding.parentAnnotationHintContainer.gone()
        }

        if (isAnswerJunk) binding.junkAnnotationIv.visible()
        else binding.junkAnnotationIv.gone()

        if (canvasViewModel.isSandbox() && canvasViewModel.isAdminRole().not())
            binding.sandboxProgress.visible()

        exoplayerControlsHandler.removeCallbacks(hideExoPlayerControlsRunnable)
        exoplayerControlsHandler.postDelayed(hideExoPlayerControlsRunnable, 3000)
    }

    override fun onStop() {
        super.onStop()
        exoplayerControlsHandler.removeCallbacks(hideExoPlayerControlsRunnable)
    }

    private fun showJunkRecordDialog() {
        val dialogMessage = getString(R.string.mark_video_as_junk)
        childFragmentManager.fragments.forEach {
            if (it is JunkRecordDialogFragment) {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val junkRecordDialogFragment = JunkRecordDialogFragment.newInstance(junkDialogListener)
        junkRecordDialogFragment.setMessage(dialogMessage)
        junkRecordDialogFragment.show(
            childFragmentManager.beginTransaction(),
            getString(R.string.junk_video)
        )
    }

    private fun showAlert(
        tag: String,
        message: String,
        title: String? = null,
        positiveText: String? = null,
        negativeText: String? = null
    ) {
        childFragmentManager.fragments.forEach {
            if (it is CustomAlertDialogFragment) {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val customAlertDialogFragment =
            CustomAlertDialogFragment.newInstance(customAlertDialogListener).apply {
                setTitle(title)
                setMessage(message)
                if (positiveText != null)
                    setPositiveBtnText(positiveText)
                if (negativeText != null)
                    setNegativeBtnText(negativeText)
                showPositiveBtn(true)
                showNegativeBtn(true)
            }
        customAlertDialogFragment.show(childFragmentManager.beginTransaction(), tag)
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun setupParentContainerHint() {
        binding.parentAnnotationHintContainer.visible()
        hideAllParentAnnotationHints()
        canvasViewModel.getLastParentAnnotationHint()?.let { videoAnnotationData ->
            if (videoAnnotationData.bitmap == null) {
                binding.parentPhotoView.visible()
                binding.parentPhotoView.setOnTouchListener { _, _ -> false }
            } else {
                videoAnnotationData.bitmap?.let {
                    when (videoAnnotationData.annotations.firstOrNull()?.type) {
                        DrawType.BOUNDING_BOX -> {
                            binding.parentCropView.reset()
                            binding.parentCropView.visible()
                            binding.parentCropView.touchEnabled(false)
                            binding.parentCropView.setBitmapAttributes(
                                it.width,
                                it.height
                            )
                            binding.parentCropView.crops.addAll(
                                videoAnnotationData.annotations
                                    .crops(it)
                            )
                            binding.parentCropView.setImageBitmap(it)
                        }

                        DrawType.QUADRILATERAL -> {
                            binding.parentQuadrilateralView.reset()
                            binding.parentQuadrilateralView.visible()
                            binding.parentQuadrilateralView.touchEnabled(false)
                            binding.parentQuadrilateralView.quadrilaterals.addAll(
                                videoAnnotationData.annotations.quadrilaterals(it)
                            )
                            binding.parentQuadrilateralView.setImageBitmap(it)
                        }

                        DrawType.POLYGON -> {
                            binding.parentPolygonView.reset()
                            binding.parentPolygonView.visible()
                            binding.parentPolygonView.touchEnabled(false)
                            binding.parentPolygonView.points.addAll(
                                videoAnnotationData.annotations.polygonPoints(it)
                            )
                            binding.parentPolygonView.setImageBitmap(it)
                        }

                        DrawType.CONNECTED_LINE -> {
                            binding.parentPaintView.reset()
                            binding.parentPaintView.visible()
                            binding.parentPaintView.touchEnabled(false)
                            binding.parentPaintView.setBitmapAttributes(
                                it.width,
                                it.height
                            )
                            binding.parentPaintView.paintDataList.addAll(
                                videoAnnotationData.annotations
                                    .paintDataList(bitmap = it)
                            )
                            binding.parentPaintView.setImageBitmap(it)
                        }

                        DrawType.SPLIT_BOX -> {
                            binding.parentDragSplitView.reset()
                            binding.parentDragSplitView.visible()
                            binding.parentDragSplitView.touchEnabled(false)
                            binding.parentDragSplitView.setBitmapAttributes(
                                it.width, it.height
                            )
                            binding.parentDragSplitView.splitCropping
                                .addAll(videoAnnotationData.annotations.splitCrops(it))
                            binding.parentDragSplitView.setImageBitmap(it)
                        }

                        else -> {
                            binding.parentPhotoView.visible()
                            binding.parentPhotoView.setOnTouchListener { _, _ -> false }
                            binding.parentPhotoView.setImageBitmap(it)
                        }
                    }
                }
            }
        }
    }

    private fun hideAllParentAnnotationHints() {
        binding.parentCropView.gone()
        binding.parentDragSplitView.gone()
        binding.parentPaintView.gone()
        binding.parentQuadrilateralView.gone()
        binding.parentPolygonView.gone()
        binding.parentPhotoView.gone()
    }

    override fun showVideoModeInterpolationResult(
        sandboxResult: SandboxVideoAnnotationData,
        annotationObjectAttributes: List<AnnotationObjectsAttribute>
    ) {
        sandboxAnnotations.clear()
        sandboxAnnotations.addAll(annotationObjectAttributes)
        binding.sandboxResultContainer.visible()
        videoAnnotationSandboxAdapter?.add(mutableListOf(sandboxResult))
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun showVideoModeSandboxResult(
        sandboxResult: MutableList<SandboxVideoAnnotationData>,
        annotationObjectAttributes: List<AnnotationObjectsAttribute>
    ) {
        sandboxAnnotations.clear()
        sandboxAnnotations.addAll(annotationObjectAttributes)
        binding.sandboxResultContainer.visible()
        videoAnnotationSandboxAdapter?.add(sandboxResult)
    }

    override fun updateCurrentStreak(streak: Int, maxStreak: Int) {
        binding.sandboxProgress.max = maxStreak
        binding.sandboxProgress.progress = streak
    }

    override fun showVideoDownloadingProgress(isDownloading: Boolean, isIndeterminate: Boolean) {
        if (isDownloading) {
            showProgressDialog()
            binding.downloadingPb.isIndeterminate = isIndeterminate
        } else hideProgressDialog()
    }

    private fun showProgressDialog() {
        binding.foregroundDownloadContainer.visible()
        binding.downloadingPb.progress = 0
        binding.progressMessageTxt.text = getString(R.string.downloading_video_message)
    }

    fun hideProgressDialog() {
        binding.foregroundDownloadContainer.gone()
    }

    override fun updateDownloadingProgress(progress: Int) {
        if (progress >= 96) {
            binding.progressMessageTxt.text = getString(R.string.please_wait_extrating_frames)
            binding.downloadingPb.isIndeterminate = true
        }
        if (binding.foregroundDownloadContainer.isVisible)
            binding.downloadingPb.progress = progress
    }

    private fun setupWalkThrough() {
        if (canvasViewModel.user?.walkThroughEnabled == true) {
            if (canvasViewModel.isSandbox().not() || isSandboxPause) {
                val config = ShowcaseConfig()
                config.delay = 500
                config.renderOverNavigationBar = true

                val sequence = MaterialShowcaseSequence(
                    requireActivity(),
                    "VideoAnnotationFragment_InitialWalkThrough"
                )
                sequence.setOnItemDismissedListener { _, position ->
                    if (position == 0 && canvasViewModel.isSandbox()) {
                        binding.exoPlayerController.ivPlayPause.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_play
                            )
                        )
                    }
                    if (position == 1 && canvasViewModel.isSandbox()) {
                        binding.exoPlayerController.ivPlayPause.setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_pause
                            )
                        )
                    }
                    if (position >= 7) {
                        isWalkThroughRunning = false
                        showExoplayerControls()
                        if (canvasViewModel.isSandbox().not()) resumePlayer()
                    }
                }

                if (!sequence.hasFired()) {
                    sequence.setConfig(config)
                    sequence.addSequenceItem(
                        getShowCaseSeq(
                            binding.exoPlayerController.ivPlayPause,
                            "Click here to play the video.",
                            allowTargetTaps = canvasViewModel.isSandbox().not()
                        )
                    )
                    sequence.addSequenceItem(
                        getShowCaseSeq(
                            binding.exoPlayerController.ivPlayPause,
                            "Click here to pause the video.",
                            allowTargetTaps = canvasViewModel.isSandbox().not()
                        )
                    )
                    if (isInterpolationEnabled() && canvasViewModel.workType != WorkType.ANNOTATION) {
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.ivForward,
                                "Click here to fast forward.",
                                allowTargetTaps = canvasViewModel.isSandbox().not()
                            )
                        )
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.ivRewind,
                                "Click here to play slow.",
                                allowTargetTaps = canvasViewModel.isSandbox().not()
                            )
                        )
                    } else {
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.ivForwardByFrame,
                                "Click here to forward 30 MS.",
                                allowTargetTaps = canvasViewModel.isSandbox().not()
                            )
                        )
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.ivForward,
                                "Click here to forward 90 MS.",
                                allowTargetTaps = canvasViewModel.isSandbox().not()
                            )
                        )
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.ivRewindByFrame,
                                "Click here to rewind 30 MS.",
                                allowTargetTaps = canvasViewModel.isSandbox().not()
                            )
                        )
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.ivRewind,
                                "Click here to rewind 90 MS.",
                                allowTargetTaps = canvasViewModel.isSandbox().not()
                            )
                        )
                    }
                    if (canvasViewModel.workType == WorkType.ANNOTATION) {
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.ivCapture,
                                "Click here to capture the violation.",
                                allowTargetTaps = false
                            )
                        )
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.submitVideoIV,
                                "Click here to submit the violation.",
                                allowTargetTaps = false
                            )
                        )
                    } else {
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.approveTv,
                                "Click here to approve.",
                                allowTargetTaps = false
                            )
                        )
                        sequence.addSequenceItem(
                            getShowCaseSeq(
                                binding.exoPlayerController.rejectTv,
                                "Click here to reject.",
                                allowTargetTaps = false
                            )
                        )
                    }

                    if (isMediaPlaying) pausePlayer()
                    isWalkThroughRunning = true
                    showExoplayerControls()
                    sequence.start()
                }
            }
        }
    }

    private fun getShowCaseSeq(
        target: View,
        text: String,
        delay: Int = 100,
        allowTargetTaps: Boolean = true
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
        return materialShowcaseView.build()
    }

    override fun drawOverlays(annotations: MutableList<AnnotationData>, frameCount: Int?) {
        if (annotations.isEmpty()) {
            clearOverlays()
            return
        }
        this.frameCount = frameCount ?: 0
        val bitmap = getBitmapFromDirectory(
            canvasViewModel.totalFrames, this.frameCount,
            canvasViewModel.framesDir
        ) ?: return
        when (annotations.firstOrNull()?.type) {
            DrawType.BOUNDING_BOX -> {
                val translucentBitmap =
                    Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                binding.cropView.setBitmapAttributes(bitmap.width, bitmap.height)
                binding.cropView.crops.clear()
                binding.cropView.crops.addAll(annotations.crops(bitmap))
                Glide.with(requireContext()).load(translucentBitmap).into(binding.cropView)
            }
        }
    }

    override fun clearOverlays() {
        binding.cropView.reset()
        binding.cropView.invalidate()
    }

    private fun showPreviewWalkThrough() {
        if (canvasViewModel.user?.walkThroughEnabled == true) {
            MaterialShowcaseView.Builder(requireActivity())
                .setTarget(binding.interpolationSwitchBtn)
                .setTargetTouchable(true)
                .setDismissOnTargetTouch(true)
                .setDismissOnTouch(false)
                .setMaskColour(Color.parseColor("#CC000000"))
                .withRectangleShape()
                .setContentText(getString(R.string.walkthrough_interpolation))
                .setDelay(500)
                .singleUse("interpolationSwitchBtn")
                .show()

        }
    }

    companion object {
        const val APPROVE_VIDEO = "APPROVE_VIDEO"
        const val REJECT_VIDEO = "REJECT_VIDEO"
        const val SUBMIT_VIDEO = "SUBMIT_VIDEO"
        const val ANNOTATING_CHILD = "ANNOTATING_CHILD"
        const val INTERPOLATION_LINKING = "INTERPOLATION_LINKING"
        const val INTERPOLATION_SANDBOX = "INTERPOLATION_SANDBOX"

        const val PARENT_STEP_VIDEO_ANNOTATION = 1
        const val CHILD_STEP_VIDEO_ANNOTATION = 2
        const val PARENT_STEP_VIDEO_VALIDATION = 3
        const val CHILD_STEP_VIDEO_VALIDATION = 4
        const val PARENT_STEP_SANDBOX = 5
        const val CHILD_STEP_SANDBOX = 6
    }
}
