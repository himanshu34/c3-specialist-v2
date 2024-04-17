package co.nayan.c3specialist_v2.faq.faq_data_details

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityFaqDataDetailsActivityBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.api.client_error.ErrorUtils
import co.nayan.c3v2.core.models.ActivityState
import co.nayan.c3v2.core.models.ErrorState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.c3_module.FaqData
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.visible
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FaqDataDetailsActivity : BaseActivity() {

    private val faqDataDetailsViewModel: FaqDataDetailsViewModel by viewModels()
    private val binding: ActivityFaqDataDetailsActivityBinding by viewBinding(
        ActivityFaqDataDetailsActivityBinding::inflate
    )

    @Inject
    lateinit var errorUtils: ErrorUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        faqDataDetailsViewModel.state.observe(this, stateObserver)

        val wfStepId = intent.getIntExtra(Extras.WF_STEP_ID, 0)
        fetchTrainingData(wfStepId)
    }

    private fun fetchTrainingData(wfStepId: Int) {
        if (wfStepId != 0) faqDataDetailsViewModel.fetchTrainingData(wfStepId)
        else showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
    }

    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            ProgressState -> {
                binding.progressBar.visible()
                binding.imageViewPager.gone()
            }

            is FaqDataDetailsViewModel.FaqDataSuccessState -> {
                if (!it.faqDataList.isNullOrEmpty()) {
                    binding.progressBar.gone()
                    binding.imageViewPager.visible()
                    setupSlidingAdapter(it.faqDataList)
                } else showMessage(getString(co.nayan.c3v2.core.R.string.something_went_wrong))
            }

            is ErrorState -> {
                showMessage(errorUtils.parseExceptionMessage(it.exception))
            }
        }
    }

    private fun setupSlidingAdapter(faqDataList: List<FaqData>) {
        val selectedDataId = intent.getIntExtra(Extras.SELECTED_FAQ_DATA_ID, 0)
        binding.imageViewPager.adapter =
            FaqImageAdapter(supportFragmentManager, lifecycle, faqDataList)
        binding.imageViewPager.setCurrentItem(
            faqDataList.indexOfFirst { it.id == selectedDataId },
            false
        )
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.progressBar, message, Snackbar.LENGTH_LONG).show()
    }
}
