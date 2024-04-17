package co.nayan.c3specialist_v2.illustration

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityIllustrationBinding
import co.nayan.c3specialist_v2.faq.FaqCallbackInput
import co.nayan.c3specialist_v2.faq.FaqResultCallback
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.models.WorkAssignment
import co.nayan.c3v2.core.utils.enabled
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.parcelable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IllustrationActivity : BaseActivity() {

    private val binding: ActivityIllustrationBinding by viewBinding(ActivityIllustrationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        workAssignment = intent.parcelable(Extras.WORK_ASSIGNMENT)
        val imageUrl = workAssignment?.illustration?.link
        setImage(imageUrl)

        binding.illustrationPv.setOnClickListener {
            setImage(imageUrl)
        }

        binding.confirmationButton.setOnClickListener {
            moveToNextScreen()
        }

        setupProgressbar()
    }

    private var workAssignment: WorkAssignment? = null

    private fun moveToNextScreen() {
        faqResultCallback.launch(
            FaqCallbackInput(
                workAssignment = workAssignment,
                userRole = intent.getStringExtra(Extras.USER_ROLE),
                isIllustration = false
            )
        )
    }

    private fun setupProgressbar() {
        val mCountDownTimer: CountDownTimer
        var i = 0
        binding.progressBar.progress = i
        mCountDownTimer = object : CountDownTimer(5000, 10) {
            override fun onTick(millisUntilFinished: Long) {
                i++
                binding.progressBar.progress = i / 5
            }

            override fun onFinish() {
                binding.progressBar.invisible()
                binding.confirmationButton.enabled()
            }
        }
        mCountDownTimer.start()
    }

    private fun setImage(imageUrl: String?) {
        val options = RequestOptions().placeholder(R.drawable.progress_animation)
        binding.illustrationPv.apply {
            Glide.with(context)
                .load(imageUrl)
                .listener(requestListener)
                .apply(options)
                .into(this)
        }
    }

    private val requestListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch {
                binding.illustrationPv.apply {
                    scaleType = ImageView.ScaleType.CENTER
                    setImageDrawable(ContextCompat.getDrawable(context, co.nayan.canvas.R.drawable.ic_reload))
                }
            }
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            lifecycleScope.launch {
                binding.illustrationPv.apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(resource)
                }
            }
            return false
        }
    }

    private val faqResultCallback =
        registerForActivityResult(FaqResultCallback()) { workAssignment ->
            if (workAssignment != null) {
                if (workAssignment.sandboxRequired == true) {
                    val intent = Intent().apply {
                        putExtra(Extras.WORK_ASSIGNMENT, workAssignment)
                    }
                    setResult(Activity.RESULT_OK, intent)
                }
            }
            finish()
        }


    override fun showMessage(message: String) {}
}