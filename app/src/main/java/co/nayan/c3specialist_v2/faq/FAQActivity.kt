package co.nayan.c3specialist_v2.faq

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nayan.c3specialist_v2.*
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityFaqactivityBinding
import co.nayan.c3specialist_v2.faq.faq_data_details.FaqDataDetailsActivity
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.config.MediaType
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.models.*
import co.nayan.c3v2.core.models.c3_module.FaqData
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.setupActionBar
import co.nayan.c3v2.core.utils.visible
import co.nayan.canvas.CanvasActivity
import co.nayan.review.models.ReviewIntentInputData
import co.nayan.review.recordsgallery.ReviewActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FAQActivity : BaseActivity() {

    @Inject
    lateinit var errorUtils: ErrorUtils
    private val faqViewModel: FaqViewModel by viewModels()
    private var currentSpanCount = 3
    private val binding: ActivityFaqactivityBinding by viewBinding(ActivityFaqactivityBinding::inflate)

    private val onFaqItemClickListener = object : OnFaqItemClickListener {
        override fun onClicked(data: FaqData) {
            Intent(this@FAQActivity, FaqDataDetailsActivity::class.java).apply {
                putExtra(Extras.WF_STEP_ID, faqViewModel.workAssignment?.wfStep?.id)
                putExtra(Extras.SELECTED_FAQ_DATA_ID, data.id)
                startActivity(this)
            }
        }
    }

    private val faqGalleryAdapter = FaqGalleryAdapter(onFaqItemClickListener)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupActionBar(binding.actionBar.appToolbar, true)
        title = getString(R.string.faq_sets)

        faqViewModel.state.observe(this, stateObserver)

        setupExtras()
        setupActions()
        faqViewModel.fetchTrainingData()
    }

    private fun setupActions() {
        binding.confirmationButton.setOnClickListener {
            faqViewModel.submitConfirmation()
        }
        binding.confirmationCheckbox.setOnCheckedChangeListener(onCheckChangedListener)

        binding.galleryItemView.addOnScrollListener(scrollChangeListener)
    }

    private fun setupExtras() {
        faqViewModel.workAssignment = intent.parcelable(Extras.WORK_ASSIGNMENT)
        setQuestion(faqViewModel.workAssignment?.wfStep?.question)
    }

    private val scrollChangeListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = binding.galleryItemView.layoutManager as GridLayoutManager
            val visibleItemCount = layoutManager.findLastVisibleItemPosition() + 1
            if (visibleItemCount == layoutManager.itemCount) {
                binding.confirmationContainer.visible()
            } else {
                binding.confirmationContainer.gone()
            }
        }
    }

    private val onCheckChangedListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            binding.confirmationButton.isEnabled = isChecked
        }

    private fun setUpAdapter() {
        binding.galleryItemView.layoutManager = GridLayoutManager(this, currentSpanCount)
        binding.galleryItemView.adapter = faqGalleryAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState, ProgressState -> {
                binding.shimmerViewContainer.visible()
                binding.shimmerViewContainer.startShimmer()
                binding.faqContainer.gone()
            }

            is FaqDataSuccessState -> {
                if (!it.displayDataItem.isNullOrEmpty()) {
                    binding.shimmerViewContainer.gone()
                    binding.shimmerViewContainer.stopShimmer()
                    binding.faqContainer.visible()
                    setUpCategoriesHeaders()
                    setUpAdapter()
                    faqGalleryAdapter.update(it.displayDataItem)
                    faqGalleryAdapter.notifyDataSetChanged()
                } else {
                    showMessage(getString(R.string.there_is_no_training_data))
                }
            }

            FaqDataUnSuccessState -> {
                showMessage(getString(R.string.there_is_no_training_data))
            }

            is FaqViewModel.SubmitConfirmationSuccessState -> {
                val errorString = it.response.errors
                if (errorString.isNullOrEmpty()) {
                    it.response.message?.let { message ->
                        showMessage(message)
                    }
                    if (faqViewModel.workAssignment?.sandboxRequired == true) {
                        val intent = Intent().apply {
                            putExtra(Extras.WORK_ASSIGNMENT, faqViewModel.workAssignment)
                        }
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    } else {
                        moveToNextScreen(faqViewModel.workAssignment)
                    }
                } else {
                    it.response.message?.let { message ->
                        showMessage("$message : $errorString")
                    }
                    binding.shimmerViewContainer.gone()
                    binding.shimmerViewContainer.stopShimmer()
                    binding.faqContainer.visible()
                }
            }

            is ErrorState -> {
                binding.shimmerViewContainer.gone()
                binding.shimmerViewContainer.stopShimmer()
                binding.faqContainer.visible()
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun moveToNextScreen(workAssignment: WorkAssignment?) {
        val role = intent.getStringExtra(Extras.USER_ROLE)
        if (role == Role.SPECIALIST ||
            (workAssignment?.workType != WorkType.REVIEW ||
                    workAssignment.wfStep?.mediaType == MediaType.VIDEO)
        ) moveToCanvasScreen(workAssignment)
        else moveToReviewScreen(workAssignment)
    }

    private fun moveToCanvasScreen(workAssignment: WorkAssignment?) {
        Intent(this@FAQActivity, CanvasActivity::class.java).apply {
            putExtra(CanvasActivity.WORK_ASSIGNMENT, workAssignment)
            putExtra(CanvasActivity.APP_FLAVOR, BuildConfig.FLAVOR)
            startActivity(this)
        }
        finish()
    }

    private val getResultContent = registerForActivityResult(ReviewActivity.ResultCallback()) {
        //if manager account is locked isAccountLocked will be true.
    }

    private fun moveToReviewScreen(workAssignment: WorkAssignment) {
        getResultContent.launch(ReviewIntentInputData(workAssignment, BuildConfig.FLAVOR))
        finish()
    }

    private fun setQuestion(question: String?) {
        question?.let {
            binding.faqQuestionTxt.text = question
        }
    }

    private fun setUpCategoriesHeaders() {
        faqViewModel.correctDataSize?.let {
            if (it > 0) {
                binding.dosHeader.visible()
            } else {
                currentSpanCount -= 1
                binding.dosHeader.gone()
            }
        }
        faqViewModel.incorrectDataSize?.let {
            if (it > 0) {
                binding.dontsHeader.visible()
            } else {
                currentSpanCount -= 1
                binding.dontsHeader.gone()
            }
        }
        faqViewModel.junkDataSize?.let {
            if (it > 0) {
                binding.junkHeader.visible()
            } else {
                currentSpanCount -= 1
                binding.junkHeader.gone()
            }
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.faqContainer, message, Snackbar.LENGTH_LONG).show()
    }
}