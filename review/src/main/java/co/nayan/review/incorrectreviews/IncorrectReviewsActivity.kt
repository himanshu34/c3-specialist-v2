package co.nayan.review.incorrectreviews

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import co.nayan.appsession.SessionActivity
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.showDialogFragment
import co.nayan.c3v2.core.utils.ImageUtils
import co.nayan.c3v2.core.utils.parcelableArrayList
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.review.R
import co.nayan.review.config.Extras
import co.nayan.review.databinding.ActivityIncorrectReviewsBinding
import co.nayan.review.recordsgallery.RecordClickListener
import co.nayan.review.recordsgallery.ReviewActivity
import co.nayan.review.recordsgallery.ReviewRecordsAdapter
import co.nayan.review.recordsgallery.ReviewRepositoryInterface
import co.nayan.review.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IncorrectReviewsActivity : SessionActivity() {

    private val binding: ActivityIncorrectReviewsBinding by viewBinding(
        ActivityIncorrectReviewsBinding::inflate
    )

    @Inject
    lateinit var reviewRepositoryInterface: ReviewRepositoryInterface

    private lateinit var reviewRecordsAdapter: ReviewRecordsAdapter
    private var spanCount = 2
    private val records = mutableListOf<Record>()
    private var appFlavor: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar)
        binding.actionBar.appToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        title = getString(R.string.incorrect_records)

        setupViewsAndData()
        setupClicks()
    }

    private fun setupViewsAndData() {
        binding.gridSelectorIv.isSelected = spanCount == 2
        appFlavor = intent.getStringExtra(ReviewActivity.APP_FLAVOR)
        val applicationMode = intent.getStringExtra(Extras.APPLICATION_MODE)
        val question = intent.getStringExtra(Extras.QUESTION)
        val contrast = intent.getIntExtra(Extras.CONTRAST_VALUE, 50)
        intent.parcelableArrayList<Record>(Extras.RECORDS)?.let {
            records.clear()
            records.addAll(it)
        }
        reviewRecordsAdapter = ReviewRecordsAdapter(
            false,
            applicationMode,
            recordClickListener,
            question,
            appFlavor
        )
        reviewRecordsAdapter.isForIncorrectSniffingRecords = true
        reviewRecordsAdapter.contrast = ImageUtils.getColorMatrix(contrast)
        setupRecordsView()
        binding.recordsView.adapter = reviewRecordsAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupRecordsView() {
        if (isAdapterInitialized()) {
            val gridLayoutManager = GridLayoutManager(this, spanCount)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val itemViewType = reviewRecordsAdapter.getItemViewType(position)
                    return if (itemViewType == ReviewRecordsAdapter.ITEM_TYPE_HEADER) {
                        spanCount
                    } else {
                        1
                    }
                }
            }
            binding.recordsView.layoutManager = gridLayoutManager
            reviewRecordsAdapter.addAll(records)
        }
    }

    private fun setupClicks() {
        binding.gridSelectorIv.setOnClickListener {
            if (it.isSelected) {
                it.unSelected()
                spanCount -= 1
            } else {
                it.selected()
                spanCount += 1
            }
            setupRecordsView()
        }
    }

    private val recordClickListener = object : RecordClickListener {
        override fun onItemClicked(record: Record) {
            val question = intent.getStringExtra(Extras.QUESTION)
            when (record.mediaType) {
                MediaType.VIDEO -> {
                    val targetActivity = reviewRepositoryInterface.videoVisualizationActivityClass()
                    Intent(this@IncorrectReviewsActivity, targetActivity).apply {
                        putExtra("record", record)
                        putExtra(Extras.QUESTION, question)
                        startActivity(this)
                    }
                }

                MediaType.CLASSIFICATION_VIDEO -> {
                    supportFragmentManager.showDialogFragment(
                        ClassificationVideoFragment(record, appFlavor),
                        "ClassificationVideoFragment"
                    )
                }
            }
        }

        override fun onLongPressed(position: Int, record: Record) {}

        override fun updateRecordsCount(selectedCount: Int, totalCount: Int) {}

        override fun starRecord(record: Record, position: Int, status: Boolean) {}

        override fun resetRecords() {}
    }

    private fun isAdapterInitialized() =
        this@IncorrectReviewsActivity::reviewRecordsAdapter.isInitialized
}