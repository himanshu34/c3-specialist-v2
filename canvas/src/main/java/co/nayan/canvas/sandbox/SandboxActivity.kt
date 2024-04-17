package co.nayan.canvas.sandbox

import android.app.Activity
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
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.InitialState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Question
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.WfStep
import co.nayan.c3v2.core.models.c3_module.responses.SandboxTrainingResponse
import co.nayan.c3v2.core.utils.Constants.Extras
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.crop.MaxCrops
import co.nayan.c3views.utils.getAnnotationAttribute
import co.nayan.canvas.CanvasFragment
import co.nayan.canvas.R
import co.nayan.canvas.config.Thresholds.CROP_ERROR_IGNORANCE_THRESHOLD
import co.nayan.canvas.config.TrainingStatus
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
import co.nayan.canvas.sandbox.models.LearningImageData
import co.nayan.canvas.sandbox.widgets.LearningImageDialogFragment
import co.nayan.canvas.sandbox.widgets.SandboxStatusDialogClickListener
import co.nayan.canvas.sandbox.widgets.SandboxStatusDialogFragment
import co.nayan.canvas.utils.FFMPegExtraction
import co.nayan.canvas.utils.ImageCachingManager
import co.nayan.canvas.videoannotation.VideoAnnotationFragment
import co.nayan.canvas.viewBinding
import co.nayan.canvas.viewmodels.BaseCanvasViewModel
import co.nayan.canvas.viewmodels.CanvasViewModelFactory
import co.nayan.canvas.viewmodels.SandboxViewModel
import co.nayan.canvas.viewmodels.VideoDownloadProvider
import co.nayan.canvas.widgets.CanvasStatusDialogClickListener
import co.nayan.canvas.widgets.CanvasStatusDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SandboxActivity : SessionActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils

    @Inject
    lateinit var canvasRepository: CanvasRepositoryInterface

    @Inject
    lateinit var sandboxRepository: SandboxRepositoryInterface

    @Inject
    lateinit var imageCachingManager: ImageCachingManager

    @Inject
    lateinit var videoDownloadProvider: VideoDownloadProvider

    @Inject
    lateinit var ffmPegExtraction: FFMPegExtraction

    private lateinit var sandboxViewModel: BaseCanvasViewModel
    private val binding: ActivityCanvasBinding by viewBinding(ActivityCanvasBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupFullScreenMode()
        setupViewModels()
        setupData()
        setupScreenOrientationMode()

        if (savedInstanceState == null) {
            if (sandboxViewModel.isAdminRole().not()
                && (sandboxViewModel.mediaType == MediaType.VIDEO
                        || sandboxViewModel.areRecordsFetched.not())
            ) {
                showCanvasStatusDialog(getString(R.string.sandbox_intro), false)
            }
        }
    }

    override fun onDestroy() {
        sandboxViewModel.setInitialState()
        sandboxViewModel.state.removeObserver(stateObserver)
        super.onDestroy()
    }

    private fun setupScreenOrientationMode() {
        requestedOrientation = when {
            sandboxViewModel.mediaType == MediaType.VIDEO -> {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            sandboxViewModel.enableLandscape() -> {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            sandboxViewModel.disabledLandscape() -> {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            else -> {
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
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
            canvasRepository,
            sandboxRepository,
            true,
            imageCachingManager,
            videoDownloadProvider,
            ffmPegExtraction
        )
        sandboxViewModel = ViewModelProvider(this, viewModel).get(BaseCanvasViewModel::class.java)
        sandboxViewModel.initVideoDownloadManager(this)
        sandboxViewModel.state.observe(this, stateObserver)
    }

    fun hideProgressOverlay() {
        binding.progressOverlay.gone()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> binding.progressOverlay.gone()
            ProgressState -> binding.progressOverlay.visible()
            SandboxViewModel.SandboxSuccessState -> {
                getString(R.string.sandbox_success)
                showSandboxStatusDialog(TrainingStatus.SUCCESS)
            }

            SandboxViewModel.SandboxFailedState -> {
                showSandboxStatusDialog(TrainingStatus.FAILED)
            }

            SandboxViewModel.CorrectAnnotationState -> {
                binding.progressOverlay.gone()
                showSandboxStatusDialog(TrainingStatus.IN_PROGRESS)
            }

            SandboxViewModel.RecordSubmissionFailedState -> {
                binding.progressOverlay.gone()
                showCanvasStatusDialog(getString(R.string.failed_to_submit_record), false)
            }

            is SandboxViewModel.LearningImageSetupState -> {
                showLearningImageDialog(it.learningImageData)
            }

            BaseCanvasViewModel.RecordsFinishedState -> {
                binding.progressOverlay.gone()
                var message = getString(R.string.no_more_records)
                if (sandboxViewModel.isSandboxCreationMode)
                    message = getString(R.string.sandbox_submitted_successfully)
                showCanvasStatusDialog(message)
            }

            is BaseCanvasViewModel.VideoAnnotationDataState -> {
                if (it.videoAnnotationData.bitmap != null) {
                    getFragment()?.let { fragment ->
                        supportFragmentManager.beginTransaction()
                            .add(R.id.fragmentContainer, fragment)
                            .addToBackStack(sandboxViewModel.applicationMode)
                            .commit()
                    }
                }
            }

            is ErrorState -> {
                binding.progressOverlay.gone()
                val errorMessage = errorUtils.parseExceptionMessage(it.exception)
                showCanvasStatusDialog(errorMessage, false)
            }

            is BaseCanvasViewModel.DownloadingFailedState -> {
                showCanvasStatusDialog(
                    getString(R.string.downloading_failed_video_message)
                        .format(it.dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId), true
                )
            }

            is BaseCanvasViewModel.FrameExtractionFailedState -> {
                showCanvasStatusDialog(
                    getString(R.string.extraction_failed_video_message)
                        .format(it.dataRecordsCorrupt.dataRecordsCorruptRecord.dataRecordId), true
                )
            }
        }
    }

    private fun setupData() {
        sandboxViewModel.workType = WorkType.ANNOTATION
        val sandboxAssignment = intent.parcelable<SandboxTrainingResponse>(SANDBOX_ASSIGNMENT)
        if (sandboxAssignment != null) {
            sandboxViewModel.setTrainingId(sandboxAssignment.sandboxTrainingId ?: 0)
            sandboxViewModel.question = sandboxAssignment.question?.question()
            sandboxViewModel.role = Role.SPECIALIST
            sandboxViewModel.applicationMode = sandboxAssignment.applicationMode
            sandboxViewModel.wfStepId = sandboxAssignment.wfStepId
            sandboxViewModel.mediaType = sandboxAssignment.mediaType
            sandboxViewModel.annotationVariationThreshold =
                sandboxAssignment.annotationVariationThreshold ?: CROP_ERROR_IGNORANCE_THRESHOLD
            setupCanvasMode()
            sandboxViewModel.setLearningVideoMode()
            setMetaData(
                sandboxAssignment.sandboxTrainingId,
                null,
                sandboxViewModel.workType,
                sandboxViewModel.role
            )
        } else {
            intent.parcelable<Record>(Extras.SANDBOX_RECORD)?.let {
                sandboxViewModel.setSandboxRecord(it)
            }
            val wfStep = intent.parcelable<WfStep>("wfStep")
            sandboxViewModel.setTrainingId(wfStep?.sandboxId ?: 0)
            sandboxViewModel.question = wfStep?.question ?: ""
            sandboxViewModel.role = Role.ADMIN
            sandboxViewModel.applicationMode = wfStep?.applicationModeName
            sandboxViewModel.wfStepId = wfStep?.id
            sandboxViewModel.mediaType = wfStep?.mediaType
            sandboxViewModel.annotationVariationThreshold =
                wfStep?.annotationVariationThreshold ?: CROP_ERROR_IGNORANCE_THRESHOLD
            setupCanvasMode()
            setMetaData(
                null,
                wfStep?.id,
                sandboxViewModel.workType,
                sandboxViewModel.role
            )
        }
    }

    private fun setupCanvasMode() {
        val canvasFragment = when (sandboxViewModel.mediaType) {
            MediaType.VIDEO -> {
                VideoAnnotationFragment()
            }

            MediaType.CLASSIFICATION_VIDEO -> {
                when (sandboxViewModel.workType) {
                    WorkType.VALIDATION -> ValidateFragment()
                    else -> getFragment()
                }
            }

            else -> {
                when (sandboxViewModel.workType) {
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
        return when (sandboxViewModel.applicationMode) {
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
                if (sandboxViewModel.isSandboxCreationMode && sandboxViewModel.sandboxRefreshDataRequest != null) {
                    setResult(Activity.RESULT_OK, Intent().apply {
                        putExtra(
                            "SandboxData",
                            sandboxViewModel.sandboxRefreshDataRequest
                        )
                    })
                }
                this@SandboxActivity.finish()
            }
        }

        override fun onNegativeBtnClick(shouldFinish: Boolean) {
            if (shouldFinish) this@SandboxActivity.finish()
        }
    }

    override fun onResume() {
        super.onResume()
        supportFragmentManager.findFragmentByTag("Sandbox Status")?.let {
            if (it is CanvasStatusDialogFragment)
                it.setDialogClickListener(canvasStatusDialogClickListener)
        }
    }

    private fun showCanvasStatusDialog(
        message: String,
        shouldFinish: Boolean = true,
        title: String = getString(R.string.alert)
    ) {
        supportFragmentManager.fragments.forEach {
            if (it is CanvasStatusDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val canvasStatusDialogFragment = CanvasStatusDialogFragment.newInstance(
            false,
            message,
            title,
            shouldFinish,
            false
        )
        canvasStatusDialogFragment.setDialogClickListener(canvasStatusDialogClickListener)
        canvasStatusDialogFragment.show(supportFragmentManager.beginTransaction(), "Sandbox Status")
    }

    private val sandboxDialogClickListener =
        object : SandboxStatusDialogClickListener {
            override fun onClick(shouldFinish: Boolean) {
                if (shouldFinish) {
                    this@SandboxActivity.finish()
                }
            }
        }

    private fun showSandboxStatusDialog(status: String) {
        supportFragmentManager.fragments.forEach {
            if (it is SandboxStatusDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val sandboxStatusDialog =
            SandboxStatusDialogFragment.newInstance(sandboxDialogClickListener)
        sandboxStatusDialog.setStatus(status)
        sandboxStatusDialog.show(supportFragmentManager, "Sandbox Status")
    }

    private fun showLearningImageDialog(learningImageData: LearningImageData) {
        supportFragmentManager.fragments.forEach {
            if (it is LearningImageDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }

        LearningImageDialogFragment.newInstance(
            learningImageData, sandboxViewModel.applicationMode, sandboxFailureListener
        ).show(supportFragmentManager, "Learning Image")
    }

    private val sandboxFailureListener = object : SandboxFailureListener {
        override fun onConfirm(
            applicationMode: String?,
            correctAnnotationList: List<AnnotationData>?
        ) {
            correctAnnotationList?.let { correctAnnotations ->
                val annotationObjectsAttributes = mutableListOf<AnnotationObjectsAttribute>()
                correctAnnotations.forEach {
                    annotationObjectsAttributes.add(getAnnotationAttribute(it))
                }
                sandboxViewModel.setAnnotationObjectiveAttributes(annotationObjectsAttributes)
                sandboxViewModel.resetViews?.invoke(applicationMode, correctAnnotations)
            }
        }
    }

    fun Question?.question(): String {
        return when {
            (this == null) -> ""
            (Locale.getDefault().language == "en") -> en ?: ""
            else -> hi ?: ""
        }
    }

    companion object {
        const val SANDBOX_ASSIGNMENT = "sandboxAssignment"
    }
}

interface SandboxFailureListener {
    fun onConfirm(applicationMode: String?, correctAnnotationList: List<AnnotationData>?)
}