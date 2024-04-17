package co.nayan.c3specialist_v2.incorrectrecords

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityIncorrectRecordsActivityBinding
import co.nayan.c3specialist_v2.incorrectrecordsdetail.IncorrectAnnotationsDetailActivity
import co.nayan.c3specialist_v2.incorrectrecordsdetail.IncorrectJudgmentsDetailActivity
import co.nayan.c3specialist_v2.incorrectrecordsdetail.IncorrectReviewsDetailActivity
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IncorrectRecordsActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val incorrectRecordsViewModel: IncorrectRecordsViewModel by viewModels()
    private val binding: ActivityIncorrectRecordsActivityBinding by viewBinding(ActivityIncorrectRecordsActivityBinding::inflate)

    private val onIncorrectRecordClickListener = object : OnIncorrectRecordClickListener {
        override fun onClick(record: Record?) {
            record?.let {
                when (incorrectRecordsViewModel.workType) {
                    WorkType.ANNOTATION -> {
                        Intent(
                            this@IncorrectRecordsActivity,
                            IncorrectAnnotationsDetailActivity::class.java
                        ).apply {
                            putExtra(
                                Extras.INCORRECT_ANNOTATION,
                                incorrectRecordsViewModel.getIncorrectAnnotation(record.id)
                            )
                            startActivity(this)
                        }
                    }
                    WorkType.VALIDATION -> {
                        Intent(
                            this@IncorrectRecordsActivity,
                            IncorrectJudgmentsDetailActivity::class.java
                        ).apply {
                            putExtra(
                                Extras.INCORRECT_JUDGMENT,
                                incorrectRecordsViewModel.getIncorrectJudgment(record.id)
                            )
                            startActivity(this)
                        }
                    }
                    WorkType.REVIEW -> {
                        Intent(
                            this@IncorrectRecordsActivity,
                            IncorrectReviewsDetailActivity::class.java
                        ).apply {
                            putExtra(
                                Extras.INCORRECT_REVIEW,
                                incorrectRecordsViewModel.getIncorrectReview(record.id)
                            )
                            startActivity(this)
                        }
                    }
                    else -> {
                    }
                }
            }
        }
    }
    private val incorrectRecordsAdapter = IncorrectRecordsAdapter(onIncorrectRecordClickListener)
    private var isLoading = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupActionBar(binding.actionBar.appToolbar)

        setupRecordsView()

        incorrectRecordsViewModel.state.observe(this, stateObserver)
        setExtras()
    }

    private fun setExtras() {
        val startDate = intent.getStringExtra(Extras.START_DATE)
        val endDate = intent.getStringExtra(Extras.END_DATE)
        val workType = intent.getStringExtra(Extras.WORK_TYPE)
        val userRole = intent.getStringExtra(Extras.USER_ROLE)
        val userId = intent.getIntExtra(Extras.USER_ID, -1)
        val wfStep = intent.parcelable<WfStep>(Extras.WF_STEP)

        incorrectRecordsViewModel.workType = workType
        incorrectRecordsViewModel.startDate = startDate
        incorrectRecordsViewModel.endDate = endDate
        incorrectRecordsViewModel.userRole = userRole
        incorrectRecordsViewModel.userId = userId
        incorrectRecordsViewModel.isForMember = userId != -1
        incorrectRecordsViewModel.wfStepId = wfStep?.id

        title = if (wfStep?.name.isNullOrEmpty()) {
            when (workType) {
                WorkType.VALIDATION -> getString(R.string.incorrect_judgments)
                WorkType.ANNOTATION -> getString(R.string.incorrect_annotations)
                else -> getString(R.string.incorrect_reviews)
            }
        } else wfStep?.name
        incorrectRecordsViewModel.fetchIncorrectRecords()
    }

    @SuppressLint("NotifyDataSetChanged")
    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                isLoading = true
                showProgressDialog(getString(R.string.please_wait_fetching_records))
                binding.noRecordsContainer.gone()
                binding.recordsView.gone()
            }
            IncorrectRecordsViewModel.NoRecordState -> {
                hideProgressDialog()
                binding.noRecordsContainer.visible()
                binding.recordsView.gone()
            }
            is IncorrectRecordsViewModel.SetUpIncorrectRecordsState -> {
                isLoading = false
                hideProgressDialog()
                binding.noRecordsContainer.gone()
                binding.recordsView.visible()

                incorrectRecordsAdapter.isPaginationEnabled = it.isPaginationEnabled
                incorrectRecordsAdapter.addNewRecords(it.incorrectRecords)
                incorrectRecordsAdapter.notifyDataSetChanged()

                binding.recordsView.addOnScrollListener(onScrollChangeListener)
            }
            is IncorrectRecordsViewModel.SetUpNextPageRecordsState -> {
                isLoading = false
                incorrectRecordsAdapter.isPaginationEnabled = it.isPaginationEnabled
                incorrectRecordsAdapter.addNewRecords(it.incorrectRecords)
                incorrectRecordsAdapter.notifyDataSetChanged()
            }
            is ErrorState -> {
                hideProgressDialog()
                isLoading = false
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private val onScrollChangeListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = binding.recordsView.layoutManager as GridLayoutManager
            val visibleCount = layoutManager.findLastVisibleItemPosition()
            if (visibleCount == incorrectRecordsAdapter.itemCount - 1 && !isLoading) {
                isLoading = true
                incorrectRecordsViewModel.fetchNextPage()
            }
        }
    }

    private fun setupRecordsView() {
        val gridLayoutManager = GridLayoutManager(this, 3)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val itemViewType = incorrectRecordsAdapter.getItemViewType(position)
                return if (itemViewType == IncorrectRecordsAdapter.ITEM_TYPE_LOADER) 3
                else 1
            }
        }
        binding.recordsView.layoutManager = gridLayoutManager
        binding.recordsView.adapter = incorrectRecordsAdapter
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.recordsView, message, Snackbar.LENGTH_SHORT).show()
    }
}