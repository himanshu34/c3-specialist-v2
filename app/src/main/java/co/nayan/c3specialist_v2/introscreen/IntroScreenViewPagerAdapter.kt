package co.nayan.c3specialist_v2.introscreen

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import co.nayan.c3specialist_v2.databinding.LayoutScreenBinding
import co.nayan.c3v2.core.models.c3_module.ScreenItem

class IntroScreenViewPagerAdapter(private val screenItems: List<ScreenItem>) : PagerAdapter() {

    override fun instantiateItem(parent: ViewGroup, position: Int): Any {
        val binding =
            LayoutScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                .apply {
                    screenItem = screenItems[position]
                }
        parent.addView(binding.root)
        return binding.root
    }

    override fun getCount() = screenItems.size

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }
}