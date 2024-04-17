package co.nayan.canvas

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import co.nayan.appsession.SessionActivity
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3v2.core.widgets.ProgressDialogFragment
import co.nayan.c3views.crop.MaxCrops
import co.nayan.canvas.config.Thresholds.CROP_ERROR_IGNORANCE_THRESHOLD
import co.nayan.canvas.databinding.ActivityCanvasBinding
import co.nayan.canvas.interfaces.CanvasRepositoryInterface
import co.nayan.canvas.interfaces.SandboxRepositoryInterface
import co.nayan.canvas.modes.binary_classify.BinaryClassifyFragment
import co.nayan.canvas.modes.classify.ClassifyFragment
import co.nayan.canvas.modes.crop.CropFragment
import co.nayan.canvas.modes.drag_split.DragSplitFragment
import co.nayan.canvas.modes.input.InputFragment
import co.nayan.canvas.modes.input.LpInputFragment
import co.nayan.canvas.modes.paint.PaintFragment
import co.nayan.canvas.modes.polygon.PolygonFragment
import co.nayan.canvas.modes.quadrilateral.QuadrilateralFragment
import co.nayan.canvas.modes.validate.ValidateFragment
import co.nayan.canvas.utils.FFMPegExtraction
import co.nayan.canvas.utils.ImageCachingManager
import co.nayan.canvas.videoannotation.VideoAnnotationFragment
import co.nayan.canvas.viewmodels.BaseCanvasViewModel
import co.nayan.canvas.viewmodels.CanvasViewModel
import co.nayan.canvas.viewmodels.CanvasViewModelFactory
import co.nayan.canvas.viewmodels.VideoDownloadProvider
import co.nayan.canvas.widgets.CanvasStatusDialogClickListener
import co.nayan.canvas.widgets.CanvasStatusDialogFragment
import co.nayan.canvas.widgets.SniffingIncorrectWarningDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CanvasActivity : SessionActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils

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

    private lateinit var canvasViewModel: BaseCanvasViewModel
    private val binding: ActivityCanvasBinding by viewBinding(ActivityCanvasBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupFullScreenMode()
        setupViewModels()
        setupData()
        setupScreenOrientationMode()
    }

    private fun setupScreenOrientationMode() {
        requestedOrientation = when {
            canvasViewModel.mediaType == MediaType.VIDEO -> {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            canvasViewModel.enableLandscape() -> {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            canvasViewModel.disabledLandscape() -> {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            else -> {
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra(WORK_ASSIGNMENT_USED, false))
            canvasViewModel.assignWork()
        else intent.putExtra(WORK_ASSIGNMENT_USED, true)
        supportFragmentManager.findFragmentByTag("Canvas Status")?.let {
            if (it is CanvasStatusDialogFragment)
                it.setDialogClickListener(canvasStatusDialogClickListener)
        }
        supportFragmentManager.findFragmentByTag(getString(R.string.warning))?.let {
            if (it is SniffingIncorrectWarningDialogFragment)
                it.setViewModel(canvasViewModel)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupFullScreenMode() {
        window.apply {
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    decorView.systemUiVisibility =
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                }
            }
        }
    }

    private fun setupViewModels() {
        val viewModel = CanvasViewModelFactory(
            canvasRepositoryInterface,
            sandboxRepositoryInterface,
            false,
            imageCachingManager,
            videoDownloadProvider,
            ffmPegExtraction
        )
        canvasViewModel = ViewModelProvider(this, viewModel)[BaseCanvasViewModel::class.java]
        canvasViewModel.initVideoDownloadManager(this)
        canvasViewModel.state.observe(this, stateObserver)
    }

    fun hideProgressOverlay() {
        binding.progressOverlay.gone()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        val myFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        when (it) {
            InitialState -> binding.progressOverlay.gone()
            ProgressState -> binding.progressOverlay.visible()
            BaseCanvasViewModel.RecordDeleteState -> {
                binding.progressOverlay.gone()
                canvasViewModel.setupForNextRecord()
            }

            BaseCanvasViewModel.RecordsFinishedState -> {
                binding.progressOverlay.gone()
                showCanvasStatusDialog(getString(R.string.no_more_records), true)
            }

            CanvasViewModel.AnswersSubmittedState -> {
                this@CanvasActivity.finish()
            }

            BaseCanvasViewModel.SniffingIncorrectWarningState -> {
                showSniffingIncorrectWarningDialog()
            }

            is BaseCanvasViewModel.AccountLockedState -> {
                showAccountLockedDialog(it.incorrectSniffing, it.isAdmin, it.isAccountLocked)
            }

            is ErrorState -> {
                showCanvasStatusDialog(errorUtils.parseExceptionMessage(it.exception))
            }

            is BaseCanvasViewModel.VideoAnnotationDataState -> {
                if (it.videoAnnotationData.bitmap != null) {
                    getFragment()?.let { canvasFragment ->
                        supportFragmentManager.beginTransaction()
                            .add(R.id.fragmentContainer, canvasFragment)
                            .addToBackStack(canvasViewModel.applicationMode)
                            .commit()
                    }
                }
            }

            is BaseCanvasViewModel.DownloadingFailedState -> {
                binding.progressOverlay.gone()
                if (myFragment != null && myFragment.isVisible) {
                    if (myFragment is VideoAnnotationFragment) {
                        if (!myFragment.isMediaPlaybackCompleted)
                            myFragment.hideProgressDialog()
                    }
                }
                if (it.dataRecordsCorrupt.dataRecordsCorruptRecord.sniffing == true) {
                    canvasViewModel.deleteCorruptedRecord(it.dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId)
                    canvasViewModel.setupForNextRecord()
                } else {
                    canvasViewModel.isRecordCorruptedCalledCount++
                    if (canvasViewModel.isRecordCorruptedCalledCount < 4)
                        canvasViewModel.sendCorruptCallback(it.dataRecordsCorrupt)
                    else {
                        canvasViewModel.isRecordCorruptedCalledCount = 0
                        showCanvasStatusDialog(
                            getString(R.string.downloading_failed_video_message)
                                .format(it.dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId),
                            true
                        )
                    }
                }
            }

            is BaseCanvasViewModel.FrameExtractionFailedState -> {
                binding.progressOverlay.gone()
                if (myFragment != null && myFragment.isVisible) {
                    if (myFragment is VideoAnnotationFragment) {
                        if (!myFragment.isMediaPlaybackCompleted)
                            myFragment.hideProgressDialog()
                    }
                }
                if (it.dataRecordsCorrupt.dataRecordsCorruptRecord.sniffing == true) {
                    canvasViewModel.deleteCorruptedRecord(it.dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId)
                    canvasViewModel.setupForNextRecord()
                } else {
                    canvasViewModel.isRecordCorruptedCalledCount++
                    if (canvasViewModel.isRecordCorruptedCalledCount < 4)
                        canvasViewModel.sendCorruptCallback(it.dataRecordsCorrupt)
                    else {
                        showCanvasStatusDialog(
                            getString(R.string.extraction_failed_video_message)
                                .format(it.dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId),
                            true
                        )
                    }
                }
            }
        }
    }

    private fun showSniffingIncorrectWarningDialog() {
        supportFragmentManager.fragments.forEach {
            if (it is SniffingIncorrectWarningDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
            }
        }

        val sniffingWarningFragment =
            SniffingIncorrectWarningDialogFragment.newInstance(getString(R.string.incorrect_sniffing_warning))
        sniffingWarningFragment.setViewModel(canvasViewModel)
        sniffingWarningFragment.show(
            supportFragmentManager.beginTransaction(),
            getString(R.string.warning)
        )
        canvasViewModel.submitIncorrectSniffingRecords()
    }

    private fun showAccountLockedDialog(
        incorrectSniffingRecords: ArrayList<Record>,
        isAdmin: Boolean,
        isAccountLocked: Boolean
    ) {
        val message = if (isAdmin) String.format(
            getString(R.string.account_locked),
            getString(R.string.manager)
        )
        else getString(R.string.sandbox_enabled)

        supportFragmentManager.fragments.forEach {
            if (it is CanvasStatusDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val canvasStatusDialogFragment = CanvasStatusDialogFragment.newInstance(
            incorrectSniffingRecords.isNotEmpty(),
            message,
            null,
            true,
            isAccountLocked
        )
        canvasStatusDialogFragment.setDialogClickListener(object : CanvasStatusDialogClickListener {
            override fun onPositiveBtnClick(shouldFinish: Boolean) {
                this@CanvasActivity.finish()
            }

            override fun onNegativeBtnClick(shouldFinish: Boolean) {
                moveToIncorrectReviewsScreen(incorrectSniffingRecords)
            }
        })
        canvasStatusDialogFragment.show(supportFragmentManager.beginTransaction(), "Canvas Status")
    }

    private fun moveToIncorrectReviewsScreen(incorrectSniffingRecords: ArrayList<Record>) {
        Intent(
            this@CanvasActivity,
            canvasRepositoryInterface.incorrectReviewsRecordsActivityClass()
        ).apply {
            putExtra(APPLICATION_MODE, canvasViewModel.applicationMode)
            putExtra(CONTRAST_VALUE, canvasViewModel.getContrast())
            putParcelableArrayListExtra(RECORDS, incorrectSniffingRecords)
            putExtra(QUESTION, canvasViewModel.question)
            putExtra(APP_FLAVOR, intent.getStringExtra(APP_FLAVOR))
            startActivity(this)
        }
    }

    private fun setupData() {
        val workAssignment = intent.parcelable<WorkAssignment>(WORK_ASSIGNMENT)
        canvasViewModel.user = intent.parcelable(USER)
        if (workAssignment != null) {
            canvasViewModel.workAssignmentId = workAssignment.id
            canvasViewModel.userCategory = workAssignment.userCategory
            canvasViewModel.question = workAssignment.wfStep?.question ?: ""
            canvasViewModel.workType = workAssignment.workType
            canvasViewModel.mediaType = workAssignment.wfStep?.mediaType
            canvasViewModel.applicationMode = workAssignment.applicationMode
            canvasViewModel.wfStepId = workAssignment.wfStep?.id
            canvasViewModel.isImageProcessingEnabled =
                workAssignment.wfStep?.aiAssistEnabled ?: false
            canvasViewModel.cameraAiModel = workAssignment.wfStep?.cameraAiModel
            setupCanvasMode()
            canvasViewModel.setLearningVideoMode()
            canvasViewModel.appFlavor = intent.getStringExtra(APP_FLAVOR)
            canvasViewModel.annotationVariationThreshold =
                workAssignment.wfStep?.annotationVariationThreshold
                    ?: CROP_ERROR_IGNORANCE_THRESHOLD
            setMetaData(
                workAssignment.id,
                workAssignment.wfStep?.id,
                workAssignment.workType,
                canvasViewModel.currentRole()
            )
        } else this@CanvasActivity.finish()
    }

    private fun setupCanvasMode() {
        val canvasFragment = when (canvasViewModel.mediaType) {
            MediaType.VIDEO -> {
                VideoAnnotationFragment()
            }

            MediaType.CLASSIFICATION_VIDEO -> {
                when (canvasViewModel.workType) {
                    WorkType.VALIDATION -> ValidateFragment()
                    else -> getFragment()
                }
            }

            else -> {
                when (canvasViewModel.workType) {
                    WorkType.VALIDATION -> ValidateFragment()
                    else -> getFragment()
                }
            }
        }

        if (canvasFragment != null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, canvasFragment)
                .commit()
        } else showCanvasStatusDialog(getString(R.string.something_went_wrong))
    }

    private fun getFragment(): CanvasFragment? {
        return when (canvasViewModel.applicationMode) {
            Mode.INPUT -> InputFragment.newInstance(isMultiInput = false)
            Mode.MULTI_INPUT -> InputFragment.newInstance(isMultiInput = true)
            Mode.LP_INPUT -> LpInputFragment()

            Mode.CROP -> CropFragment.newInstance(maxCrops = MaxCrops.CROP)
            Mode.MULTI_CROP -> CropFragment.newInstance(maxCrops = MaxCrops.MULTI_CROP)
            Mode.BINARY_CROP -> CropFragment.newInstance(maxCrops = MaxCrops.BINARY_CROP)
            Mode.MCMI -> CropFragment.newInstance(maxCrops = MaxCrops.MULTI_CROP, isInput = true)
            Mode.MCML -> CropFragment.newInstance(maxCrops = MaxCrops.MULTI_CROP, isLabel = true)
            Mode.INTERPOLATED_MCML -> CropFragment.newInstance(
                maxCrops = MaxCrops.MULTI_CROP,
                isInterpolation = true
            )

            Mode.INTERPOLATED_MCMT -> CropFragment.newInstance(
                maxCrops = MaxCrops.MULTI_CROP,
                isInterpolation = true
            )

            Mode.MCMT -> CropFragment.newInstance(
                maxCrops = MaxCrops.MULTI_CROP,
                isMultiLabel = true
            )

            Mode.CLASSIFY -> ClassifyFragment()
            Mode.BINARY_CLASSIFY -> BinaryClassifyFragment()

            Mode.PAINT -> PaintFragment()
            Mode.QUADRILATERAL -> QuadrilateralFragment()
            Mode.POLYGON -> PolygonFragment()
            Mode.DRAG_SPLIT -> DragSplitFragment()
            Mode.DYNAMIC_CLASSIFY -> ClassifyFragment()
            Mode.EVENT_VALIDATION -> ClassifyFragment()
            else -> null
        }
    }

    private val canvasStatusDialogClickListener = object : CanvasStatusDialogClickListener {
        override fun onPositiveBtnClick(shouldFinish: Boolean) {
            if (shouldFinish) {
                if (canvasViewModel.isAllAnswersSubmitted()) this@CanvasActivity.finish()
                else {
                    showProgressDialog()
                    canvasViewModel.submitSavedAnswers()
                }
            }
        }

        override fun onNegativeBtnClick(shouldFinish: Boolean) {
            if (shouldFinish) {
                canvasViewModel.clearAnswers()
                this@CanvasActivity.finish()
            }
        }
    }

    private fun showCanvasStatusDialog(message: String, shouldFinish: Boolean = true) {
        val shouldShowNegativeBtn = message == getString(R.string.something_went_wrong)
        supportFragmentManager.fragments.forEach {
            if (it is CanvasStatusDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val canvasStatusDialogFragment = CanvasStatusDialogFragment.newInstance(
            shouldShowNegativeBtn,
            message,
            null,
            shouldFinish,
            false
        )
        canvasStatusDialogFragment.setDialogClickListener(canvasStatusDialogClickListener)
        canvasStatusDialogFragment.show(supportFragmentManager.beginTransaction(), "Canvas Status")
    }

    private fun showProgressDialog() {
        hideProgressDialog()
        val progressDialog = ProgressDialogFragment()
        progressDialog.setMessage(getString(R.string.submitting_records))
        progressDialog.show(supportFragmentManager.beginTransaction(), "Submitting Answers")
    }

    private fun hideProgressDialog() {
        supportFragmentManager.fragments.forEach {
            if (it is ProgressDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
    }

    override fun onBackPressed() {
        val fragment = supportFragmentManager.fragments.firstOrNull()
        if (canvasViewModel.annotatingFrame) {
            super.onBackPressed()
            canvasViewModel.annotatingFrame = false
        } else {
            if (fragment is BinaryClassifyFragment) {
                if (fragment.onBackPressed()) super.onBackPressed()
            } else {
                if (canvasViewModel.isAllAnswersSubmitted())
                    super.onBackPressed()
                else {
                    showProgressDialog()
                    canvasViewModel.submitSavedAnswers()
                }
            }
        }
    }

    companion object {
        const val WORK_ASSIGNMENT = "work_assignment"
        const val WORK_ASSIGNMENT_USED = "work_assignment_used"
        const val APP_FLAVOR = "app_flavor"
        const val CONTRAST_VALUE = "contrastValue"
        const val APPLICATION_MODE = "applicationMode"
        const val RECORDS = "records"
        const val QUESTION = "question"
        const val USER = "USER"
    }
}