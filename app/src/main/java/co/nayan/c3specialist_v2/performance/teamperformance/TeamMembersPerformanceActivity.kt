package co.nayan.c3specialist_v2.performance.teamperformance

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityTeamMembersPerformanceBinding
import co.nayan.c3specialist_v2.performance.PerformanceActivity
import co.nayan.c3specialist_v2.performance.models.Performance
import co.nayan.c3specialist_v2.performance.models.PointStats
import co.nayan.c3specialist_v2.performance.models.UserStats
import co.nayan.c3specialist_v2.performance.widgets.PerformanceFragment
import co.nayan.c3specialist_v2.utils.setChildren
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.c3_module.responses.MemberStats
import co.nayan.c3v2.core.utils.setupActionBar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TeamMembersPerformanceActivity : PerformanceActivity() {

    private val binding: ActivityTeamMembersPerformanceBinding by viewBinding(
        ActivityTeamMembersPerformanceBinding::inflate
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupActionBar(binding.appToolbar)
        title = getString(R.string.team_performance)

        initDates()
        setupClicks()
        binding.roleContainer.setChildren(R.id.specialistTxt)
        binding.dateRangeLayout.filterBtn.performClick()
    }

    override fun setupTeamStats(stats: MemberStats?) {
        val reviewStats = if (userType() == Role.MANAGER) {
            UserStats(
                stats?.totalReviews,
                stats?.completedReviews,
                stats?.correctReviews,
                stats?.incorrectReviews,
                stats?.inconclusiveReviews
            )
        } else {
            null
        }
        val performance = Performance(
            workDuration = stats?.workDuration,
            points = PointStats(
                stats?.potentialScore,
                stats?.completedPotentialScore,
                stats?.correctScore,
                stats?.incorrectScore
            ),
            annotationStats = UserStats(
                stats?.totalAnnotations,
                stats?.completedAnnotations,
                stats?.correctAnnotations,
                stats?.incorrectAnnotations,
                stats?.inconclusiveAnnotations
            ),
            judgmentStats = UserStats(
                stats?.totalJudgments,
                stats?.completedJudgments,
                stats?.correctJudgments,
                stats?.incorrectJudgments,
                stats?.inconclusiveJudgments
            ),
            reviewStats = reviewStats
        )
        val fragment = PerformanceFragment.newInstance(performance)
        supportFragmentManager.beginTransaction().replace(R.id.performanceContainer, fragment)
            .commit()
    }

    override fun setupClicks() {
        binding.specialistTxt.setOnClickListener {
            binding.roleContainer.setChildren(it.id)
            fetchPerformance(startDate(), endDate())
        }

        binding.managerTxt.setOnClickListener {
            binding.roleContainer.setChildren(it.id)
            fetchPerformance(startDate(), endDate())
        }

        binding.dateRangeLayout.startDateTxt.setOnClickListener {
            showDatePickerDialog(isStartDate = true)
        }

        binding.dateRangeLayout.endDateTxt.setOnClickListener {
            showDatePickerDialog(isStartDate = false)
        }

        binding.dateRangeLayout.filterBtn.setOnClickListener {
            fetchPerformance(startDate(), endDate())
        }
    }

    override fun setStartDate(toSet: String) {
        binding.dateRangeLayout.startDateTxt.text = toSet
    }

    override fun setEndDate(toSet: String) {
        binding.dateRangeLayout.endDateTxt.text = toSet
    }

    override fun fetchPerformance(startDate: String, endDate: String) {
        performanceViewModel.fetchOverAllPerformance(startDate, endDate, userType())
    }

    override fun startDate(): String {
        return "${binding.dateRangeLayout.startDateTxt.text}"
    }

    override fun endDate(): String {
        return "${binding.dateRangeLayout.endDateTxt.text}"
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.performanceContainer, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun userType() = if (binding.specialistTxt.isSelected)
        Role.SPECIALIST else Role.MANAGER

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.team_performance, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.viewAll) {
            Intent(
                this@TeamMembersPerformanceActivity,
                MembersPerformanceActivity::class.java
            ).apply {
                putExtra(Extras.START_DATE, startDate())
                putExtra(Extras.END_DATE, endDate())
                startActivity(this)
            }
            true
        } else super.onOptionsItemSelected(item)
    }
}