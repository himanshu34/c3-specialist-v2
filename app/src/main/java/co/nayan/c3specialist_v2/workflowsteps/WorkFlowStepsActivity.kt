package co.nayan.c3specialist_v2.workflowsteps

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityWorkFlowStepsBinding
import co.nayan.c3specialist_v2.developerreview.DeveloperReviewActivity
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentFailureState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkAssignmentSuccessState
import co.nayan.c3specialist_v2.home.roles.specialist.WorkRequestingState
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3specialist_v2.workrequeststatus.WorkRequestStatusDialogFragment
import co.nayan.c3specialist_v2.workrequeststatus.WorkRequestStatusDialogListener
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.CanvasActivity
import co.nayan.canvas.sandbox.SandboxReviewActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WorkFlowStepsActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val workFlowStepsViewModel: WorkFlowStepsViewModel by viewModels()
    private val binding: ActivityWorkFlowStepsBinding by viewBinding(ActivityWorkFlowStepsBinding::inflate)

    private val onWfStepClickListener = object : OnWfStepClickListener {
        override fun openSandbox(wfStep: WfStep?) {
            Intent(this@WorkFlowStepsActivity, SandboxReviewActivity::class.java).apply {
                putExtra(Extras.WF_STEP, wfStep)
                startActivity(this)
            }
        }

        override fun openReviewRecords(wfStep: WfStep?) {
            workFlowStepsViewModel.assignWork(wfStep?.id)
        }
    }
    private val wfStepsAdapter = WfStepsAdapter(onWfStepClickListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        title = intent.getStringExtra(Extras.WORK_FLOW_NAME)

        setupViews()
        workFlowStepsViewModel.state.observe(this, stateObserver)
        val workFlowId = intent.getIntExtra(Extras.WORK_FLOW_ID, -1)
        if (workFlowId == -1) finish()
        else workFlowStepsViewModel.fetchWfSteps(workFlowId)

        binding.pullToRefresh.setOnRefreshListener {
            workFlowStepsViewModel.fetchWfSteps(workFlowId)
        }
    }

    private fun setupViews() {
        binding.wfStepsView.layoutManager = LinearLayoutManager(this)
        binding.wfStepsView.adapter = wfStepsAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                if (!binding.pullToRefresh.isRefreshing) {
                    binding.shimmerViewContainer.visible()
                    binding.shimmerViewContainer.startShimmer()
                    binding.wfStepsView.gone()
                    binding.noWfStepsContainer.gone()
                    binding.shimmerViewContainer.visible()
                }
            }
            WorkFlowStepsViewModel.NoWorkflowStepsState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noWfStepsContainer.visible()
                binding.wfStepsView.gone()
            }
            is WorkFlowStepsViewModel.FetchWorkflowStepsSuccessState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noWfStepsContainer.gone()
                binding.wfStepsView.visible()

                wfStepsAdapter.addAll(it.wfSteps)
                wfStepsAdapter.notifyDataSetChanged()
                binding.wfStepsView.scheduleLayoutAnimation()
            }
            WorkFlowStepsViewModel.ReviewWorkProgressState -> {
                showProgressDialog(getString(R.string.please_wait_assigning_work), true)
            }
            is WorkAssignmentSuccessState -> {
                hideProgressDialog()
                setupWork(it.workAssignment)
            }
            WorkAssignmentFailureState -> {
                hideProgressDialog()
                showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }
            is WorkRequestingState -> {
                hideProgressDialog()
                showWorkRequestingStatusDialog(it.workRequestId)
            }
            is ErrorState -> {
                hideProgressDialog()
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private val workRequestStatusDialogListener = object : WorkRequestStatusDialogListener {
        override fun succeeded(workAssignment: WorkAssignment?, role: String?) {
            if (workAssignment == null) showMessage(getString(R.string.no_pending_work))
            else setupWork(workAssignment)
        }

        override fun failed(errorMessage: String) {
            showMessage(errorMessage)
        }

        override fun noWork(role: String?) {
            showMessage(getString(R.string.no_pending_work))
        }
    }

    private fun showWorkRequestingStatusDialog(workRequestId: Int) {
        WorkRequestStatusDialogFragment.newInstance(
            workRequestStatusDialogListener,
            workRequestId,
            workFlowStepsViewModel.currentRole()
        ).show(supportFragmentManager, getString(R.string.requesting_work))
    }

    private fun setupWork(workAssignment: WorkAssignment) {
        if (workAssignment.workType != null) moveToNextScreen(workAssignment)
        else showMessage(getString(R.string.no_pending_work))
    }

    private fun moveToNextScreen(workAssignment: WorkAssignment) {
        if (workAssignment.wfStep?.mediaType == MediaType.VIDEO) {
            workFlowStepsViewModel.setCanvasRole(Role.ADMIN)
            moveToCanvasScreen(workAssignment)
        } else moveToDeveloperReviewScreen(workAssignment)
    }

    private fun moveToCanvasScreen(workAssignment: WorkAssignment?) {
        Intent(this@WorkFlowStepsActivity, CanvasActivity::class.java).apply {
            putExtra(CanvasActivity.WORK_ASSIGNMENT, workAssignment)
            startActivity(this)
        }
    }

    private fun moveToDeveloperReviewScreen(workAssignment: WorkAssignment) {
        Intent(this@WorkFlowStepsActivity, DeveloperReviewActivity::class.java).apply {
            putExtra(Extras.WORK_ASSIGNMENT, workAssignment)
            startActivity(this)
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.shimmerViewContainer, message, Snackbar.LENGTH_SHORT).show()
    }
}