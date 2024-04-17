package co.nayan.c3specialist_v2.videogallery

import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.LearningVideosCategory.CURRENT_ROLE
import co.nayan.c3specialist_v2.databinding.ActivityVideoGalleryBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.config.Role.SPECIALIST
import co.nayan.c3v2.core.utils.setupActionBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LearningVideosPlaylistActivity : BaseActivity() {

    private val binding: ActivityVideoGalleryBinding by viewBinding(
        ActivityVideoGalleryBinding::inflate
    )
    private val viewModel: VideoGalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        viewModel.currentSelectedRole = intent.getStringExtra(CURRENT_ROLE) ?: SPECIALIST
        setupActionBar(binding.actionBar.appToolbar)
        title = getString(R.string.video_gallery)
        setupViews()
        setupClicks()
    }

    private fun setupViews() {
        binding.tabLayout.getTabAt(0)?.select()
        replaceFragment(
            LearningVideosFragment.newInstance(
                binding.tabLayout.selectedTabPosition
            )
        )
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment).commit()
    }

    private fun setupClicks() {
        binding.tabLayout.addOnTabSelectedListener(tabSelectedListener)
    }

    private val tabSelectedListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabReselected(tab: TabLayout.Tab?) {}
        override fun onTabUnselected(tab: TabLayout.Tab?) {}
        override fun onTabSelected(tab: TabLayout.Tab?) {
            replaceFragment(
                LearningVideosFragment.newInstance(
                    binding.tabLayout.selectedTabPosition
                )
            )
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}