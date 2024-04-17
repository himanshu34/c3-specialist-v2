package co.nayan.c3specialist_v2.datarecords

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityDataRecordsBinding
import co.nayan.c3specialist_v2.datarecords.adapters.DataRecordsAdapter
import co.nayan.c3specialist_v2.datarecords.widget.OnWfStepSelectionListener
import co.nayan.c3specialist_v2.datarecords.widget.WfStepFilterDialogFragment
import co.nayan.c3specialist_v2.profile.widgets.ItemSelectionDialogFragment
import co.nayan.c3specialist_v2.profile.widgets.OnItemSelectionListener
import co.nayan.c3specialist_v2.record_visualization.RecordHistoryActivity
import co.nayan.c3specialist_v2.utils.currentDate
import co.nayan.c3specialist_v2.utils.getDate
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import co.nayan.review.recordsgallery.RecordClickListener
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class DataRecordsActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val dataRecordsViewModel: DataRecordsViewModel by viewModels()
    private val binding: ActivityDataRecordsBinding by viewBinding(ActivityDataRecordsBinding::inflate)
    private var spanCount: Int = 2
    private var isLoading: Boolean = false

    private val recordClickListener = object : RecordClickListener {
        override fun onItemClicked(record: Record) {
            moveToRecordScreen(record.id)
        }

        override fun onLongPressed(position: Int, record: Record) {}
        override fun updateRecordsCount(selectedCount: Int, totalCount: Int) {}
        override fun starRecord(record: Record, position: Int, status: Boolean) {}
        override fun resetRecords() {}
    }
    private lateinit var aiOverlayRecordsAdapter: DataRecordsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.appToolbar)
        title = getString(R.string.data_records)

        aiOverlayRecordsAdapter = DataRecordsAdapter(recordClickListener)
        updateFiltersView()
        initVariables()
        setupClicks()
        setupRecordsView()
        binding.recordsView.adapter = aiOverlayRecordsAdapter

        dataRecordsViewModel.state.observe(this, stateObserver)
        dataRecordsViewModel.fetchAasmStates()
        dataRecordsViewModel.fetchWorkflows()
        dataRecordsViewModel.fetchFirstPage()
    }

    private fun initVariables() {
        spanCount = dataRecordsViewModel.getSavedSpanCount()
    }

    @SuppressLint("NotifyDataSetChanged")
    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                isLoading = true
                showProgressDialog(getString(R.string.please_wait_fetching_records))
                binding.noRecordsContainer.gone()
                binding.recordsView.gone()
                binding.reloadIV.gone()
            }
            DataRecordsViewModel.NoRecordState -> {
                hideProgressDialog()
                binding.noRecordsContainer.visible()
                binding.recordsView.gone()
                binding.reloadIV.gone()
            }
            is DataRecordsViewModel.SetUpRecordsState -> {
                isLoading = false
                hideProgressDialog()
                binding.noRecordsContainer.gone()
                binding.recordsView.visible()
                binding.reloadIV.gone()

                aiOverlayRecordsAdapter.isPaginationEnabled = it.isPaginationEnabled
                aiOverlayRecordsAdapter.addAll(it.records)
                aiOverlayRecordsAdapter.notifyDataSetChanged()

                binding.recordsView.addOnScrollListener(onScrollChangeListener)
            }
            is DataRecordsViewModel.SetUpNextPageRecordsState -> {
                isLoading = false
                aiOverlayRecordsAdapter.isPaginationEnabled = it.isPaginationEnabled
                aiOverlayRecordsAdapter.addNewRecords(it.records)
            }
            is ErrorState -> {
                hideProgressDialog()
                isLoading = false
                setupReloadView()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupReloadView() {
        if (dataRecordsViewModel.currentPage == 1) {
            binding.reloadIV.visible()
        }
    }

    private fun setupClicks() {
        binding.reloadIV.setOnClickListener {
            dataRecordsViewModel.fetchFirstPage()
        }
        binding.startDateFilter.setOnClickListener {
            showDatePickerDialog(true)
        }
        binding.endDateFilter.setOnClickListener {
            showDatePickerDialog(false)
        }
        binding.aasmStateFilter.setOnClickListener {
            val title = getString(R.string.aasm_state)
            val aasmState = listOf("None") + dataRecordsViewModel.aasmStates
            ItemSelectionDialogFragment.newInstance(
                title,
                onAasmStateSelectionListener,
                aasmState.toTypedArray()
            ).show(supportFragmentManager, getString(R.string.aasm_state))
        }
        binding.wfStepFilter.setOnClickListener {
            WfStepFilterDialogFragment.newInstance(
                onWfStepSelectionListener,
                dataRecordsViewModel.workflows
            ).show(supportFragmentManager, getString(R.string.wf_step))
        }
    }

    private val onAasmStateSelectionListener = object : OnItemSelectionListener {
        override fun onSelect(element: String) {
            if (element != dataRecordsViewModel.filters.aasmState) {
                dataRecordsViewModel.filters.aasmState = element
                updateFiltersView()
                dataRecordsViewModel.fetchFirstPage()
            }
        }
    }

    private val onWfStepSelectionListener = object : OnWfStepSelectionListener {
        override fun onSelect(wfStep: Pair<Int?, String>) {
            if (wfStep.first != dataRecordsViewModel.filters.wfStep.first) {
                dataRecordsViewModel.filters.wfStep = wfStep
                updateFiltersView()
                dataRecordsViewModel.fetchFirstPage()
            }
        }
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this, { _, year, month, day ->
                calendar.set(year, month, day)
                setupDatePickerData(calendar, isStartDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        val limitCalendar = Calendar.getInstance()
        limitCalendar.add(Calendar.DAY_OF_MONTH, -1)
        datePickerDialog.datePicker.maxDate = limitCalendar.timeInMillis

        if (isStartDate.not()) {
            val minDateLimit = dataRecordsViewModel.filters.startTime.getDate()
            datePickerDialog.datePicker.minDate = minDateLimit?.time ?: 1483209000000
        }
        datePickerDialog.show()
    }

    private fun setupDatePickerData(calendar: Calendar, isStartDate: Boolean) {
        if (isStartDate) {
            dataRecordsViewModel.filters.startTime = calendar.currentDate()
        } else {
            dataRecordsViewModel.filters.endTime = calendar.currentDate()
        }
        updateFiltersView()
        dataRecordsViewModel.fetchFirstPage()
    }

    private val onScrollChangeListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = binding.recordsView.layoutManager as GridLayoutManager
            val visibleCount = layoutManager.findLastVisibleItemPosition()
            if (visibleCount == aiOverlayRecordsAdapter.itemCount - 1 && !isLoading) {
                isLoading = true
                dataRecordsViewModel.fetchNextPage()
            }
        }
    }

    private fun setupRecordsView() {
        val gridLayoutManager = GridLayoutManager(this, spanCount)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val itemViewType = aiOverlayRecordsAdapter.getItemViewType(position)
                return if (itemViewType == DataRecordsAdapter.ITEM_TYPE_LOADER) {
                    spanCount
                } else {
                    1
                }
            }
        }
        binding.recordsView.layoutManager = gridLayoutManager
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.recordsView, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun updateFiltersView() {
        val filters = dataRecordsViewModel.filters
        binding.aasmStateFilter.text = if (filters.aasmState == "None") {
            getString(R.string.aasm_state)
        } else {
            filters.aasmState
        }

        binding.startDateFilter.text = filters.startTime
        binding.endDateFilter.text = filters.endTime

        val wfStep = filters.wfStep
        binding.wfStepFilter.text = if (wfStep.first == -1) {
            getString(R.string.wf_step)
        } else {
            wfStep.second
        }
    }

    private fun moveToRecordScreen(recordId: Int) {
        Intent(this@DataRecordsActivity, RecordHistoryActivity::class.java).apply {
            putExtra(Extras.RECORD_ID, recordId)
            startActivity(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.let {
            menuInflater.inflate(R.menu.search_item, it)
            val menuItem = it.findItem(R.id.search)
            val searchView = menuItem.actionView as SearchView
            searchView.inputType = InputType.TYPE_CLASS_NUMBER
            searchView.setOnQueryTextListener(queryTextListener)
        }
        return super.onCreateOptionsMenu(menu)
    }

    private val queryTextListener =
        object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    moveToRecordScreen(it.toInt())
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        }
}