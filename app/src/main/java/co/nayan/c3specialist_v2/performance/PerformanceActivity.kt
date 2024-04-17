package co.nayan.c3specialist_v2.performance

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.incorrect_records_wf_steps.IncorrectRecordsWfStepsActivity
import co.nayan.c3specialist_v2.utils.currentDate
import co.nayan.c3specialist_v2.utils.getDate
import co.nayan.c3specialist_v2.utils.weekStartDate
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.Stats
import co.nayan.c3v2.core.models.c3_module.responses.LeaderStats
import co.nayan.c3v2.core.models.c3_module.responses.MemberStats
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import java.util.Calendar
import javax.inject.Inject

abstract class PerformanceActivity : BaseActivity() {

    protected val performanceViewModel: PerformanceViewModel by viewModels()

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.statusBarColor = Color.WHITE

        performanceViewModel.state.observe(this, stateObserver)
        performanceViewModel.stats.observe(this, userStatsObserver)
    }

    protected fun initDates() {
        setStartDate(Calendar.getInstance().weekStartDate())
        setEndDate(Calendar.getInstance().currentDate())
    }

    private val userStatsObserver: Observer<StatsResponse?> = Observer {
        setupStats(it?.stats)
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                showProgressDialog(getString(R.string.fetching_stats))
            }
            FinishedState -> {
                hideProgressDialog()
            }
            is PerformanceViewModel.OverallTeamPerformanceStatsSuccessState -> {
                hideProgressDialog()
                setupTeamStats(it.membersStats)
            }
            is PerformanceViewModel.LeaderPerformanceSuccessState -> {
                hideProgressDialog()
                setupLeaderStats(it.leaderStats)
            }
            is ErrorState -> {
                hideProgressDialog()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    protected fun showDatePickerDialog(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this, { _, year, month, day ->
                calendar.set(year, month, day)
                if (isStartDate) {
                    setStartDate(calendar.currentDate())
                } else {
                    setEndDate(calendar.currentDate())
                }

            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        if (isStartDate) {
            val maxDateLimit = endDate().getDate()
            datePickerDialog.datePicker.maxDate = maxDateLimit?.time ?: 1483209000000
        } else {
            val minDateLimit = startDate().getDate()
            datePickerDialog.datePicker.minDate = minDateLimit?.time ?: 1483209000000
            datePickerDialog.datePicker.maxDate = Calendar.getInstance().timeInMillis
        }
        datePickerDialog.show()
    }

    fun moveToIncorrectRecordsScreen(workType: String, userRole: String) {
        Intent(
            this@PerformanceActivity, IncorrectRecordsWfStepsActivity::class.java
        ).apply {
            putExtra(Extras.START_DATE, startDate())
            putExtra(Extras.END_DATE, endDate())
            putExtra(Extras.WORK_TYPE, workType)
            putExtra(Extras.USER_ROLE, userRole)
            startActivity(this)
        }
    }

    open fun setupLeaderStats(stats: LeaderStats?) = Unit
    open fun setupStats(stats: Stats?) = Unit
    open fun setupTeamStats(stats: MemberStats?) = Unit
    abstract fun setupClicks()
    abstract fun startDate(): String
    abstract fun endDate(): String
    abstract fun setEndDate(toSet: String)
    abstract fun setStartDate(toSet: String)
    abstract fun fetchPerformance(startDate: String, endDate: String)
}