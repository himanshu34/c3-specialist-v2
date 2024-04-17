package co.nayan.review.recorddetail

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import co.nayan.appsession.SessionActivity
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.parcelableArrayList
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.widgets.CustomAlertDialogFragment
import co.nayan.c3v2.core.widgets.CustomAlertDialogListener
import co.nayan.c3v2.core.widgets.ProgressDialogFragment
import co.nayan.review.R
import co.nayan.review.config.Extras
import co.nayan.review.config.ScreenValue
import co.nayan.review.config.Tag
import co.nayan.review.databinding.ActivityRecordDetailBinding
import co.nayan.review.recordsgallery.RecordItem
import co.nayan.review.recordsgallery.ReviewViewModel
import co.nayan.review.recordsgallery.TabPositions.APPROVED
import co.nayan.review.recordsgallery.TabPositions.PENDING
import co.nayan.review.recordsgallery.TabPositions.REJECTED
import co.nayan.review.utils.betterSmoothScrollToPosition
import co.nayan.review.viewBinding
import co.nayan.review.widgets.RecordInfoDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RecordDetailActivity : SessionActivity() {

    private val reviewViewModel: ReviewViewModel by viewModels()
    private val binding: ActivityRecordDetailBinding by viewBinding(ActivityRecordDetailBinding::inflate)

    @Inject
    lateinit var errorUtils: ErrorUtils
    private lateinit var recordDetailAdapter: RecordDetailAdapter
    private lateinit var recordItems: ArrayList<RecordItem>
    private var landedFrom = ScreenValue.REVIEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupActionBar(binding.actionBar.appToolbar)
        binding.actionBar.appToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val canvasMode = intent.getStringExtra(Extras.APPLICATION_MODE)
        val question = intent.getStringExtra(Extras.QUESTION)
        title = question
        val starred = intent.getBooleanExtra(Extras.SHOW_STAR, false)
        val contrastValue = intent.getIntExtra(Extras.CONTRAST_VALUE, 50)
        landedFrom = intent.getIntExtra(Extras.LANDED_FROM, ScreenValue.REVIEW)
        val appFlavor = intent.getStringExtra(Extras.APP_FLAVOR)
        setupView(appFlavor, canvasMode, starred, contrastValue)
        val workAssignment = intent.parcelable<WorkAssignment>(Extras.WORK_ASSIGNMENT)
        setMetaData(
            workAssignment?.id,
            workAssignment?.wfStep?.id,
            workAssignment?.workType,
            reviewViewModel.currentRole()
        )
        if (::recordItems.isInitialized.not()) recordItems = arrayListOf()
        else recordItems.clear()
        val currentRecordId =
            savedInstanceState?.getInt(Extras.CURRENT_RECORD_ID, RecyclerView.NO_POSITION)
                ?: intent.getIntExtra(Extras.CURRENT_RECORD_ID, RecyclerView.NO_POSITION)
        val items: ArrayList<RecordItem>? =
            savedInstanceState?.parcelableArrayList(Extras.RECORD_ITEMS)
                ?: intent.parcelableArrayList(Extras.RECORD_ITEMS)
        setupExtras(currentRecordId, items, intent.getIntExtra(Extras.CURRENT_TAB, PENDING))

        reviewViewModel.state.observe(this, stateObserver)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendResultCallbackData()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val currentPosition =
            (binding.recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        if (currentPosition > 0 && currentPosition < recordItems.size - 1)
            outState.putInt(Extras.CURRENT_RECORD_ID, recordItems[currentPosition].record.id)
        else outState.putInt(Extras.CURRENT_RECORD_ID, RecyclerView.NO_POSITION)
        outState.putParcelableArrayList(Extras.RECORD_ITEMS, recordItems)
        super.onSaveInstanceState(outState)
    }

    private fun setupView(
        appFlavor: String?,
        canvasMode: String?,
        starred: Boolean,
        contrastValue: Int
    ) {
        recordDetailAdapter =
            RecordDetailAdapter(
                onRecordListener,
                resources.configuration.orientation,
                canvasMode,
                appFlavor,
                starred,
                contrastValue
            ) { record ->
                record?.let { setupRecordInfoDialog(it) }
            }
        binding.recyclerView.apply {
            val linearLayoutManager = LinearLayoutManager(context)
            linearLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
            layoutManager = linearLayoutManager
            adapter = recordDetailAdapter
        }
        PagerSnapHelper().attachToRecyclerView(binding.recyclerView)
    }

    private fun setupRecordInfoDialog(record: Record) = lifecycleScope.launch {
        supportFragmentManager.fragments.forEach {
            if (it is RecordInfoDialogFragment) {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
        }
        val recordInfoDialogFragment = RecordInfoDialogFragment.newInstance(record)
        recordInfoDialogFragment.show(
            supportFragmentManager.beginTransaction(),
            getString(R.string.record_info)
        )
    }

    private val onRecordListener = object : RecordListener {
        override fun onApproveClicked(recordItem: RecordItem) {
            if (landedFrom == ScreenValue.WORK_STEP)
                reviewViewModel.approveDeveloperRecord(listOf(recordItem.record.id))
            else {
                recordDetailAdapter.markRecordAsApproved(recordItem)
                if (recordDetailAdapter.itemCount == 0) sendResultCallbackData()
            }
        }

        override fun onResetClicked(recordItem: RecordItem) {
            if (landedFrom == ScreenValue.WORK_STEP)
                reviewViewModel.rejectDeveloperRecord(listOf(recordItem.record.id))
            else {
                recordDetailAdapter.markRecordAsRejected(recordItem)
                if (recordDetailAdapter.itemCount == 0) sendResultCallbackData()
            }
        }
    }

    private fun setupExtras(
        currentRecordId: Int,
        items: ArrayList<RecordItem>?,
        currentSelectedTab: Int
    ) {
        if (currentRecordId != RecyclerView.NO_POSITION) {
            items?.let {
                recordItems.addAll(it)
                val findRecord = recordItems.find { recordItem ->
                    recordItem.record.id == currentRecordId
                }
                when (currentSelectedTab) {
                    PENDING -> recordDetailAdapter.addAll(recordItems.filter { recordItem -> recordItem.isApproved.not() && recordItem.isRejected.not() })
                    APPROVED -> recordDetailAdapter.addAll(recordItems.filter { recordItem -> recordItem.isApproved && recordItem.isRejected.not() })
                    REJECTED -> recordDetailAdapter.addAll(recordItems.filter { recordItem -> recordItem.isApproved.not() && recordItem.isRejected })
                }
                binding.recyclerView.betterSmoothScrollToPosition(recordItems.indexOf(findRecord))
            }
        } else finish()
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> showProgressDialog(getString(R.string.submitting_records_message))
            is ReviewViewModel.RecordSubmittedState -> {
                hideProgressDialog()
                if (::recordDetailAdapter.isInitialized) {
                    val selectedRecord =
                        recordItems.find { recordItem -> recordItem.record.id == it.recordId }
                    selectedRecord?.let {
                        recordDetailAdapter.updateRecords(selectedRecord)
                        recordItems.remove(selectedRecord)
                    }
                    if (recordDetailAdapter.itemCount == 0) sendResultCallbackData()
                }
            }

            ReviewViewModel.FailureState -> {
                hideProgressDialog()
                showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
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

    private val customAlertDialogListener = object : CustomAlertDialogListener {
        override fun onPositiveBtnClick(shouldFinish: Boolean, tag: String?) {
            if (shouldFinish) sendResultCallbackData()
        }

        override fun onNegativeBtnClick(shouldFinish: Boolean, tag: String?) {}
    }

    private fun sendResultCallbackData() {
        setResult(Activity.RESULT_OK,
            Intent().apply {
                putParcelableArrayListExtra(
                    Extras.RECORD_ITEMS,
                    recordItems
                )
            }
        )
        finish()
    }
}