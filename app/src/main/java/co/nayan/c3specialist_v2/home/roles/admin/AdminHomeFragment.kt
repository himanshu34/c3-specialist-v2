package co.nayan.c3specialist_v2.home.roles.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.FragmentAdminHomeBinding
import co.nayan.c3specialist_v2.datarecords.DataRecordsActivity
import co.nayan.c3specialist_v2.developerreview.DeveloperReviewActivity
import co.nayan.c3specialist_v2.home.roles.RoleBaseFragment
import co.nayan.c3specialist_v2.home.roles.specialist.FetchWorkflowStepsSuccessState
import co.nayan.c3specialist_v2.home.roles.specialist.NoWorkflowStepsState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentFailureState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentSuccessState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkRequestingState
import co.nayan.c3specialist_v2.home.widgets.ActiveWorkflowStepsDialogFragment
import co.nayan.c3specialist_v2.screen_sharing.users.UsersActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3specialist_v2.workflows.WorkFlowsActivity
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AdminHomeFragment : RoleBaseFragment(R.layout.fragment_admin_home) {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val adminViewModel: AdminViewModel by viewModels()
    private val binding by viewBinding(FragmentAdminHomeBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.userEmailTxt.text = adminViewModel.getUserEmail()
        binding.homeMessageTxt.text = String.format(
            getString(R.string.home_screen_message_developer), adminViewModel.getUserName()
        )
        adminViewModel.state.observe(viewLifecycleOwner, stateObserver)
        adminViewModel.stats.observe(viewLifecycleOwner, userStatsObserver)
        setupClicks()
        if (activity is DashboardActivity)
            (activity as DashboardActivity).updateHomeBackground(CurrentRole.ADMIN)
    }

    private val userStatsObserver: Observer<StatsResponse?> = Observer {
        val stats = it?.stats
        binding.reviewTimeTxt.text = stats?.reviewDuration ?: "00:00"
        binding.totalWorkingHrsTxt.text = stats?.workDuration ?: "00:00"
        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(stats?.lastUpdatedAt ?: "--")

        binding.adminHomeStatsLayout.reviewedRecordsTxt.text = (stats?.totalReviews ?: 0).toString()
        binding.adminHomeStatsLayout.approvedRecordsTxt.text =
            (stats?.approvedCount ?: 0).toString()
        binding.adminHomeStatsLayout.rejectedRecordsTxt.text = (stats?.resetCount ?: 0).toString()
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
                moveToNextScreen(it.workAssignment)
            }

            WorkAssignmentFailureState -> {
                enableUI()
                showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }

            NoWorkflowStepsState -> {
                enableUI()
                showMessage(getString(R.string.no_pending_work))
            }

            is FetchWorkflowStepsSuccessState -> {
                enableUI()
                childFragmentManager.showDialogFragment(
                    ActiveWorkflowStepsDialogFragment(it.wfSteps) { wfStep ->
                        adminViewModel.assignWork(wfStep)
                    })
            }

            is WorkRequestingState -> {
                enableUI()
                showWorkRequestingStatusDialog(it.workRequestId, it.role)
            }

            is ErrorState -> {
                ifNotPullToRefreshing { enableUI() }
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupClicks() {
//        binding.pullToRefresh.setOnRefreshListener {
//            adminViewModel.fetchUserStats()
//        }

        binding.reviewWorkContainer.setOnClickListener {
            activeRoleAction { adminViewModel.requestWorkStepToWork() }
        }

        binding.imageExpandIcon.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.baseView, AutoTransition())
            if (it.isSelected) {
                it.unSelected()
                binding.adminHomeStatsLayout.adminHomeStatsContainer.gone()
                it.animate().rotation(0f).setDuration(500).start()
            } else {
                it.selected()
                binding.adminHomeStatsLayout.adminHomeStatsContainer.visible()
                it.animate().rotation(180f).setDuration(500).start()
            }
        }

        binding.myWorkflowsContainer.setOnClickListener {
            startActivity(Intent(activity, WorkFlowsActivity::class.java))
        }

        binding.startMeetingContainer.setOnClickListener {
            startActivity(Intent(activity, UsersActivity::class.java))
        }

        binding.recordsContainer.setOnClickListener {
            startActivity(Intent(activity, DataRecordsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
//        adminViewModel.fetchUserStats()
    }

    private fun moveToNextScreen(workAssignment: WorkAssignment) {
        if (workAssignment.wfStep?.mediaType == MediaType.VIDEO) {
            adminViewModel.setCanvasRole(Role.ADMIN)
            val user = adminViewModel.getUserInfo()
            moveToCanvasScreen(workAssignment, user)
        } else moveToDeveloperReviewScreen(workAssignment)
    }

    override fun setupWork(workAssignment: WorkAssignment) {
        if (workAssignment.workType != null) moveToNextScreen(workAssignment)
        else showMessage(getString(R.string.no_pending_work))
    }

    private fun moveToDeveloperReviewScreen(workAssignment: WorkAssignment) {
        Intent(activity, DeveloperReviewActivity::class.java).apply {
            putExtra(Extras.WORK_ASSIGNMENT, workAssignment)
            startActivity(this)
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun enableUI() {
        binding.progressBar.invisible()
        binding.reviewWorkContainer.enabled()
    }

    override fun disableUI() {
        binding.progressBar.visible()
        binding.reviewWorkContainer.enabled()
    }

    private fun activeRoleAction(action: () -> Unit) {
        if (adminViewModel.isAdminActive()) action()
        else showMessage(getString(R.string.dont_have_admin_access))
    }

    private fun ifNotPullToRefreshing(action: () -> Unit) {
        if (binding.pullToRefresh.isRefreshing.not()) {
            action()
        }
    }
}