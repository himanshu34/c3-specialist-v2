package co.nayan.c3specialist_v2.performance.teamperformance

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityMembersPerformanceBinding
import co.nayan.c3specialist_v2.performance.PerformanceViewModel
import co.nayan.c3specialist_v2.performance.models.Performance
import co.nayan.c3specialist_v2.performance.models.PointStats
import co.nayan.c3specialist_v2.performance.models.UserStats
import co.nayan.c3specialist_v2.utils.setChildren
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.MemberStats
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MembersPerformanceActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val performanceViewModel: PerformanceViewModel by viewModels()
    private val binding: ActivityMembersPerformanceBinding by viewBinding(
        ActivityMembersPerformanceBinding::inflate
    )

    private val memberStatsClickListener = object : OnMemberStatsClickListener {
        override fun onClick(stats: MemberStats) {
            val reviewStats = if (userType() == Role.MANAGER) {
                UserStats(
                    stats.totalReviews,
                    stats.completedReviews,
                    stats.correctReviews,
                    stats.incorrectReviews,
                    stats.inconclusiveReviews
                )
            } else null

            val performance = Performance(
                workDuration = stats.workDuration,
                points = PointStats(
                    stats.potentialScore,
                    stats.completedPotentialScore,
                    stats.correctScore,
                    stats.incorrectScore
                ),
                annotationStats = UserStats(
                    stats.totalAnnotations,
                    stats.completedAnnotations,
                    stats.correctAnnotations,
                    stats.incorrectAnnotations,
                    stats.inconclusiveAnnotations
                ),
                judgmentStats = UserStats(
                    stats.totalJudgments,
                    stats.completedJudgments,
                    stats.correctJudgments,
                    stats.incorrectJudgments,
                    stats.inconclusiveJudgments
                ),
                reviewStats = reviewStats
            )
            Intent(
                this@MembersPerformanceActivity,
                TeamMemberPerformanceActivity::class.java
            ).apply {
                putExtra(Extras.USER_NAME, stats.userName)
                putExtra(Extras.USER_ROLE, userType())
                putExtra(Extras.PERFORMANCE, performance)
                putExtra(Extras.START_DATE, binding.startDateTxt.text)
                putExtra(Extras.END_DATE, binding.endDateTxt.text)
                putExtra(Extras.USER_ID, stats.userId)
                startActivity(this)
            }
        }
    }

    private val membersStatsAdapter = MembersPerformanceAdapter(memberStatsClickListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        title = getString(R.string.team_performance)

        setupClicks()
        setupViews()
        performanceViewModel.state.observe(this, stateObserver)
        setupExtras()
    }

    private fun setupExtras() {
        binding.startDateTxt.text = intent.getStringExtra(Extras.START_DATE)
        binding.endDateTxt.text = intent.getStringExtra(Extras.END_DATE)
        binding.specialistTxt.performClick()
        binding.pullToRefresh.setOnRefreshListener { fetchStats() }
    }

    private fun fetchStats() {
        performanceViewModel.fetchTeamMembersPerformance(
            binding.startDateTxt.text.toString(),
            binding.endDateTxt.text.toString(),
            userType()
        )
    }

    private fun setupClicks() {
        binding.specialistTxt.setOnClickListener {
            binding.roleContainer.setChildren(it.id)
            fetchStats()
        }

        binding.managerTxt.setOnClickListener {
            binding.roleContainer.setChildren(it.id)
            fetchStats()
        }
    }

    private fun setupViews() {
        binding.teamMembersView.layoutManager = LinearLayoutManager(this)
        binding.teamMembersView.adapter = membersStatsAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                if (binding.pullToRefresh.isRefreshing.not()) {
                    binding.shimmerViewContainer.visible()
                    binding.shimmerViewContainer.startShimmer()
                    binding.teamMembersView.gone()
                    binding.noTeamMemberContainer.gone()
                }
            }
            PerformanceViewModel.NoStatsState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noTeamMemberContainer.visible()
                binding.teamMembersView.gone()
            }
            is PerformanceViewModel.TeamMembersPerformanceStatsSuccessState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noTeamMemberContainer.gone()
                binding.teamMembersView.visible()

                membersStatsAdapter.addAll(it.membersStats)
                membersStatsAdapter.notifyDataSetChanged()
                binding.teamMembersView.scheduleLayoutAnimation()
            }
            is ErrorState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_item, menu)
        val menuItem = menu.findItem(R.id.search)
        val searchView = menuItem?.actionView as SearchView
        searchView.setOnQueryTextListener(queryTextListener)
        return super.onPrepareOptionsMenu(menu)
    }

    @SuppressLint("NotifyDataSetChanged")
    private val queryTextListener =
        object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    membersStatsAdapter.filterListItems(it)
                    membersStatsAdapter.notifyDataSetChanged()
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    membersStatsAdapter.filterListItems(it)
                    membersStatsAdapter.notifyDataSetChanged()
                }
                return false
            }

        }

    private fun userType() = if (binding.specialistTxt.isSelected) Role.SPECIALIST else Role.MANAGER

    override fun showMessage(message: String) {
        Snackbar.make(binding.shimmerViewContainer, message, Snackbar.LENGTH_SHORT).show()
    }
}