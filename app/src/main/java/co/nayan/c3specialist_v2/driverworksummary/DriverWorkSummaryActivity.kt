package co.nayan.c3specialist_v2.driverworksummary

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.databinding.ActivityDriverWorkSummaryBinding
import co.nayan.c3specialist_v2.utils.currentDate
import co.nayan.c3specialist_v2.utils.getDate
import co.nayan.c3specialist_v2.utils.weekStartDate
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.FinishedState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.responses.DriverStatsResponse
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class DriverWorkSummaryActivity : BaseActivity() {

    private val driverWorkSummaryViewModel: DriverWorkSummaryViewModel by viewModels()
    private val binding: ActivityDriverWorkSummaryBinding by viewBinding(
        ActivityDriverWorkSummaryBinding::inflate
    )

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        window?.statusBarColor = Color.WHITE
        setupActionBar(binding.appToolbar)
        title = getString(R.string.work_summary)

        binding.extendedFab.visible()
        setupStartDateForWeek()
        setupEndDate()
        driverWorkSummaryViewModel.state.observe(this, stateObserver)
        driverWorkSummaryViewModel.stats.observe(this, userStatsObserver)
        setupClicks()
        binding.dateRangeLayout.filterBtn.performClick()
        binding.imageExpandIcon.performClick()
    }

    private val userStatsObserver: Observer<DriverStatsResponse?> = Observer {
        lifecycleScope.launch {
            val stats = it?.stats
            binding.hoursWorkedTxt.text = stats?.workDuration ?: "00:00"
            binding.amountEarnedTxt.text = (stats?.amountEarned ?: 0).toString()
            binding.recordedVideosTxt.text = (stats?.recordedVideos ?: 0).toString()
            binding.detectedObjectTxt.text = (stats?.detectedObjects ?: 0).toString()
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.baseView, message, Snackbar.LENGTH_SHORT).show()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> showProgressDialog(getString(R.string.fetching_stats))
            FinishedState -> hideProgressDialog()

            is DriverWorkSummaryViewModel.StatsErrorState -> {
                hideProgressDialog()
                showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
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
            driverWorkSummaryViewModel.fetchUserStats(startDate(), endDate())
            replaceFragment(ObjectOfInterestMapsFragment.newInstance(startDate(), endDate()))
        }

        binding.imageExpandIcon.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.baseView, AutoTransition())
            if (it.isSelected) {
                it.unSelected()
                binding.driverStatsContainer.gone()
                it.animate().rotation(0f).setDuration(500).start()
            } else {
                it.selected()
                binding.driverStatsContainer.visible()
                it.animate().rotation(180f).setDuration(500).start()
            }
        }

        binding.extendedFab.setOnClickListener {
            if (binding.imageExpandIcon.isSelected) {
                TransitionManager.beginDelayedTransition(binding.baseView, AutoTransition())
                binding.imageExpandIcon.unSelected()
                binding.driverStatsContainer.gone()
                binding.driverStatsContainer.gone()
                binding.imageExpandIcon.animate().rotation(0f).setDuration(500).start()
            }

            replaceFragment(SurgeMapsFragment.newInstance())
            binding.extendedFab.gone()
        }
    }

    private fun setupEndDate() = lifecycleScope.launch {
        val calendar = Calendar.getInstance()
        binding.dateRangeLayout.endDateTxt.text = calendar.currentDate()
    }

    private fun setupStartDateForWeek() = lifecycleScope.launch {
        val calendar = Calendar.getInstance()
        binding.dateRangeLayout.startDateTxt.text = calendar.weekStartDate()
    }

    private fun showDatePickerDialog(isStartDate: Boolean) = lifecycleScope.launch {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this@DriverWorkSummaryActivity, { _, year, month, day ->
                calendar.set(year, month, day)
                if (isStartDate) binding.dateRangeLayout.startDateTxt.text = calendar.currentDate()
                else binding.dateRangeLayout.endDateTxt.text = calendar.currentDate()
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

    private fun startDate(): String = "${binding.dateRangeLayout.startDateTxt.text}"
    private fun endDate(): String = "${binding.dateRangeLayout.endDateTxt.text}"

    private fun replaceFragment(fragment: Fragment) = lifecycleScope.launch {
        supportFragmentManager.beginTransaction().replace(R.id.map_container, fragment).commit()
    }
}