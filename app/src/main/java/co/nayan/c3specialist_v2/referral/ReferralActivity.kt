package co.nayan.c3specialist_v2.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.databinding.ActivityReferralBinding
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.showToast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReferralActivity : BaseActivity() {

    private val binding: ActivityReferralBinding by viewBinding(ActivityReferralBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.appToolbar)
        title = getString(R.string.referral)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.appToolbar.setNavigationOnClickListener { finish() }
        binding.tvRefCode.text = userRepository.getUserInfo()?.referalCode

        binding.tvCopyCode.setOnClickListener { copyReferralCode() }
        binding.inviteTxt.setOnClickListener { shareReferralCode() }
    }

    private fun copyReferralCode() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Referral Link", binding.tvRefCode.text.toString())
        clipboard.setPrimaryClip(clip)
        postDelayed(200) { showMessage(getString(R.string.referral_copied_successfully)) }
    }

    private fun shareReferralCode() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Referral Code")
        shareIntent.putExtra(Intent.EXTRA_TEXT, getReferralText())

        val chooserIntent = Intent.createChooser(shareIntent, "Share Referral Code")
        if (shareIntent.resolveActivity(packageManager) != null) startActivity(chooserIntent)
    }

    private fun getReferralText(): String {
        var refLink = getString(R.string.referral_link).replace("%PACKAGE_NAME%", packageName)
        refLink = "$refLink${binding.tvRefCode.text}"
        return getString(R.string.referral_text).replace("%APP_LINK%", refLink)
    }

    override fun showMessage(message: String) {
        showToast(message)
    }
}