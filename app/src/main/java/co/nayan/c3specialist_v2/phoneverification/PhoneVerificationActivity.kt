package co.nayan.c3specialist_v2.phoneverification

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.databinding.ActivityPhoneVerificationBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.postDelayed
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhoneVerificationActivity : BaseActivity() {

    //c809ko78mFV
    //YDJ9FDE8Ern -> 8 Dec 2022
    private val viewModel: PhoneVerificationViewModel by viewModels()
    private val binding by viewBinding(ActivityPhoneVerificationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)

        initData()
        binding.tvLogout.setOnClickListener {
            logoutUser(errorMessage = null)
        }
    }

    private fun initData() = lifecycleScope.launch {
        postDelayed(1000) { viewModel.requestHint?.invoke() }
        replaceFragment(PhoneVerificationFragment())
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainerVerification, fragment)
        }
    }

    override fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}