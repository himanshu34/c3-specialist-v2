package co.nayan.review.recordsgallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import co.nayan.appsession.SessionActivity
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.utils.*
import co.nayan.c3v2.core.widgets.CustomAlertDialogFragment
import co.nayan.c3v2.core.widgets.CustomAlertDialogListener
import co.nayan.c3v2.core.widgets.ProgressDialogFragment
import co.nayan.review.R
import co.nayan.review.config.Extras
import co.nayan.review.config.ScreenValue
import co.nayan.review.config.Tag
import co.nayan.review.databinding.ActivityReviewBinding
import co.nayan.review.incorrectreviews.IncorrectReviewsActivity
import co.nayan.review.models.ReviewIntentInputData
import co.nayan.review.recorddetail.ReviewCallbackInput
import co.nayan.review.recorddetail.ReviewResultCallBack
import co.nayan.review.utils.DragSelectTouchListener
import co.nayan.review.utils.DragSelectionProcessor
import co.nayan.review.viewBinding
import co.nayan.review.widgets.ManagerDisabledAlertFragment
import co.nayan.review.widgets.ManagerDisabledDialogListener
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReviewActivity : SessionActivity() {

    private val binding: ActivityReviewBinding by viewBinding(ActivityReviewBinding::inflate)
    private val reviewViewModel: ReviewViewModel by viewModels()
    private var workAssignment: WorkAssignment? = null
    private val mMode = DragSelectionProcessor.Mode.Simple
    private lateinit var mDragSelectTouchListener: DragSelectTouchListener
    private lateinit var mDragSelectionProcessor: DragSelectionProcessor

    @Inject
    lateinit var errorUtils: ErrorUtils
    private lateinit var reviewRecordsAdapter: ReviewRecordsAdapter
    private var spanCount = 2

    private val recordClickListener = object : RecordClickListener {
        override fun onItemClicked(record: Record) {
            reviewResultCallback.launch(
                ReviewCallbackInput(
                    ScreenValue.REVIEW,
                    reviewViewModel.currentTabPosition,
                    record.id,
                    reviewViewModel.contrastValue.value,
                    reviewViewModel.question,
                    reviewViewModel.applicationMode,
                    reviewViewModel.appFlavor,
                    workAssignment,
                    reviewViewModel.getRecordItems() as ArrayList<RecordItem>
                )
            )
        }

        override fun onLongPressed(position: Int, record: Record) {
            mDragSelectTouchListener.startDragSelection(position)
            invalidateOptionsMenu()
        }

        override fun updateRecordsCount(selectedCount: Int, totalCount: Int) {
            binding.selectedRecordsCountTxt.text = String.format("%d/%d", selectedCount, totalCount)
        }

        override fun starRecord(record: Record, position: Int, status: Boolean) {}
        override fun resetRecords() {
            if (isAdapterInitialized()) reviewRecordsAdapter.onBackPressed()
            invalidateOptionsMenu()
        }
    }

    private val reviewResultCallback =
        registerForActivityResult(ReviewResultCallBack()) { dataRequest ->
            // If records submitted successfully refresh the view
            dataRequest?.let {
                reviewViewModel.updateRecords(it)
                setupTabItems()
                setupRecords()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupActionBar(binding.appToolbar)
        binding.appToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        title = getString(R.string.review_records)

        setupExtras()
        setupClicks()
        setupViewsAndData()
        setupTabItems()
        reviewViewModel.contrastValue.observe(this, contrastObserver)
        reviewViewModel.state.observe(this, stateObserver)
        reviewViewModel.fetchRecords()
    }

    private fun setupTabItems() {
        binding.recordsTypeTl.getTabAt(TabPositions.PENDING)?.text = String.format(
            getString(R.string.pending_head),
            reviewViewModel.getRecords(TabPositions.PENDING).count()
        )
        binding.recordsTypeTl.getTabAt(TabPositions.APPROVED)?.text = String.format(
            getString(R.string.approved_head),
            reviewViewModel.getRecords(TabPositions.APPROVED).count()
        )
        binding.recordsTypeTl.getTabAt(TabPositions.REJECTED)?.text = String.format(
            getString(R.string.rejected_head),
            reviewViewModel.getRecords(TabPositions.REJECTED).count()
        )

        binding.selectedRecordsCountTxt.text = ""
        invalidateOptionsMenu()
        if (isAdapterInitialized()) reviewRecordsAdapter.selectionMode = false
    }

    private val tabSelectedListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabReselected(tab: TabLayout.Tab?) {}
        override fun onTabUnselected(tab: TabLayout.Tab?) {}
        override fun onTabSelected(tab: TabLayout.Tab?) {
            reviewViewModel.currentTabPosition = tab?.position ?: 0
            reviewRecordsAdapter.selectionMode = false
            setupRecords()
            invalidateOptionsMenu()
        }
    }

    private fun setupRecords() {
        val records = reviewViewModel.getRecords()
        if (records.isNullOrEmpty()) {
            binding.recordsView.gone()
            binding.noRecordsContainer.visible()
        } else {
            binding.recordsView.visible()
            binding.noRecordsContainer.gone()
            if (isAdapterInitialized())
                reviewRecordsAdapter.addAll(records as MutableList<Record>)
        }
    }

    private fun setupViewsAndData() {
        spanCount = reviewViewModel.getSpanCount()
        binding.gridSelectorIv.isSelected = spanCount == 2
        val appFlavor = intent.getStringExtra(APP_FLAVOR)
        reviewRecordsAdapter = ReviewRecordsAdapter(
            reviewViewModel.isSelectionEnabled(),
            reviewViewModel.applicationMode,
            recordClickListener,
            reviewViewModel.question,
            appFlavor
        )
        setupRecordsView()
        binding.recordsView.adapter = reviewRecordsAdapter
        binding.recordsView.addOnItemTouchListener(mDragSelectTouchListener)
        binding.recordsTypeTl.addOnTabSelectedListener(tabSelectedListener)
    }

    private fun setupRecordsView() {
        if (isAdapterInitialized()) {
            val gridLayoutManager = GridLayoutManager(this, spanCount)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val itemViewType = reviewRecordsAdapter.getItemViewType(position)
                    return if (itemViewType == ReviewRecordsAdapter.ITEM_TYPE_HEADER) spanCount else 1
                }
            }
            binding.recordsView.layoutManager = gridLayoutManager
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val hideOverlayIvTouchListener = View.OnTouchListener { _, event ->
        if (isAdapterInitialized()) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    reviewRecordsAdapter.showOverlay = false
                    reviewRecordsAdapter.notifyDataSetChanged()
                }

                MotionEvent.ACTION_UP -> {
                    reviewRecordsAdapter.showOverlay = true
                    reviewRecordsAdapter.notifyDataSetChanged()
                }
            }
        }
        true
    }

    private fun setupClicks() {
        // Drag Selection Listener
        mDragSelectionProcessor =
            DragSelectionProcessor(object : DragSelectionProcessor.ISelectionHandler {
                override fun getSelection(): HashSet<Int> {
                    return reviewRecordsAdapter.getSelection()
                }

                override fun isSelected(index: Int): Boolean {
                    return reviewRecordsAdapter.getSelection().contains(index)
                }

                override fun updateSelection(
                    start: Int,
                    end: Int,
                    isSelected: Boolean,
                    calledFromOnStart: Boolean
                ) {
                    reviewRecordsAdapter.selectRange(start, end, isSelected)
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
            reviewViewModel.saveSpanCount(spanCount)
            setupRecordsView()
        }

        binding.contrastIv.setOnClickListener {
            if (it.isSelected) {
                it.unSelected()
                binding.selectedRecordsCountTxt.visible()
                binding.contrastSlider.gone()
            } else {
                it.selected()
                binding.contrastSlider.visible()
                binding.selectedRecordsCountTxt.gone()
            }
        }

        binding.approveFb.setOnClickListener {
            showAlert(
                message = getString(R.string.approve_message),
                title = getString(R.string.approve_records_alert),
                showPositiveBtn = true,
                positiveText = getString(R.string.approve),
                showNegativeBtn = true,
                negativeText = getString(R.string.cancel),
                shouldFinish = false,
                tag = Tag.APPROVE_RECORDS
            )
        }

        binding.rejectBtn.setOnClickListener {
            showAlert(
                message = getString(R.string.reject_message),
                title = getString(R.string.reject_alert),
                showPositiveBtn = true,
                positiveText = getString(R.string.reject),
                showNegativeBtn = true,
                negativeText = getString(R.string.cancel),
                shouldFinish = false,
                tag = Tag.REJECT_RECORDS
            )
        }

        binding.submitFb.setOnClickListener {
            showAlert(
                message = getString(R.string.submit_alert_records_message),
                title = getString(R.string.submit_records),
                showPositiveBtn = true,
                positiveText = getString(R.string.submit),
                showNegativeBtn = true,
                negativeText = getString(R.string.cancel),
                shouldFinish = false,
                tag = Tag.SUBMIT_RECORDS
            )
        }

        binding.contrastSlider.setOnSeekBarChangeListener(onSeekBarChangeListener)
    }

    private val onSeekBarChangeListener = object : OnSeekBarChangeListener() {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            reviewViewModel.saveContrastValue(progress)
        }
    }

    private val contrastObserver: Observer<Int> = Observer {
        if (isAdapterInitialized()) {
            binding.contrastSlider.progress = it
            reviewRecordsAdapter.contrast = ImageUtils.getColorMatrix(it)
            reviewRecordsAdapter.notifyDataSetChanged()
        }
    }

    private fun setupExtras() {
        reviewViewModel.appFlavor = intent.getStringExtra(APP_FLAVOR)
        workAssignment = intent.parcelable(WORK_ASSIGNMENT)
        if (workAssignment != null) {
            reviewViewModel.workAssignmentId = workAssignment?.id
            reviewViewModel.wfStepId = workAssignment?.wfStep?.id
            reviewViewModel.applicationMode = workAssignment?.applicationMode
            reviewViewModel.question = workAssignment?.wfStep?.question
            setMetaData(
                workAssignment?.id,
                workAssignment?.wfStep?.id,
                workAssignment?.workType,
                reviewViewModel.currentRole()
            )
        } else this@ReviewActivity.finish()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> showProgressDialog(getString(R.string.fetching_records))
            ReviewViewModel.RecordsSuccessState -> {
                reviewRecordsAdapter.selectionMode = false
                invalidateOptionsMenu()
                hideProgressDialog()
                binding.recordsTypeTl.selectTab(binding.recordsTypeTl.getTabAt(TabPositions.PENDING))
                setupTabItems()
                setupRecords()
            }

            ReviewViewModel.RecordsFinishedState -> {
                hideProgressDialog()
                val message = getString(R.string.no_more_records)
                showAlert(
                    message = message,
                    shouldFinish = true,
                    tag = Tag.RECORDS_FINISHED,
                    showPositiveBtn = true,
                    isCancelable = false
                )
            }

            ReviewViewModel.RecordSubmissionProgressState -> {
                showProgressDialog(getString(R.string.submitting_records_message))
            }

            ReviewViewModel.FailureState -> {
                hideProgressDialog()
                showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }

            ReviewViewModel.UserAccountLockedState -> {
                hideProgressDialog()
                showAccountLockedDialog()
            }

            is ErrorState -> {
                hideProgressDialog()
                showAlert(
                    message = errorUtils.parseExceptionMessage(it.exception),
                    shouldFinish = true,
                    tag = Tag.ERROR,
                    showPositiveBtn = true
                )
            }
        }
    }

    private fun setupJudgmentButtons() {
        when (reviewViewModel.currentTabPosition) {
            TabPositions.APPROVED -> {
                binding.approveFb.gone()
                binding.rejectBtn.visible()
            }

            TabPositions.REJECTED -> {
                binding.approveFb.visible()
                binding.rejectBtn.gone()
            }

            else -> {
                binding.approveFb.visible()
                binding.rejectBtn.visible()
            }
        }
    }

    private fun isInSelectionMode() = reviewRecordsAdapter.selectionMode

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_review, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (isInSelectionMode().not()) {
            binding.selectedRecordsCountTxt.text = ""
            binding.judgmentContainer.gone()
            if (reviewViewModel.getRecords(TabPositions.PENDING)
                    .isEmpty()
            ) binding.submitFb.visible()
            else binding.submitFb.gone()
        } else {
            binding.judgmentContainer.visible()
            binding.submitFb.gone()
            setupJudgmentButtons()
        }

        menu.findItem(R.id.options)?.isEnabled =
            !isInSelectionMode() &&
                    reviewViewModel.currentTabPosition == TabPositions.PENDING &&
                    reviewViewModel.getRecords(TabPositions.PENDING).isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_approveRecords -> {
                showAlert(
                    message = getString(R.string.approve_all_records_message),
                    title = getString(R.string.approve_all_records_alert),
                    showPositiveBtn = true,
                    positiveText = getString(R.string.approve_all),
                    showNegativeBtn = true,
                    negativeText = getString(R.string.cancel),
                    shouldFinish = false,
                    tag = Tag.APPROVE_ALL_RECORDS
                )
            }

            R.id.action_rejectRecords -> {
                showAlert(
                    message = getString(R.string.reject_all_records_message),
                    title = getString(R.string.reject_all_records_alert),
                    showPositiveBtn = true,
                    positiveText = getString(R.string.reject_all),
                    showNegativeBtn = true,
                    negativeText = getString(R.string.cancel),
                    shouldFinish = false,
                    tag = Tag.REJECT_ALL_RECORDS
                )
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        invalidateOptionsMenu()
        if (isAdapterInitialized() && reviewRecordsAdapter.onBackPressed().not()) {
            val reviewedCount = reviewViewModel.reviewedRecordsCount()
            if (reviewedCount > 0) {
                showAlert(
                    message = getString(R.string.reject_records_warning),
                    tag = Tag.REJECTED_RECORDS_SUBMISSION_WARNING,
                    showPositiveBtn = true,
                    showNegativeBtn = true,
                    isCancelable = false,
                    shouldFinish = true
                )
            } else super.onBackPressed()
        }
    }

    private fun isAdapterInitialized() = this@ReviewActivity::reviewRecordsAdapter.isInitialized

    private fun showProgressDialog(message: String) {
        hideProgressDialog()
        val progressDialog = ProgressDialogFragment()
        progressDialog.setMessage(message)
        progressDialog.show(supportFragmentManager.beginTransaction(), message)
    }

    private fun hideProgressDialog() {
        supportFragmentManager.fragments.forEach {
            if (it is ProgressDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.recordsView, message, Snackbar.LENGTH_SHORT).show()
    }

    private val customAlertDialogListener = object : CustomAlertDialogListener {
        override fun onPositiveBtnClick(shouldFinish: Boolean, tag: String?) {
            when (tag) {
                Tag.REJECT_RECORDS -> {
                    val selectedRecordIds = reviewRecordsAdapter.getSelectedItems()
                    reviewViewModel.rejectRecords(selectedRecordIds)
                    setupTabItems()
                    setupRecords()
                }

                Tag.APPROVE_RECORDS -> {
                    val selectedRecordIds = reviewRecordsAdapter.getSelectedItems()
                    reviewViewModel.approveRecords(selectedRecordIds)
                    setupTabItems()
                    setupRecords()
                }

                Tag.APPROVE_ALL_RECORDS -> {
                    reviewViewModel.approveAllRecords()
                    setupTabItems()
                    setupRecords()
                }

                Tag.REJECT_ALL_RECORDS -> {
                    reviewViewModel.rejectAllRecords()
                    setupTabItems()
                    setupRecords()
                }

                Tag.SUBMIT_RECORDS -> {
                    reviewViewModel.submitRecords()
                }
            }
            if (shouldFinish) {
                this@ReviewActivity.finish()
            }
        }

        override fun onNegativeBtnClick(shouldFinish: Boolean, tag: String?) {}
    }

    private fun showAlert(
        message: String,
        shouldFinish: Boolean,
        tag: String,
        title: String? = null,
        positiveText: String? = null,
        negativeText: String? = null,
        showPositiveBtn: Boolean = false,
        showNegativeBtn: Boolean = false,
        isCancelable: Boolean = true
    ) {
        supportFragmentManager.fragments.forEach {
            if (it is CustomAlertDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }

        val customAlertDialogFragment =
            CustomAlertDialogFragment.newInstance(customAlertDialogListener).apply {
                setTitle(title)
                setMessage(message)
                showNegativeBtn(showNegativeBtn)
                showPositiveBtn(showPositiveBtn)
                shouldFinish(shouldFinish)
                if (positiveText != null)
                    setPositiveBtnText(positiveText)
                if (negativeText != null)
                    setNegativeBtnText(negativeText)
            }
        customAlertDialogFragment.isCancelable = isCancelable
        customAlertDialogFragment.show(supportFragmentManager.beginTransaction(), tag)
    }

    private val managerDisabledDialogListener = object : ManagerDisabledDialogListener {
        override fun onPositiveBtnClick() {
            setResult(
                Activity.RESULT_OK,
                Intent().apply { putExtra(ACCOUNT_LOCKED, true) }
            )
            this@ReviewActivity.finish()
        }

        override fun onNegativeBtnClick() {
            moveToIncorrectReviewsScreen()
        }
    }

    private fun moveToIncorrectReviewsScreen() {
        Intent(this@ReviewActivity, IncorrectReviewsActivity::class.java).apply {
            putExtra(Extras.APPLICATION_MODE, reviewViewModel.applicationMode)
            putExtra(Extras.CONTRAST_VALUE, reviewViewModel.contrastValue.value)
            putParcelableArrayListExtra(
                Extras.RECORDS,
                reviewViewModel.getIncorrectSniffingRecords() as ArrayList<out Parcelable>
            )
            putExtra(Extras.QUESTION, reviewViewModel.question)
            putExtra(APP_FLAVOR, intent.getStringExtra(APP_FLAVOR))
            startActivity(this)
        }
    }

    private fun showAccountLockedDialog() {
        ManagerDisabledAlertFragment.newInstance(
            managerDisabledDialogListener,
            getString(R.string.your_account_is_locked_due_to_wrong_submission_of_records)
        ).show(supportFragmentManager.beginTransaction(), "Account Locked")
    }

    class ResultCallback : ActivityResultContract<ReviewIntentInputData, Boolean>() {
        override fun createIntent(context: Context, input: ReviewIntentInputData): Intent {
            return Intent(context, ReviewActivity::class.java).apply {
                putExtra(WORK_ASSIGNMENT, input.workAssignment)
                putExtra(APP_FLAVOR, input.appFlavor)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode == Activity.RESULT_OK &&
                    intent?.getBooleanExtra(ACCOUNT_LOCKED, false) ?: false
        }
    }

    companion object {
        const val APP_FLAVOR = "app_flavor"
        const val WORK_ASSIGNMENT = "work_assignment"
        const val ACCOUNT_LOCKED = "account_locked"
    }
}