package co.nayan.canvas

import android.content.Intent
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.SeekBar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.utils.ImageUtils
import co.nayan.c3v2.core.utils.OnSeekBarChangeListener
import co.nayan.canvas.interfaces.CanvasRepositoryInterface
import co.nayan.canvas.interfaces.SandboxRepositoryInterface
import co.nayan.canvas.sandbox.SandboxActivity
import co.nayan.canvas.utils.FFMPegExtraction
import co.nayan.canvas.utils.ImageCachingManager
import co.nayan.canvas.utils.TextToSpeechConstants
import co.nayan.canvas.utils.TextToSpeechUtils
import co.nayan.canvas.viewmodels.BaseCanvasViewModel
import co.nayan.canvas.viewmodels.CanvasViewModelFactory
import co.nayan.canvas.viewmodels.SandboxViewModel
import co.nayan.canvas.viewmodels.VideoDownloadProvider
import co.nayan.tutorial.LearningVideoPlayerActivity
import co.nayan.tutorial.config.LearningVideosExtras.LEARNING_VIDEO
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

abstract class CanvasFragment(layoutID: Int) : BaseFragment(layoutID) {

    protected lateinit var canvasViewModel: BaseCanvasViewModel

    @Inject
    lateinit var canvasRepositoryInterface: CanvasRepositoryInterface

    @Inject
    lateinit var sandboxRepositoryInterface: SandboxRepositoryInterface

    @Inject
    lateinit var imageCachingManager: ImageCachingManager

    @Inject
    lateinit var videoDownloadProvider: VideoDownloadProvider

    @Inject
    lateinit var ffmPegExtraction: FFMPegExtraction

    private lateinit var textToSpeech: TextToSpeech

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel()
        textToSpeech = TextToSpeech(context, textToSpeechListener, "com.google.android.tts")
        setupContrastSlider(canvasViewModel.getContrast())
        setupObservers()
        if (canvasViewModel.areRecordsFetched.not())
            canvasViewModel.fetchRecordsFirstTime()
    }

    private fun initViewModel() {
        val isSandbox = requireActivity()::class.java.name == SandboxActivity::class.java.name
        canvasViewModel = ViewModelProvider(
            requireActivity(),
            CanvasViewModelFactory(
                canvasRepositoryInterface,
                sandboxRepositoryInterface,
                isSandbox,
                imageCachingManager,
                videoDownloadProvider,
                ffmPegExtraction
            )
        ).get(BaseCanvasViewModel::class.java)
    }

    /**
     *[observeAllRecords] returns true if wants to populate all records and
     * [recordsObserver] will receive events otherwise [recordObserver] will receive the events.
     *[undoRecordObserver] will receive events related that user can undo record or not
     * If user is in sandbox and user is a specialist and if user annotates some incorrect annotations
     **/
    private fun setupObservers() {
        if (canvasViewModel.mediaType == MediaType.VIDEO) {
            canvasViewModel.state
                .observe(viewLifecycleOwner, videoAnnotationModeObserver)
            canvasViewModel.record.observe(viewLifecycleOwner, recordObserver)
            canvasViewModel.foregroundVideoDownloading
                .observe(viewLifecycleOwner, videoDownloadingObserver)
            canvasViewModel.downloadingProgress
                .observe(viewLifecycleOwner, downloadingProgressObserver)
        } else {
            if (observeAllRecords()) canvasViewModel.records.observe(
                viewLifecycleOwner,
                recordsObserver
            )
            else canvasViewModel.record.observe(viewLifecycleOwner, recordObserver)
        }
        canvasViewModel.sandboxCurrentStreak.observe(viewLifecycleOwner, sandboxStreakObserver)
        canvasViewModel.isOpenCvInitialized.observe(viewLifecycleOwner) { isInitialized ->
            if (isInitialized) onOpenCVInitialized()
        }
        canvasViewModel.canUndo.observe(viewLifecycleOwner, undoRecordObserver)
        canvasViewModel.learningVideoMode.observe(viewLifecycleOwner, learningVideoModeObserver)
    }

    private val sandboxStreakObserver: Observer<Int> = Observer { streak ->
        streak.let {
            val requiredStreak = canvasViewModel.requiredStreak
            if (requiredStreak != null && requiredStreak >= streak) {
                updateCurrentStreak(streak, requiredStreak)
            }
        }
    }

    private val recordsObserver: Observer<List<Record>> = Observer {
        canvasViewModel.setupUndoRecordState()
        populateAllRecords(it)
    }

    private val videoDownloadingObserver: Observer<Boolean> = Observer {
        showVideoDownloadingProgress(it)
    }

    private val downloadingProgressObserver: Observer<Int> = Observer {
        updateDownloadingProgress(it)
    }

    private val videoAnnotationModeObserver: Observer<ActivityState> = Observer {
        when (it) {
            is InitialState -> {
                when (requireActivity()) {
                    is CanvasActivity -> {
                        (requireActivity() as CanvasActivity).hideProgressOverlay()
                    }

                    is SandboxActivity -> {
                        (requireActivity() as SandboxActivity).hideProgressOverlay()
                    }
                }
            }
            is BaseCanvasViewModel.VideoAnnotationDataState -> {
                if (it.videoAnnotationData.bitmap != null)
                    populateBitmap(it.videoAnnotationData)
            }
            is BaseCanvasViewModel.RefreshVideoAnnotationModeState -> {
                refreshVideoAnnotationSlider()
            }
            is SandboxViewModel.ToggleScreenCaptureState -> {
                toggleScreenCaptureState(it.status)
            }
            is SandboxViewModel.VideoModeInterpolationResultState -> {
                showVideoModeInterpolationResult(it.sandboxResult, it.annotationObjectsAttributes)
            }
            is SandboxViewModel.VideoModeSandboxResultState -> {
                showVideoModeSandboxResult(it.sandboxResult, it.annotationObjectsAttributes)
            }
            SandboxViewModel.VideoModeNextSandboxState -> {
                annotateNextSandbox()
            }
            is BaseCanvasViewModel.DrawAnnotationState -> {
                drawOverlays(it.annotations, it.frameCount)
            }
            BaseCanvasViewModel.ClearAnnotationState -> {
                clearOverlays()
            }
        }
    }

    private val recordObserver: Observer<Record> = Observer {
        canvasViewModel.setupUndoRecordState()
        if (canvasViewModel.mediaType == MediaType.VIDEO) populateVideoRecord(it)
        else populateRecord(it)
    }

    private val undoRecordObserver: Observer<Boolean> = Observer {
        enableUndo(isEnabled = it)
    }

    private val learningVideoModeObserver: Observer<String?> = Observer {
        setupHelpButton(it)
    }

    protected val onSeekBarChangedListener = object : OnSeekBarChangeListener() {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            val colorFilter = ImageUtils.getColorMatrix(progress)
            updateContrast(colorFilter)
            canvasViewModel.saveContrastValue(progress)
        }
    }

    protected val onThickChangeListener = object : OnSeekBarChangeListener() {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            super.onProgressChanged(seekBar, progress, p2)
            updateThickness(progress.toFloat())
        }
    }

    private val textToSpeechListener = TextToSpeech.OnInitListener {
        if (it == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.getDefault()
            textToSpeech.setPitch(TextToSpeechConstants.PITCH)
            textToSpeech.setSpeechRate(TextToSpeechConstants.SPEED_RATE)
        }
    }

    protected fun showPreviousButton(): Boolean = canvasViewModel.mediaType != MediaType.VIDEO

    protected fun speakOut(text: String?) {
        textToSpeech.stop()
        if (text.isNullOrEmpty()) {
            return
        } else {
            val updatedText = TextToSpeechUtils.getSeparatedTextByNumbers(text)
            textToSpeech.speak(updatedText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    protected fun moveToLearningVideoScreen() {
        val mode = canvasViewModel.learningVideoMode.value ?: return
        lifecycleScope.launch {
            val learningVideo = canvasViewModel.getLearningVideo(mode)
            if (learningVideo != null) {
                Intent(activity, LearningVideoPlayerActivity::class.java).apply {
                    putExtra(LEARNING_VIDEO, learningVideo)
                    startActivity(this)
                }
            } else showMessage(String.format(getString(R.string.no_learning_video_found), mode))
        }
    }

    protected fun appFlavor() = canvasViewModel.appFlavor

    protected fun shouldUseSandboxProgress() =
        canvasViewModel.isSandbox() && canvasViewModel.isAdminRole().not()

    protected fun getRecordIdText(record: Record): String {
        val id = if (appFlavor() != "qa" && record.isSniffingRecord == true)
            record.randomSniffingId else record.id
        return String.format(getString(R.string.record_id_text), id)
    }

    protected fun isInterpolationEnabled() =
        (canvasViewModel.isInterpolatedMCML() || canvasViewModel.isInterpolatedMCMT())

    abstract fun setupHelpButton(applicationMode: String?)

    open fun setupContrastSlider(progress: Int) = Unit
    open fun updateContrast(colorFilter: ColorMatrixColorFilter) = Unit
    open fun showMessage(message: String) = Unit

    open fun disabledJudgmentButtons() = Unit
    open fun enabledJudgmentButtons() = Unit

    open fun observeAllRecords(): Boolean = false
    open fun refreshVideoAnnotationSlider() = Unit
    open fun toggleScreenCaptureState(status: Boolean) = Unit
    open fun showVideoModeInterpolationResult(
        sandboxResult: SandboxVideoAnnotationData,
        annotationObjectAttributes: List<AnnotationObjectsAttribute>
    ) = Unit

    open fun showVideoModeSandboxResult(
        sandboxResult: MutableList<SandboxVideoAnnotationData>,
        annotationObjectAttributes: List<AnnotationObjectsAttribute>
    ) = Unit

    open fun populateRecord(record: Record) = Unit
    open fun populateVideoRecord(record: Record) = Unit
    open fun populateBitmap(videoAnnotationData: VideoAnnotationData) = Unit
    open fun populateAllRecords(records: List<Record>) = Unit

    open fun undo(i: Int) = 0
    open fun enableUndo(isEnabled: Boolean) = Unit
    open fun updateThickness(value: Float) = Unit
    open fun onOpenCVInitialized() = Unit
    open fun updateCurrentStreak(streak: Int, maxStreak: Int) = Unit
    open fun showVideoDownloadingProgress(
        isDownloading: Boolean,
        isIndeterminate: Boolean = false
    ) = Unit

    open fun updateDownloadingProgress(progress: Int) = Unit
    open fun drawOverlays(annotations: MutableList<AnnotationData>, frameCount: Int?) = Unit
    open fun clearOverlays() = Unit
    open fun annotateNextSandbox() = Unit
}