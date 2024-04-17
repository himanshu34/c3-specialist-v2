package co.nayan.c3specialist_v2.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.databinding.ActivityDashboardBinding
import co.nayan.c3specialist_v2.search.SearchFragment
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.utils.selected
import co.nayan.c3v2.core.utils.unSelected
import co.nayan.c3v2.login.LoginActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : BaseActivity() {

    private val binding: ActivityDashboardBinding by viewBinding(ActivityDashboardBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupBottomContainer()
    }

    override fun showMessage(message: String) {

    }

    private fun setupBottomContainer() {
        binding.homeContainer.setOnClickListener {
            Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.VERSION_NAME, BuildConfig.VERSION_NAME)
                putExtra(LoginActivity.VERSION_CODE, BuildConfig.VERSION_CODE)
                startActivity(this)
            }
        }

        binding.profileContainer.setOnClickListener {
            Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.VERSION_NAME, BuildConfig.VERSION_NAME)
                putExtra(LoginActivity.VERSION_CODE, BuildConfig.VERSION_CODE)
                startActivity(this)
            }
        }

        binding.searchContainer.setOnClickListener {
            it.selected()
            binding.homeContainer.unSelected()
            binding.profileContainer.unSelected()

            replaceFragment(SearchFragment())
        }

        binding.homeContainer.callOnClick()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}