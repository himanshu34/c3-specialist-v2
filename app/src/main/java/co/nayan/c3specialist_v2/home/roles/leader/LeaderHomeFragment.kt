package co.nayan.c3specialist_v2.home.roles.leader

import android.content.Intent
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.CurrentRole
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.FragmentLeaderHomeBinding
import co.nayan.c3specialist_v2.performance.leaderperformance.LeaderPerformanceActivity
import co.nayan.c3specialist_v2.performance.teamperformance.TeamMembersPerformanceActivity
import co.nayan.c3specialist_v2.team.teammember.TeamMembersActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.LeaderHomeStatsResponse
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LeaderHomeFragment : BaseFragment(R.layout.fragment_leader_home) {

    private val leaderViewModel: LeaderViewModel by viewModels()
    private val binding by viewBinding(FragmentLeaderHomeBinding::bind)

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.userEmailTxt.text = leaderViewModel.getUserEmail()

        leaderViewModel.state.observe(viewLifecycleOwner, stateObserver)
        leaderViewModel.stats.observe(viewLifecycleOwner, userStatsObserver)
        setupClicks()
        binding.pullToRefresh.setOnRefreshListener {
            leaderViewModel.fetchUserStats()
        }
        if (activity is DashboardActivity)
            (activity as DashboardActivity).updateHomeBackground(CurrentRole.LEADER)
    }

    override fun onResume() {
        super.onResume()
        leaderViewModel.fetchUserStats()
    }

    private val userStatsObserver: Observer<LeaderHomeStatsResponse?> = Observer {
        binding.potentialPointsTxt.text = it?.stats?.getTotalPotentialScore() ?: "0.0"
        binding.hoursWorkedTxt.text = it?.stats?.getTotalWorkHours() ?: "00:00"
        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(it?.stats?.lastUpdatedAt ?: "--")

        val specialistStats = it?.stats?.specialist
        binding.memberStatsExpandedLayout.specialistTotalAnnotationsCountTxt.text =
            (specialistStats?.totalAnnotations ?: 0).toString()
        binding.memberStatsExpandedLayout.specialistCompletedAnnotationsCountTxt.text =
            (specialistStats?.completedAnnotations ?: 0).toString()

        binding.memberStatsExpandedLayout.specialistTotalValidationsCountTxt.text = (specialistStats?.totalJudgments ?: 0).toString()
        binding.memberStatsExpandedLayout.specialistCompletedValidationsCountTxt.text =
            (specialistStats?.completedJudgments ?: 0).toString()


        val managerStats = it?.stats?.manager
        binding.memberStatsExpandedLayout.managerTotalAnnotationsCountTxt.text = (managerStats?.totalAnnotations ?: 0).toString()
        binding.memberStatsExpandedLayout.managerCompletedAnnotationsCountTxt.text =
            (managerStats?.completedAnnotations ?: 0).toString()

        binding.memberStatsExpandedLayout.managerTotalValidationsCountTxt.text = (managerStats?.totalJudgments ?: 0).toString()
        binding.memberStatsExpandedLayout.managerCompletedValidationsCountTxt.text =
            (managerStats?.completedJudgments ?: 0).toString()

        binding.memberStatsExpandedLayout.managerTotalReviewsCountTxt.text = (managerStats?.totalReviews ?: 0).toString()
        binding.memberStatsExpandedLayout.managerCompletedReviewsCountTxt.text = (managerStats?.completedReviews ?: 0).toString()

        val leaderStats = it?.leaderStats
        binding.potentialPointsLeadersTxt.text = (leaderStats?.overallPotentialScore ?: "0.0").toString()
        binding.leaderStatsExpandedLayout.correctPotentialPointsLeadersTxt.text =
            (leaderStats?.overallCorrectScore ?: "0.0").toString()
        binding.completedPotentialPointsLeadersTxt.text =
            (leaderStats?.overallCompletedPotentialScore ?: "0.0").toString()
        binding.leaderStatsExpandedLayout.incorrectPotentialPointsLeadersTxt.text =
            (leaderStats?.overallIncorrectScore ?: "0.0").toString()
        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(leaderStats?.lastUpdatedAt ?: "--")
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
            is ErrorState -> {
                ifNotPullToRefreshing { enableUI() }
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupClicks() {
        binding.yourTeamContainer.setOnClickListener {
            activeRoleAction {
                Intent(activity, TeamMembersActivity::class.java).apply {
                    startActivity(this)
                }
            }
        }

        binding.imageExpandIcon.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.baseView, AutoTransition())
            if (it.isSelected) {
                it.unSelected()
                binding.memberStatsExpandedLayout.memberStatsExpandedContainer.gone()
                it.animate().rotation(0f).setDuration(500).start()
            } else {
                it.selected()
                binding.memberStatsExpandedLayout.memberStatsExpandedContainer.visible()
                it.animate().rotation(180f).setDuration(500).start()
            }
        }

        binding.imageExpandLeaderStatsIv.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.baseView, AutoTransition())
            if (it.isSelected) {
                it.unSelected()
                binding.leaderStatsExpandedLayout.leaderStatsExpandedContainer.gone()
                it.animate().rotation(0f).setDuration(500).start()
            } else {
                it.selected()
                binding.leaderStatsExpandedLayout.leaderStatsExpandedContainer.visible()
                it.animate().rotation(180f).setDuration(500).start()
            }
        }

        binding.teamPerformanceContainer.setOnClickListener {
            activeRoleAction {
                Intent(activity, TeamMembersPerformanceActivity::class.java).apply {
                    startActivity(this)
                }
            }
        }

        binding.leaderPerformanceContainer.setOnClickListener {
            activeRoleAction {
                Intent(activity, LeaderPerformanceActivity::class.java).apply {
                    startActivity(this)
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun enableUI() {
        binding.progressBar.invisible()
    }

    private fun disableUI() {
        binding.progressBar.visible()
    }

    private fun activeRoleAction(action: () -> Unit) {
        if (leaderViewModel.isLeaderActive()) action()
        else showMessage(getString(R.string.dont_have_leader_access))
    }

    private fun ifNotPullToRefreshing(action: () -> Unit) {
        if (binding.pullToRefresh.isRefreshing.not()) {
            action()
        }
    }
}