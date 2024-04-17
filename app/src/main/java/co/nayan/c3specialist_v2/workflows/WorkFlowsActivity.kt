package co.nayan.c3specialist_v2.workflows

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
import co.nayan.c3specialist_v2.databinding.ActivityWorkFlowsBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3specialist_v2.workflowsteps.WorkFlowStepsActivity
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.WorkFlow
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WorkFlowsActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val workSummaryViewModel: WorkFlowsViewModel by viewModels()
    private val binding: ActivityWorkFlowsBinding by viewBinding(ActivityWorkFlowsBinding::inflate)

    private val onWorkFlowClickListener = object : OnWorkFlowClickListener {
        override fun onClick(workFlow: WorkFlow) {
            Intent(this@WorkFlowsActivity, WorkFlowStepsActivity::class.java).apply {
                putExtra(Extras.WORK_FLOW_ID, workFlow.id)
                putExtra(Extras.WORK_FLOW_NAME, workFlow.name)
                startActivity(this)
            }
        }
    }

    private val workFlowsAdapter = WorkFlowsAdapter(onWorkFlowClickListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        title = getString(R.string.my_workflows)

        setupViews()
        workSummaryViewModel.state.observe(this, stateObserver)
        workSummaryViewModel.fetchWorkFlows()

        binding.pullToRefresh.setOnRefreshListener {
            workSummaryViewModel.fetchWorkFlows()
        }
    }

    private fun setupViews() {
        binding.workflowsView.layoutManager = LinearLayoutManager(this)
        binding.workflowsView.adapter = workFlowsAdapter
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                if (binding.pullToRefresh.isRefreshing.not()) {
                    binding.shimmerViewContainer.visible()
                    binding.shimmerViewContainer.startShimmer()
                    binding.workflowsView.gone()
                    binding.noWorkflowContainer.gone()
                }
            }

            WorkFlowsViewModel.NoWorkflowState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noWorkflowContainer.visible()
                binding.workflowsView.gone()
            }

            is WorkFlowsViewModel.FetchWorkflowsSuccessState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noWorkflowContainer.gone()
                binding.workflowsView.visible()

                workFlowsAdapter.addAll(it.workFlows)
                binding.workflowsView.scheduleLayoutAnimation()
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

    private val queryTextListener =
        object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    workFlowsAdapter.filterListItems(it)
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    workFlowsAdapter.filterListItems(it)
                }
                return false
            }

        }

    override fun showMessage(message: String) {
        Snackbar.make(binding.shimmerViewContainer, message, Snackbar.LENGTH_SHORT).show()
    }
}