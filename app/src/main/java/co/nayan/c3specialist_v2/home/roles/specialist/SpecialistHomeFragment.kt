package co.nayan.c3specialist_v2.home.roles.specialist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.config.LearningVideosCategory.CURRENT_ROLE
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.FragmentSpecialistHomeBinding
import co.nayan.c3specialist_v2.home.roles.RoleBaseFragment
import co.nayan.c3specialist_v2.performance.specialistperformance.SpecialistPerformanceActivity
import co.nayan.c3specialist_v2.specialistworksummary.SpecialistWorkSummaryActivity
import co.nayan.c3specialist_v2.videogallery.LearningVideosPlaylistActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.config.Role.SPECIALIST
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.models.c3_module.responses.SandboxTrainingResponse
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.sandbox.SandboxActivity
import co.nayan.review.models.ReviewIntentInputData
import co.nayan.review.recordsgallery.ReviewActivity
import co.nayan.review.recordsreview.ReviewModeActivity
import co.nayan.tutorial.utils.LearningVideosContractInput
import co.nayan.tutorial.utils.LearningVideosResultCallback
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import javax.inject.Inject

@AndroidEntryPoint
class SpecialistHomeFragment : RoleBaseFragment(R.layout.fragment_specialist_home) {

    private val specialistViewModel: SpecialistViewModel by viewModels()
    private val binding by viewBinding(FragmentSpecialistHomeBinding::bind)

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.userEmailTxt.text = specialistViewModel.getUserEmail()
        binding.homeMessageTxt.text = String.format(
            getString(R.string.home_screen_message), specialistViewModel.getUserName()
        )
        specialistViewModel.state.observe(viewLifecycleOwner, stateObserver)
        specialistViewModel.stats.observe(viewLifecycleOwner, userStatsObserver)
        setupClicks()

        binding.pullToRefresh.setOnRefreshListener {
            specialistViewModel.fetchUserStats()
        }

        if (activity is DashboardActivity)
            (activity as DashboardActivity).updateHomeBackground(CurrentRole.SPECIALIST)

        specialistViewModel.getSpecialistIntroVideo()
    }

    override fun onResume() {
        super.onResume()
        specialistViewModel.fetchUserStats()
    }

    private val launchLearningVideoActivity =
        registerForActivityResult(LearningVideosResultCallback()) {
            specialistViewModel.saveIntroVideoCompleted()
        }

    private fun moveToLearningVideoScreen(video: Video?) {
        if (video == null) return
        else {
            launchLearningVideoActivity.launch(
                LearningVideosContractInput(
                    showDoneButton = true,
                    video = video,
                    workAssignment = null
                )
            )
        }
    }

    private val userStatsObserver: Observer<StatsResponse?> = Observer {
        val stats = it?.stats
        binding.potentialPointsTxt.text = stats?.potentialScore ?: "0.0"
        binding.hoursWorkedTxt.text = stats?.workDuration ?: "00:00"
        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(stats?.lastUpdatedAt ?: "--")

        binding.specialistStatsLayout.totalAnnotationsTxt.text =
            (stats?.totalAnnotations ?: 0).toString()
        binding.specialistStatsLayout.completedAnnotationsTxt.text =
            (stats?.completedAnnotations ?: 0).toString()
        binding.specialistStatsLayout.correctAnnotationsTxt.text =
            (stats?.correctAnnotations ?: 0).toString()
        binding.specialistStatsLayout.incorrectAnnotationsTxt.text =
            (stats?.incorrectAnnotations ?: 0).toString()

        binding.specialistStatsLayout.totalJudgmentsTxt.text =
            (stats?.totalJudgments ?: 0).toString()
        binding.specialistStatsLayout.completedJudgmentsTxt.text =
            (stats?.completedJudgments ?: 0).toString()
        binding.specialistStatsLayout.correctJudgmentsTxt.text =
            (stats?.correctJudgments ?: 0).toString()
        binding.specialistStatsLayout.incorrectJudgmentsTxt.text =
            (stats?.incorrectJudgments ?: 0).toString()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                ifNotPullToRefreshing { disableUI() }
            }

            FinishedState -> {
                ifNotPullToRefreshing { enableUI() }
                binding.pullToRefresh.isRefreshing = false
            }

            is WorkAssignmentSuccessState -> {
                enableUI()
                moveToWorkScreen(it.workAssignment)
            }

            WorkAssignmentFailureState -> {
                enableUI()
                showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }

            is SandboxTrainingSetupSuccessState -> {
                enableUI()
                if (it.sandboxTrainingResponse != null)
                    moveToSandboxScreen(it.sandboxTrainingResponse)
                else showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }

            is DownloadingAIEngineState -> {
                enableUI()
                showFileDownloadDialog(it.workAssignment)
            }

            is WorkRequestingState -> {
                enableUI()
                showWorkRequestingStatusDialog(it.workRequestId, it.role)
            }

            is FaqNotSeenState -> {
                enableUI()
                moveToIllustrationScreen(it.workAssignment, SPECIALIST)
            }

            is EarningAlertState -> {
                enableUI()
                showEarningAlert(it.workAssignment)
            }

            is AppIntroVideoState -> {
                moveToLearningVideoScreen(it.video)
            }

            is ErrorState -> {
                ifNotPullToRefreshing { enableUI() }
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupClicks() {
        binding.startWorkContainer.setOnClickListener {
            activeRoleAction {
                specialistViewModel.assignWork()
            }
        }

        if ((specialistViewModel.getAppLanguage() != null) && specialistViewModel.getWalkThroughEnabled() == true) {
            MaterialShowcaseView.Builder(requireActivity())
                .setTarget(binding.startWorkContainer)
                .setTargetTouchable(true)
                .setDismissOnTargetTouch(true)
                .setDismissOnTouch(false)
                .setMaskColour(Color.parseColor("#CC000000"))
                .withRectangleShape()
                .setContentText("Click here to start working.")
                .setDelay(500)
                .singleUse("startWorkContainer")
                .show()
        }

        binding.imageExpandIcon.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.baseView, AutoTransition())
            if (it.isSelected) {
                it.unSelected()
                binding.specialistStatsLayout.specialistStatsContainer2.gone()
                it.animate().rotation(0f).setDuration(500).start()
            } else {
                it.selected()
                binding.specialistStatsLayout.specialistStatsContainer2.visible()
                it.animate().rotation(180f).setDuration(500).start()
            }
        }

        binding.workSummaryContainer.setOnClickListener {
            activeRoleAction {
                Intent(activity, SpecialistWorkSummaryActivity::class.java).apply {
                    startActivity(this)
                }
            }
        }

        binding.performanceContainer.setOnClickListener {
            activeRoleAction {
                Intent(activity, SpecialistPerformanceActivity::class.java).apply {
                    startActivity(this)
                }
            }
        }

        binding.videoGalleryContainer.setOnClickListener {
            Intent(activity, LearningVideosPlaylistActivity::class.java).apply {
                putExtra(CURRENT_ROLE, SPECIALIST)
                startActivity(this)
            }
        }
    }

    private fun moveToSandboxScreen(sandboxTrainingResponse: SandboxTrainingResponse?) {
        Intent(activity, SandboxActivity::class.java).apply {
            putExtra(SandboxActivity.SANDBOX_ASSIGNMENT, sandboxTrainingResponse)
            startActivity(this)
        }
    }

    override fun moveToWorkScreen(workAssignment: WorkAssignment) {
        when {
            workAssignment.workType != null -> moveToNextScreen(workAssignment)
            else -> showMessage(getString(R.string.no_pending_work))
        }
    }

    private fun moveToNextScreen(workAssignment: WorkAssignment) {
        if (workAssignment.workType == WorkType.REVIEW &&
            workAssignment.wfStep?.mediaType != MediaType.VIDEO
        ) moveToReviewScreen(workAssignment)
        else {
            val user = specialistViewModel.getUserInfo()
            moveToCanvasScreen(workAssignment, user)
        }
    }

    private fun moveToReviewScreen(workAssignment: WorkAssignment) {
        if (workAssignment.applicationMode == Mode.MCML)
            getResultContentMCML.launch(ReviewIntentInputData(workAssignment, BuildConfig.FLAVOR))
        else getResultContent.launch(ReviewIntentInputData(workAssignment, BuildConfig.FLAVOR))
    }

    private val getResultContent = registerForActivityResult(ReviewActivity.ResultCallback()) {
        // if manager account is locked isAccountLocked will be true.
    }

    private val getResultContentMCML =
        registerForActivityResult(ReviewModeActivity.ResultCallback()) {
            // if manager account is locked isAccountLocked will be true.
        }

    override fun setCanvasRole(role: String) {
        specialistViewModel.setCanvasRole(role)
    }

    override fun lookForSpecialistWork(message: String) {
        activeRoleAction { specialistViewModel.assignWork() }
    }

    override fun setupWork(workAssignment: WorkAssignment) {
        specialistViewModel.setupWork(workAssignment)
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun saveDownloadDetailsFor(cameraAiModel: CameraAIModel?) {
        specialistViewModel.saveDownloadDetailsFor(cameraAiModel)
    }

    override fun enableUI() {
        binding.progressBar.invisible()
        binding.startWorkContainer.enabled()
    }

    override fun disableUI() {
        binding.progressBar.visible()
        binding.startWorkContainer.enabled()
    }

    override fun saveLearningVideoCompletedFor(applicationModeName: String?) {
        specialistViewModel.saveLearningVideoCompletedFor(applicationModeName)
    }

    private fun activeRoleAction(action: () -> Unit) {
        when {
            specialistViewModel.isSpecialistActive() -> action()
            else -> showMessage(getString(R.string.dont_have_specialist_access))
        }
    }

    private fun ifNotPullToRefreshing(action: () -> Unit) {
        if (binding.pullToRefresh.isRefreshing.not()) action()
    }
}