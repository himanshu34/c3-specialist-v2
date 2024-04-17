package co.nayan.c3specialist_v2.performance.widgets

import android.os.Bundle
import android.view.View
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.databinding.FragmentPerformanceBinding
import co.nayan.c3specialist_v2.performance.models.Performance
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable

class PerformanceFragment : BaseFragment(R.layout.fragment_performance) {

    private var performance: Performance? = null
    var onIncorrectClickListener: OnIncorrectClickListener? = null
    private var showIncorrect: Boolean = false
    private val binding by viewBinding(FragmentPerformanceBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            performance = it.parcelable(PERFORMANCE)
            showIncorrect = it.getBoolean(SHOW_INCORRECT, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        performance?.let {
            setupStats(it)
        }
    }

    private fun setupStats(toSet: Performance) {
        binding.hoursWorkedTxt.text = toSet.workDuration ?: "00:00"

        val pointsStats = toSet.points
        if (pointsStats == null) {
            binding.pointsContainer.gone()
        } else {
            binding.totalPotentialTxt.text = String.format("%.2f", pointsStats.potential ?: 0f)
            binding.completedPotentialTxt.text =
                String.format("%.2f", pointsStats.completedPotential ?: 0f)
            binding.correctPotentialTxt.text =
                String.format("%.2f", pointsStats.correctPotential ?: 0f)
            binding.incorrectPotentialTxt.text =
                String.format("%.2f", pointsStats.incorrectPotential ?: 0f)
        }

        val annotationStats = toSet.annotationStats
        if (annotationStats == null) {
            binding.annotationContainer.gone()
        } else {
            binding.totalAnnotationsTxt.text = (annotationStats.totalCount ?: 0).toString()
            binding.completedAnnotationsTxt.text = (annotationStats.completedCount ?: 0).toString()
            binding.correctAnnotationsTxt.text = (annotationStats.correctCount ?: 0).toString()
            binding.inconclusiveAnnotationsTxt.text =
                (annotationStats.inconclusiveCount ?: 0).toString()
            binding.incorrectAnnotationsTxt.text = (annotationStats.incorrectCount ?: 0).toString()
            binding.annotationsAccuracyTxt.text = annotationStats.accuracy()
            if (showIncorrect) {
                binding.incorrectAnnotationsContainer.setBackgroundResource(R.drawable.bg_incorrect_count)
                binding.incorrectAnnotationsContainer.setOnClickListener {
                    onIncorrectClickListener?.onIncorrectAnnotationClicked()
                }
            }
        }

        val judgmentStats = toSet.judgmentStats
        if (judgmentStats == null) {
            binding.validationContainer.gone()
        } else {
            binding.totalJudgmentsTxt.text = (judgmentStats.totalCount ?: 0).toString()
            binding.completedJudgmentsTxt.text = (judgmentStats.completedCount ?: 0).toString()
            binding.correctJudgmentsTxt.text = (judgmentStats.correctCount ?: 0).toString()
            binding.inconclusiveJudgmentsTxt.text =
                (judgmentStats.inconclusiveCount ?: 0).toString()
            binding.incorrectJudgmentsTxt.text = (judgmentStats.incorrectCount ?: 0).toString()
            binding.judgmentsAccuracyTxt.text = judgmentStats.accuracy()
            if (showIncorrect) {
                binding.incorrectJudgmentsContainer.setBackgroundResource(R.drawable.bg_incorrect_count)
                binding.incorrectJudgmentsContainer.setOnClickListener {
                    onIncorrectClickListener?.onIncorrectJudgmentClicked()
                }
            }
        }

        val reviewStats = toSet.reviewStats
        if (reviewStats == null) {
            binding.reviewContainer.gone()
        } else {
            binding.totalReviewsTxt.text = (reviewStats.totalCount ?: 0).toString()
            binding.completedReviewsTxt.text = (reviewStats.completedCount ?: 0).toString()
            binding.correctReviewsTxt.text = (reviewStats.correctCount ?: 0).toString()
            binding.inconclusiveReviewsTxt.text = (reviewStats.inconclusiveCount ?: 0).toString()
            binding.incorrectReviewsTxt.text = (reviewStats.incorrectCount ?: 0).toString()
            binding.reviewsAccuracyTxt.text = reviewStats.accuracy()
            if (showIncorrect) {
                binding.incorrectReviewsContainer.setBackgroundResource(R.drawable.bg_incorrect_count)
                binding.incorrectReviewsContainer.setOnClickListener {
                    onIncorrectClickListener?.onIncorrectReviewClicked()
                }
            }
        }
    }

    companion object {
        private const val PERFORMANCE = "performance"
        private const val SHOW_INCORRECT = "show_incorrect"

        @JvmStatic
        fun newInstance(
            performance: Performance,
            showIncorrect: Boolean = false,
            callback: OnIncorrectClickListener? = null
        ) = PerformanceFragment().apply {
            onIncorrectClickListener = callback
            arguments = Bundle().apply {
                putBoolean(SHOW_INCORRECT, showIncorrect)
                putParcelable(PERFORMANCE, performance)
            }
        }
    }
}

interface OnIncorrectClickListener {
    fun onIncorrectAnnotationClicked()
    fun onIncorrectJudgmentClicked()
    fun onIncorrectReviewClicked()
}