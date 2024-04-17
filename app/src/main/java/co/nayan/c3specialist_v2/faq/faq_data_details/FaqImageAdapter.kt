package co.nayan.c3specialist_v2.faq.faq_data_details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import co.nayan.c3v2.core.models.c3_module.FaqData

class FaqImageAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val faqDataList: List<FaqData>
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = faqDataList.size

    override fun createFragment(position: Int): Fragment {
        return FaqDataDetailsFragment.newInstance(faqDataList[position])
    }
}