package co.nayan.c3specialist_v2.specialistworksummary

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.databinding.ActivitySpecialistWorkSummaryBinding
import co.nayan.c3specialist_v2.utils.currentDate
import co.nayan.c3specialist_v2.utils.getDate
import co.nayan.c3specialist_v2.utils.weekStartDate
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import co.nayan.c3v2.core.utils.setupActionBar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class SpecialistWorkSummaryActivity : BaseActivity() {

    private val specialistWorkSummaryViewModel: SpecialistWorkSummaryViewModel by viewModels()
    private val binding: ActivitySpecialistWorkSummaryBinding by viewBinding(
        ActivitySpecialistWorkSummaryBinding::inflate
    )

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        window?.statusBarColor = Color.WHITE
        setupActionBar(binding.appToolbar)
        title = getString(R.string.work_summary)

        setupStartDateForWeek()
        setupEndDate()
        specialistWorkSummaryViewModel.state.observe(this, stateObserver)
        specialistWorkSummaryViewModel.stats.observe(this, userStatsObserver)
        setupClicks()
        binding.dateRangeLayout.filterBtn.performClick()
    }

    private val userStatsObserver: Observer<StatsResponse?> = Observer {
        val stats = it?.stats
        binding.potentialPointsTxt.text = stats?.potentialScore ?: "0.0"
        binding.hoursWorkedTxt.text = stats?.workDuration ?: "00:00"
        binding.lastUpdatedAtTxt.text =
            getString(R.string.last_updated_at).format(stats?.lastUpdatedAt ?: "--")
        binding.totalAnnotationsCountTxt.text = (stats?.totalAnnotations ?: 0).toString()
        binding.completedAnnotationsCountTxt.text = (stats?.completedAnnotations ?: 0).toString()
        binding.totalValidationsCountTxt.text = (stats?.totalJudgments ?: 0).toString()
        binding.completedValidationsCountTxt.text = (stats?.completedJudgments ?: 0).toString()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                showProgressDialog(getString(R.string.fetching_stats))
            }
            FinishedState -> {
                hideProgressDialog()
            }
            is ErrorState -> {
                hideProgressDialog()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupClicks() {
        binding.dateRangeLayout.startDateTxt.setOnClickListener {
            showDatePickerDialog(isStartDate = true)
        }

        binding.dateRangeLayout.endDateTxt.setOnClickListener {
            showDatePickerDialog(isStartDate = false)
        }

        binding.dateRangeLayout.filterBtn.setOnClickListener {
            specialistWorkSummaryViewModel.fetchWorkSummary(startDate(), endDate())
        }
    }

    private fun setupEndDate() {
        val calendar = Calendar.getInstance()
        binding.dateRangeLayout.endDateTxt.text = calendar.currentDate()
    }

    private fun setupStartDateForWeek() {
        val calendar = Calendar.getInstance()
        binding.dateRangeLayout.startDateTxt.text = calendar.weekStartDate()
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this, { _, year, month, day ->
                calendar.set(year, month, day)
                if (isStartDate) {
                    binding.dateRangeLayout.startDateTxt.text = calendar.currentDate()
                } else {
                    binding.dateRangeLayout.endDateTxt.text = calendar.currentDate()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        if (isStartDate) {
            val maxDateLimit = binding.dateRangeLayout.endDateTxt.text.toString().getDate()
            datePickerDialog.datePicker.maxDate = maxDateLimit?.time ?: 1483209000000
        } else {
            val minDateLimit = binding.dateRangeLayout.startDateTxt.text.toString().getDate()
            datePickerDialog.datePicker.minDate = minDateLimit?.time ?: 1483209000000
            datePickerDialog.datePicker.maxDate = Calendar.getInstance().timeInMillis
        }
        datePickerDialog.show()
    }

    private fun startDate(): String {
        return binding.dateRangeLayout.startDateTxt.text.toString()
    }

    private fun endDate(): String {
        return binding.dateRangeLayout.endDateTxt.text.toString()
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.statsContainer, message, Snackbar.LENGTH_SHORT).show()
    }
}