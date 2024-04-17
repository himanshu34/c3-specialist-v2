package co.nayan.c3specialist_v2.faq.faq_data_details

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseFragment
import co.nayan.c3specialist_v2.config.FaqDataCategories
import co.nayan.c3specialist_v2.databinding.FragmentFaqDataDetailsBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.models.c3_module.FaqData
import co.nayan.c3v2.core.utils.parcelable
import com.bumptech.glide.Glide

class FaqDataDetailsFragment : BaseFragment(R.layout.fragment_faq_data_details) {

    private var faqData: FaqData? = null
    private val binding by viewBinding(FragmentFaqDataDetailsBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.parcelable<FaqData>(FAQ_DATA)?.let { data ->
            faqData = data
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        faqData?.image?.let {
            binding.trainingImageView.apply {
                Glide.with(context).load(it).into(this)
            }
        }

        faqData?.category?.let {
            val colorId = when (it) {
                FaqDataCategories.CORRECT -> R.color.green
                FaqDataCategories.INCORRECT -> R.color.red
                else -> co.nayan.canvas.R.color.junk
            }
            binding.categoryTxt.apply {
                setTextColor(ContextCompat.getColor(context, colorId))
                this.text = it
            }
        }
    }

    companion object {
        const val FAQ_DATA = "faq_data"

        fun newInstance(faqData: FaqData) =
            FaqDataDetailsFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(FAQ_DATA, faqData)
                }
            }
    }
}
