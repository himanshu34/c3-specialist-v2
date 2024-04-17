package co.nayan.c3specialist_v2.introscreen

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.UserRepository
import co.nayan.c3specialist_v2.dashboard.DashboardActivity
import co.nayan.c3specialist_v2.databinding.ActivityIntroScreenBinding
import co.nayan.c3specialist_v2.utils.ZoomOutTransformation
import co.nayan.c3v2.core.models.c3_module.ScreenItem
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3v2.login.viewBinding
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IntroScreenActivity : AppCompatActivity() {

    @Inject
    lateinit var userRepository: UserRepository
    private val binding: ActivityIntroScreenBinding by viewBinding(ActivityIntroScreenBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        setupIntroScreen()
    }

    private fun setupIntroScreen() {
        binding.screenViewpager.apply {
            setPageTransformer(true, ZoomOutTransformation())
            adapter = IntroScreenViewPagerAdapter(screenItems)
            binding.tabIndicator.setupWithViewPager(this)
        }

        binding.nextBtn.setOnClickListener {
            var position = binding.screenViewpager.currentItem
            if (position < screenItems.size) {
                position++
                binding.screenViewpager.setCurrentItem(position, true)
            }
            if (position == screenItems.size - 1) loadLastScreen()
        }

        binding.tabIndicator.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == screenItems.size - 1) loadLastScreen()
            }
        })

        binding.getStartedBtn.setOnClickListener {
            userRepository.setOnBoardingDone()
            moveToDashboard()
        }

        binding.skipTv.setOnClickListener {
            binding.screenViewpager.currentItem = screenItems.size
        }
    }

    private fun loadLastScreen() {
        binding.nextBtn.invisible()
        binding.getStartedBtn.visible()
        if (binding.tabIndicator.isVisible) {
            val nextButtonAnimation = AnimationUtils.loadAnimation(this, R.anim.next_btn_animation)
            binding.getStartedBtn.animation = nextButtonAnimation
        }
        binding.skipTv.invisible()
        binding.tabIndicator.invisible()
    }

    private fun moveToDashboard() {
        startActivity(Intent(this@IntroScreenActivity, DashboardActivity::class.java))
        finish()
    }

    private val screenItems = listOf(
        ScreenItem(
            R.drawable.intro_slide_1,
            "Let's Get Started",
            "Earn Money From Home\nCashout Upto ₹500 Per Day"
        ),
        ScreenItem(
            R.drawable.intro_slide_2,
            "Let's Get Started",
            "Simple Question\nSimple Yes/No Answer"
        ),
        ScreenItem(
            R.drawable.intro_slide_3,
            "Let's Get Started",
            "Correct Answer Positive Points\nWrong Answer Negative Points"
        )
    )
}