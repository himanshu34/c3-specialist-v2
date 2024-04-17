package co.nayan.c3specialist_v2.home.roles.manager

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.config.LearningVideosCategory.CURRENT_ROLE
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.FragmentManagerHomeBinding
import co.nayan.c3specialist_v2.home.roles.RoleBaseFragment
import co.nayan.c3specialist_v2.home.roles.specialist.DownloadingAIEngineState
import co.nayan.c3specialist_v2.home.roles.specialist.EarningAlertState
import co.nayan.c3specialist_v2.home.roles.specialist.FaqNotSeenState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentFailureState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentSuccessState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkRequestingState
import co.nayan.c3specialist_v2.managerworksummary.ManagerWorkSummaryActivity
import co.nayan.c3specialist_v2.performance.managerperformance.ManagerPerformanceActivity
import co.nayan.c3specialist_v2.videogallery.LearningVideosPlaylistActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Mode
import co.nayan.c3v2.core.config.Role.MANAGER
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.CameraAIModel
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.WfStep
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import co.nayan.review.incorrectreviews.IncorrectReviewsActivity
import co.nayan.review.models.ReviewIntentInputData
import co.nayan.review.recordsgallery.ReviewActivity
import co.nayan.review.recordsreview.ReviewModeActivity
import co.nayan.review.widgets.ManagerDisabledAlertFragment
import co.nayan.review.widgets.ManagerDisabledDialogListener
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ManagerHomeFragment : RoleBaseFragment(R.layout.fragment_manager_home) {

    private val managerViewModel: ManagerViewModel by viewModels()
    private val binding by viewBinding(FragmentManagerHomeBinding::bind)

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.userEmailTxt.text = managerViewModel.getUserEmail()
        binding.homeMessageTxt.text = String.format(
            getString(R.string.home_screen_message), managerViewModel.getUserName()
        )
        managerViewModel.state.observe(viewLifecycleOwner, stateObserver)
        managerViewModel.stats.observe(viewLifecycleOwner, userStatsObserver)
        setupClicks()
        binding.pullToRefresh.setOnRefreshListener {
            managerViewModel.fetchUserStats()
        }
        if (activity is DashboardActivity) {
            (activity as DashboardActivity).updateHomeBackground(CurrentRole.MANAGER)
        }
    }

    override fun onResume() {
        super.onResume()
        managerViewModel.fetchUserStats()
    }

    private val userStatsObserver: Observer<StatsResponse?> = Observer {
        val stats = it?.stats
        binding.potentialPointsTxt.text = stats?.potentialScore ?: "0.0"
        binding.hoursWorkedTxt.text = stats?.workDuration ?: "00:00"
        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(stats?.lastUpdatedAt ?: "--")

        binding.managerHomeStatsLayout.totalReviewsCountTxt.text =
            (stats?.totalReviews ?: 0).toString()
        binding.managerHomeStatsLayout.completedReviewsCountTxt.text =
            (stats?.completedReviews ?: 0).toString()

        binding.managerHomeStatsLayout.totalAnnotationsCountTxt.text =
            (stats?.totalAnnotations ?: 0).toString()
        binding.managerHomeStatsLayout.completedAnnotationsCountTxt.text =
            (stats?.completedAnnotations ?: 0).toString()

        binding.managerHomeStatsLayout.totalValidationsCountTxt.text =
            (stats?.totalJudgments ?: 0).toString()
        binding.managerHomeStatsLayout.completedValidationsCountTxt.text =
            (stats?.completedJudgments ?: 0).toString()
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
            is DownloadingAIEngineState -> {
                enableUI()
                showFileDownloadDialog(it.workAssignment)
            }
            is WorkRequestingState -> {
                enableUI()
                showWorkRequestingStatusDialog(it.workRequestId, MANAGER)
            }
            is ManagerViewModel.ManagerAccountLockedState -> {
                enableUI()
                showAccountLockedDialog(it.message, it.incorrectSniffingRecords, it.wfStep)
            }
            is FaqNotSeenState -> {
                enableUI()
                moveToIllustrationScreen(it.workAssignment, MANAGER)
            }
            is EarningAlertState -> {
                enableUI()
                showEarningAlert(it.workAssignment)
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
            activeRoleAction { managerViewModel.assignWork() }
        }

        binding.imageExpandIcon.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.baseView, AutoTransition())
            if (it.isSelected) {
                it.unSelected()
                binding.managerHomeStatsLayout.managerHomeStatsContainer.gone()
                it.animate().rotation(0f).setDuration(500).start()
            } else {
                it.selected()
                binding.managerHomeStatsLayout.managerHomeStatsContainer.visible()
                it.animate().rotation(180f).setDuration(500).start()
            }
        }

        binding.workSummaryContainer.setOnClickListener {
            activeRoleAction {
                Intent(activity, ManagerWorkSummaryActivity::class.java).apply {
                    startActivity(this)
                }
            }
        }

        binding.performanceContainer.setOnClickListener {
            activeRoleAction {
                Intent(activity, ManagerPerformanceActivity::class.java).apply {
                    startActivity(this)
                }
            }
        }

        binding.videoGalleryContainer.setOnClickListener {
            Intent(activity, LearningVideosPlaylistActivity::class.java).apply {
                putExtra(CURRENT_ROLE, MANAGER)
                startActivity(this)
            }
        }
    }

    private fun showAccountLockedDialog(
        message: String?, incorrectSniffingRecords: List<Record>?, wfStep: WfStep?
    ) {
        ManagerDisabledAlertFragment.newInstance(object : ManagerDisabledDialogListener {
            override fun onNegativeBtnClick() {
                moveToIncorrectReviewsScreen(incorrectSniffingRecords, wfStep)
            }

            override fun onPositiveBtnClick() {}
        }, message).show(childFragmentManager.beginTransaction(), "Account Locked")
    }

    private fun moveToNextScreen(workAssignment: WorkAssignment) {
        if (workAssignment.workType == WorkType.REVIEW &&
            workAssignment.wfStep?.mediaType != MediaType.VIDEO
        ) {
            moveToReviewScreen(workAssignment)
        } else {
            val user = managerViewModel.getUserInfo()
            moveToCanvasScreen(workAssignment, user)
        }
    }

    private val getResultContent = registerForActivityResult(ReviewActivity.ResultCallback()) {
        // if manager account is locked isAccountLocked will be true.
    }

    private val getResultContentMCML =
        registerForActivityResult(ReviewModeActivity.ResultCallback()) {
            // if manager account is locked isAccountLocked will be true.
        }

    private fun moveToReviewScreen(workAssignment: WorkAssignment) {
        if (workAssignment.applicationMode == Mode.MCML)
            getResultContentMCML.launch(ReviewIntentInputData(workAssignment, BuildConfig.FLAVOR))
        else getResultContent.launch(ReviewIntentInputData(workAssignment, BuildConfig.FLAVOR))
    }

    override fun moveToWorkScreen(workAssignment: WorkAssignment) {
        if (workAssignment.workType != null) moveToNextScreen(workAssignment)
        else showMessage(getString(R.string.no_pending_work))
    }

    override fun saveDownloadDetailsFor(cameraAiModel: CameraAIModel?) {
        managerViewModel.saveDownloadDetailsFor(cameraAiModel)
    }

    override fun setupWork(workAssignment: WorkAssignment) {
        managerViewModel.setupWork(workAssignment)
    }

    override fun lookForSpecialistWork(message: String) {
        showMessage(message)
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
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
        managerViewModel.saveLearningVideoCompletedFor(applicationModeName)
    }

    private fun activeRoleAction(action: () -> Unit) {
        if (managerViewModel.isManagerActive()) action()
        else showMessage(getString(R.string.dont_have_manager_access))
    }

    private fun ifNotPullToRefreshing(action: () -> Unit) {
        if (binding.pullToRefresh.isRefreshing.not()) {
            action()
        }
    }

    private fun moveToIncorrectReviewsScreen(
        incorrectSniffingRecords: List<Record>?, wfStep: WfStep?
    ) {
        if (incorrectSniffingRecords.isNullOrEmpty()) return
        Intent(activity, IncorrectReviewsActivity::class.java).apply {
            putExtra(
                co.nayan.review.config.Extras.APPLICATION_MODE,
                wfStep?.applicationModeName
            )
            putExtra(co.nayan.review.config.Extras.CONTRAST_VALUE, managerViewModel.getContrast())
            putParcelableArrayListExtra(
                co.nayan.review.config.Extras.RECORDS,
                incorrectSniffingRecords as ArrayList<out Parcelable>
            )
            putExtra(co.nayan.review.config.Extras.QUESTION, wfStep?.question)
            putExtra(ReviewActivity.APP_FLAVOR, BuildConfig.FLAVOR)
            startActivity(this)
        }
    }
}