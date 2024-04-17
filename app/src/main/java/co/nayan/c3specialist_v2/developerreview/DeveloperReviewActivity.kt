package co.nayan.c3specialist_v2.developerreview

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityDeveloperReviewBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.utils.*
import co.nayan.review.config.ScreenValue
import co.nayan.review.config.Tag
import co.nayan.review.recorddetail.ReviewCallbackInput
import co.nayan.review.recorddetail.ReviewResultCallBack
import co.nayan.review.recordsgallery.RecordClickListener
import co.nayan.review.recordsgallery.TabPositions
import co.nayan.review.utils.DragSelectTouchListener
import co.nayan.review.utils.DragSelectionProcessor
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DeveloperReviewActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val developerReviewViewModel: DeveloperReviewViewModel by viewModels()
    private val binding: ActivityDeveloperReviewBinding by viewBinding(
        ActivityDeveloperReviewBinding::inflate
    )
    private var spanCount = 2
    private var workAssignment: WorkAssignment? = null
    private val mMode = DragSelectionProcessor.Mode.Simple
    private lateinit var mDragSelectTouchListener: DragSelectTouchListener
    private lateinit var mDragSelectionProcessor: DragSelectionProcessor

    private val recordClickListener = object : RecordClickListener {
        override fun onItemClicked(record: Record) {
            reviewResultCallback.launch(
                ReviewCallbackInput(
                    ScreenValue.WORK_STEP,
                    TabPositions.PENDING,
                    record.id,
                    developerReviewViewModel.contrastValue.value,
                    developerReviewViewModel.question,
                    developerReviewViewModel.applicationMode,
                    BuildConfig.FLAVOR,
                    workAssignment,
                    developerReviewViewModel.getRecords()
                )
            )
        }

        override fun onLongPressed(position: Int, record: Record) {
            setupJudgmentContainer()
            mDragSelectTouchListener.startDragSelection(position)
        }

        override fun updateRecordsCount(selectedCount: Int, totalCount: Int) {
            binding.selectedRecordsCountTxt.text = String.format("%d/%d", selectedCount, totalCount)
        }

        override fun starRecord(record: Record, position: Int, status: Boolean) {
            developerReviewViewModel.updateRecordStarredStatus(record, position, status)
        }

        override fun resetRecords() {}
    }

    private val reviewResultCallback =
        registerForActivityResult(ReviewResultCallBack()) { dataRequest ->
            // If records submitted successfully refresh the view
            dataRequest?.let { recordItems ->
                val records = recordItems.map { it.record }
                developerReviewViewModel.updateRecords(records)
            }
        }

    private lateinit var developerRecordsAdapter: DeveloperRecordsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        developerRecordsAdapter = DeveloperRecordsAdapter(recordClickListener)
        developerRecordsAdapter.lastItemMargin = 68f.dpToPixel()
        initVariables()
        setupClicks()
        setupRecordsView()

        developerReviewViewModel.contrastValue.observe(this, contrastObserver)
        developerReviewViewModel.state.observe(this, stateObserver)
        developerReviewViewModel.fetchTrainingRecords()
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra(
                WORK_ASSIGNMENT_USED,
                false
            )
        ) developerReviewViewModel.assignWork()
        else intent.putExtra(WORK_ASSIGNMENT_USED, true)
    }

    private fun initVariables() {
        spanCount = developerReviewViewModel.getSavedSpanCount()
        binding.gridSelectorIv.isSelected = spanCount == 2

        workAssignment = intent.parcelable(Extras.WORK_ASSIGNMENT)
        if (workAssignment == null) finish()
        else {
            title = workAssignment?.wfStep?.name
            developerReviewViewModel.workAssignmentId = workAssignment?.id
            developerReviewViewModel.question = workAssignment?.wfStep?.question
            developerReviewViewModel.applicationMode = workAssignment?.applicationMode
            developerRecordsAdapter.applicationMode = workAssignment?.applicationMode
            binding.questionTxt.text = workAssignment?.wfStep?.question
        }
        binding.recordsView.adapter = developerRecordsAdapter
    }

    private val contrastObserver: Observer<Int> = Observer {
        binding.contrastSlider.progress = it
        developerRecordsAdapter.contrast = ImageUtils.getColorMatrix(it)
        developerRecordsAdapter.notifyDataSetChanged()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                showProgressDialog(getString(R.string.please_wait_fetching_records))
                binding.noRecordsContainer.gone()
                binding.recordsView.gone()
            }

            DeveloperReviewViewModel.NoRecordState -> {
                hideProgressDialog()
                hideJudgmentContainer()
                binding.selectedRecordsCountTxt.text = ""
                binding.noRecordsContainer.visible()
                binding.recordsView.gone()
                val message = getString(co.nayan.review.R.string.no_more_records)
                showAlert(
                    message = message,
                    shouldFinish = true,
                    tag = Tag.RECORDS_FINISHED,
                    showPositiveBtn = true,
                    isCancelable = false
                )
            }

            is DeveloperReviewViewModel.SetUpRecordsState -> {
                developerRecordsAdapter.selectionMode = false
                setupJudgmentContainer()
                hideProgressDialog()
                binding.noRecordsContainer.gone()
                binding.recordsView.visible()

                developerRecordsAdapter.addAll(it.records)
                binding.recordsView.addOnScrollListener(onScrollChangeListener)
            }

            DeveloperReviewViewModel.RecordStatusProgressState -> {
                showProgressDialog(getString(co.nayan.canvas.R.string.please_wait))
            }

            is DeveloperReviewViewModel.RecordsStarredStatusState -> {
                hideProgressDialog()
                developerRecordsAdapter.updateRecordStatus(it.position, it.status)
            }

            is ErrorState -> {
                hideProgressDialog()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private val onScrollChangeListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            setupJudgmentContainer()
        }
    }

    private fun setupRecordsView() {
        val gridLayoutManager = GridLayoutManager(this, spanCount)
        binding.recordsView.layoutManager = gridLayoutManager
        binding.recordsView.addOnItemTouchListener(mDragSelectTouchListener)
    }

    @SuppressLint("ClickableViewAccessibility")
    private val hideOverlayIvTouchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                developerRecordsAdapter.showOverlay = false
                developerRecordsAdapter.notifyDataSetChanged()
            }

            MotionEvent.ACTION_UP -> {
                developerRecordsAdapter.showOverlay = true
                developerRecordsAdapter.notifyDataSetChanged()
            }
        }
        true
    }

    private fun setupClicks() {
        // Drag Selection Listener
        mDragSelectionProcessor =
            DragSelectionProcessor(object : DragSelectionProcessor.ISelectionHandler {
                override fun getSelection(): HashSet<Int> {
                    return developerRecordsAdapter.getSelection()
                }

                override fun isSelected(index: Int): Boolean {
                    return developerRecordsAdapter.getSelection().contains(index)
                }

                override fun updateSelection(
                    start: Int,
                    end: Int,
                    isSelected: Boolean,
                    calledFromOnStart: Boolean
                ) {
                    developerRecordsAdapter.selectRange(start, end, isSelected)
                }
            }).withMode(mMode)
        mDragSelectTouchListener = DragSelectTouchListener()
            .withSelectListener(mDragSelectionProcessor)
        mDragSelectionProcessor.withMode(mMode)

        binding.hideOverlayIv.setOnTouchListener(hideOverlayIvTouchListener)
        binding.gridSelectorIv.setOnClickListener {
            if (it.isSelected) {
                it.unSelected()
                spanCount -= 1
            } else {
                it.selected()
                spanCount += 1
            }
            developerReviewViewModel.saveSpanCount(spanCount)
            setupRecordsView()
        }
        binding.contrastIv.setOnClickListener {
            if (it.isSelected) {
                it.unSelected()
                binding.contrastSlider.gone()
            } else {
                it.selected()
                binding.contrastSlider.visible()
            }
        }
        binding.approveBtn.setOnClickListener {
            showAlert(
                message = getString(co.nayan.review.R.string.approve_message),
                title = getString(co.nayan.review.R.string.approve_records_alert),
                showPositiveBtn = true,
                positiveText = getString(co.nayan.review.R.string.approve),
                showNegativeBtn = true,
                negativeText = getString(R.string.cancel),
                shouldFinish = false,
                tag = Tag.APPROVE_RECORDS
            )
        }
        binding.resetBtn.setOnClickListener {
            showAlert(
                message = getString(co.nayan.review.R.string.reject_message),
                title = getString(co.nayan.review.R.string.reject_alert),
                showPositiveBtn = true,
                positiveText = getString(co.nayan.review.R.string.reject),
                showNegativeBtn = true,
                negativeText = getString(R.string.cancel),
                shouldFinish = false,
                tag = Tag.REJECT_RECORDS
            )
        }
        binding.approveAllBtn.setOnClickListener {
            showAlert(
                message = getString(co.nayan.review.R.string.approve_all_records_message),
                title = getString(co.nayan.review.R.string.approve_all_records_alert),
                showPositiveBtn = true,
                positiveText = getString(co.nayan.review.R.string.approve_all),
                showNegativeBtn = true,
                negativeText = getString(R.string.cancel),
                shouldFinish = false,
                tag = Tag.APPROVE_ALL_RECORDS
            )
        }
        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangeListener)
    }

    private val onSeekBarChangeListener = object : OnSeekBarChangeListener() {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            developerReviewViewModel.saveContrastValue(progress)
        }
    }

    private fun showJudgmentContainer() = lifecycleScope.launch {
        if (binding.judgmentContainer.isVisible.not()) {
            binding.judgmentContainer.visible()
            val animation =
                AnimationUtils.loadAnimation(this@DeveloperReviewActivity, R.anim.slide_up)
            binding.judgmentContainer.startAnimation(animation)
        }
    }

    private fun hideJudgmentContainer() = lifecycleScope.launch {
        if (binding.judgmentContainer.isVisible) {
            val animation =
                AnimationUtils.loadAnimation(this@DeveloperReviewActivity, R.anim.slide_down)
            binding.judgmentContainer.startAnimation(animation)
            binding.judgmentContainer.invisible()
        }
    }

    private fun setupJudgmentContainer() = lifecycleScope.launch {
        if (isInSelectionMode()) {
            binding.resetBtn.visible()
            binding.approveBtn.visible()
            binding.approveAllBtn.gone()
            binding.selectedRecordsCountTxt.visible()
            binding.questionTxt.gone()
            showJudgmentContainer()
        } else {
            binding.selectedRecordsCountTxt.gone()
            binding.questionTxt.visible()
            val layoutManager = binding.recordsView.layoutManager as GridLayoutManager
            val visibleCount = layoutManager.findLastVisibleItemPosition()

            if (visibleCount >= developerRecordsAdapter.itemCount - 1) {
                binding.resetBtn.gone()
                binding.approveBtn.gone()
                binding.approveAllBtn.visible()
                showJudgmentContainer()
            } else hideJudgmentContainer()
        }
    }

    override fun alertDialogNegativeClick(shouldFinishActivity: Boolean, tag: String?) {
        if (shouldFinishActivity) {
            this@DeveloperReviewActivity.finish()
        }
    }

    override fun alertDialogPositiveClick(shouldFinishActivity: Boolean, tag: String?) {
        when (tag) {
            Tag.REJECT_RECORDS -> {
                val selectedRecordIds = developerRecordsAdapter.getSelectedItems()
                showProgressDialog(getString(co.nayan.review.R.string.submitting_rejected_records_message))
                developerReviewViewModel.rejectRecords(selectedRecordIds)
            }

            Tag.APPROVE_RECORDS -> {
                val selectedRecordIds = developerRecordsAdapter.getSelectedItems()
                showProgressDialog(getString(co.nayan.review.R.string.submitting_approved_records_message))
                developerReviewViewModel.approveRecords(selectedRecordIds)
            }

            Tag.APPROVE_ALL_RECORDS -> {
                showProgressDialog(getString(co.nayan.review.R.string.submitting_approved_records_message))
                developerReviewViewModel.approveAllRecords()
            }
        }

        if (shouldFinishActivity) {
            this@DeveloperReviewActivity.finish()
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.recordsView, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun Float.dpToPixel(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics
        ).toInt()
    }

    override fun onBackPressed() {
        if (!developerRecordsAdapter.onBackPressed()) super.onBackPressed()
        setupJudgmentContainer()
    }

    private fun isInSelectionMode() = developerRecordsAdapter.selectionMode

    companion object {
        const val WORK_ASSIGNMENT_USED = "work_assignment_used"
    }
}