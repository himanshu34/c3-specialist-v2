package co.nayan.c3specialist_v2.performance.leaderperformance

import android.os.Bundle
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.ActivityLeaderPerformanceBinding
import co.nayan.c3specialist_v2.performance.PerformanceActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.models.c3_module.responses.LeaderStats
import co.nayan.c3v2.core.utils.setupActionBar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LeaderPerformanceActivity : PerformanceActivity() {

    private val binding: ActivityLeaderPerformanceBinding by viewBinding(ActivityLeaderPerformanceBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupActionBar(binding.appToolbar)
        title = getString(R.string.my_performance)

        initDates()
        setupClicks()
        binding.dateRangeLayout.filterBtn.performClick()
    }

    override fun setupLeaderStats(stats: LeaderStats?) {
        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(stats?.lastUpdatedAt ?: "--")

        binding.totalPotentialTxt.text = String.format("%.2f", stats?.overallPotentialScore ?: 0f)
        binding.completedPotentialTxt.text =
            String.format("%.2f", stats?.overallCompletedPotentialScore ?: 0f)
        binding.correctPotentialTxt.text = String.format("%.2f", stats?.overallCorrectScore ?: 0f)
        binding.incorrectPotentialTxt.text = String.format("%.2f", stats?.overallIncorrectScore ?: 0f)
        binding.annotationPotentialTxt.text = String.format("%.2f", stats?.annotationsPotentialScore ?: 0f)
        binding.judgmentPotentialTxt.text = String.format("%.2f", stats?.judgmentsPotentialScore ?: 0f)
    }

    override fun setupClicks() {
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
        performanceViewModel.fetchLeaderPerformance(startDate, endDate)
    }

    override fun startDate(): String {
        return "${binding.dateRangeLayout.startDateTxt.text}"
    }

    override fun endDate(): String {
        return "${binding.dateRangeLayout.endDateTxt.text}"
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.pointsContainer, message, Snackbar.LENGTH_SHORT).show()
    }
}