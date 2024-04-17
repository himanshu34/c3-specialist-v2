package co.nayan.c3specialist_v2.incorrect_records_wf_steps

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityIncorrectRecordsWfStepsBinding
import co.nayan.c3specialist_v2.incorrectrecords.IncorrectRecordsActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectWfStep
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IncorrectRecordsWfStepsActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val workFlowStepsViewModel: IncorrectRecordsWfStepsViewModel by viewModels()
    private val binding: ActivityIncorrectRecordsWfStepsBinding by viewBinding(
        ActivityIncorrectRecordsWfStepsBinding::inflate
    )

    private val onWfStepClickListener = object : OnIncorrectRecordWfStepClickListener {
        override fun onClicked(wfStep: IncorrectWfStep?) {
            Intent(
                this@IncorrectRecordsWfStepsActivity,
                IncorrectRecordsActivity::class.java
            ).apply {
                putExtra(Extras.START_DATE, workFlowStepsViewModel.startDate)
                putExtra(Extras.END_DATE, workFlowStepsViewModel.endDate)
                putExtra(Extras.WORK_TYPE, workFlowStepsViewModel.workType)
                putExtra(Extras.USER_ROLE, workFlowStepsViewModel.userRole)
                putExtra(Extras.WF_STEP, wfStep?.wfStep)
                putExtra(Extras.USER_ROLE, workFlowStepsViewModel.userRole)
                putExtra(Extras.USER_ID, workFlowStepsViewModel.userId)
                startActivity(this)
            }
        }
    }
    private val wfStepsAdapter = IncorrectRecordsWfStepsAdapter(onWfStepClickListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        setupViews()
        setExtras()
        workFlowStepsViewModel.state.observe(this, stateObserver)

        binding.pullToRefresh.setOnRefreshListener {
            workFlowStepsViewModel.fetchIncorrectRecordsWfSteps()
        }
    }

    private fun setExtras() {
        val startDate = intent.getStringExtra(Extras.START_DATE)
        val endDate = intent.getStringExtra(Extras.END_DATE)
        val workType = intent.getStringExtra(Extras.WORK_TYPE)
        val userRole = intent.getStringExtra(Extras.USER_ROLE)
        val userId = intent.getIntExtra(Extras.USER_ID, -1)

        workFlowStepsViewModel.workType = workType
        workFlowStepsViewModel.startDate = startDate
        workFlowStepsViewModel.endDate = endDate
        workFlowStepsViewModel.userRole = userRole
        workFlowStepsViewModel.userId = userId
        workFlowStepsViewModel.isForMember = userId != -1

        title = when (workType) {
            WorkType.VALIDATION -> getString(R.string.incorrect_judgments)
            WorkType.ANNOTATION -> getString(R.string.incorrect_annotations)
            else -> getString(R.string.incorrect_reviews)
        }
        workFlowStepsViewModel.fetchIncorrectRecordsWfSteps()
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
            IncorrectRecordsWfStepsViewModel.NoWfStepState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noWfStepsContainer.visible()
                binding.wfStepsView.gone()
            }
            is IncorrectRecordsWfStepsViewModel.SetUpIncorrectRecordsWfStepsState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.pullToRefresh.isRefreshing = false
                binding.noWfStepsContainer.gone()
                binding.wfStepsView.visible()

                wfStepsAdapter.addAll(it.wfSteps)
                wfStepsAdapter.notifyDataSetChanged()
                binding.wfStepsView.scheduleLayoutAnimation()
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

    override fun showMessage(message: String) {
        Snackbar.make(binding.shimmerViewContainer, message, Snackbar.LENGTH_SHORT).show()
    }
}