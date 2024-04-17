package co.nayan.c3specialist_v2.performance.managerperformance

import android.os.Bundle
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.databinding.ActivityManagerPerformanceBinding
import co.nayan.c3specialist_v2.performance.PerformanceActivity
import co.nayan.c3specialist_v2.performance.models.Performance
import co.nayan.c3specialist_v2.performance.models.PointStats
import co.nayan.c3specialist_v2.performance.models.UserStats
import co.nayan.c3specialist_v2.performance.widgets.OnIncorrectClickListener
import co.nayan.c3specialist_v2.performance.widgets.PerformanceFragment
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.c3_module.Stats
import co.nayan.c3v2.core.utils.setupActionBar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ManagerPerformanceActivity : PerformanceActivity() {

    private val binding: ActivityManagerPerformanceBinding by viewBinding(
        ActivityManagerPerformanceBinding::inflate
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.appToolbar)
        title = getString(R.string.my_performance)

        initDates()
        setupClicks()
        binding.dateRangeLayout.filterBtn.performClick()
    }

    private val onIncorrectClickListener = object : OnIncorrectClickListener {
        override fun onIncorrectAnnotationClicked() {
            moveToIncorrectRecordsScreen(WorkType.ANNOTATION, Role.MANAGER)
        }

        override fun onIncorrectJudgmentClicked() {
            moveToIncorrectRecordsScreen(WorkType.VALIDATION, Role.MANAGER)
        }

        override fun onIncorrectReviewClicked() {
            moveToIncorrectRecordsScreen(WorkType.REVIEW, Role.MANAGER)
        }
    }

    override fun setupStats(stats: Stats?) {
        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(stats?.lastUpdatedAt ?: "--")

        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(stats?.lastUpdatedAt ?: "--")

        val performance = Performance(
            workDuration = stats?.workDuration,
            points = PointStats(
                stats?.potentialScore?.toFloat(),
                stats?.completedPotentialScore?.toFloat(),
                stats?.correctScore?.toFloat(),
                stats?.incorrectScore?.toFloat()
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
            reviewStats = UserStats(
                stats?.totalReviews,
                stats?.completedReviews,
                stats?.correctReviews,
                stats?.incorrectReviews,
                stats?.inconclusiveReviews
            )

        )
        val fragment = PerformanceFragment.newInstance(
            performance,
            showIncorrect = true,
            callback = onIncorrectClickListener
        )
        supportFragmentManager.beginTransaction().replace(R.id.performanceContainer, fragment)
            .commit()
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
        performanceViewModel.fetchManagerPerformance(startDate, endDate)
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
}