package co.nayan.tutorial

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import co.nayan.c3v2.core.models.Video
import co.nayan.c3v2.core.utils.gone
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.visible
import co.nayan.tutorial.config.LearningVideosExtras.IS_VIDEO_COMPLETED
import co.nayan.tutorial.config.LearningVideosExtras.LEARNING_VIDEO
import co.nayan.tutorial.databinding.ActivityLearningVideoPlayerBinding
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer

class LearningVideoPlayerActivity : AppCompatActivity() {

    private val binding: ActivityLearningVideoPlayerBinding by viewBinding(ActivityLearningVideoPlayerBinding::inflate)
    private var isVideoCompleted = false
    private var simpleExoPlayer: SimpleExoPlayer? = null
    private var videoDetails: Video? = null

    override fun onPause() {
        super.onPause()
        simpleExoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        simpleExoPlayer?.pause()
        simpleExoPlayer?.stop()
        simpleExoPlayer?.release()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setFullScreen()
        videoDetails = intent.parcelable(LEARNING_VIDEO)
        videoDetails?.let { startVideoInExoPlayer(it) } ?: run { finish() }

        binding.videoCompletedBtn.setOnClickListener {
            val intent = Intent()
            intent.putExtra(IS_VIDEO_COMPLETED, isVideoCompleted)
            setResult(RESULT_OK, intent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent()
                intent.putExtra(IS_VIDEO_COMPLETED, isVideoCompleted)
                setResult(RESULT_OK, intent)
                finish()
            }
        })
    }

    private fun startVideoInExoPlayer(video: Video) {
        binding.exoPlayerView.visible()
        val videoUri = Uri.parse(video.gcpUrl)
        setUpExoPlayer(videoUri)
    }

    private fun setUpExoPlayer(video: Uri) {
        simpleExoPlayer = SimpleExoPlayer.Builder(this).build().also { exoPlayer ->
            val mediaItem = MediaItem.fromUri(video)
            binding.exoPlayerView.player = exoPlayer
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        }
        simpleExoPlayer?.addListener(eventListener)
    }

    private val eventListener = object : Player.EventListener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {

                }

                Player.STATE_BUFFERING -> {
                    binding.videoCompletedBtn.gone()
                }

                Player.STATE_READY -> {
                }

                Player.STATE_ENDED -> {
                    binding.videoCompletedBtn.visible()
                    isVideoCompleted = true
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setFullScreen() {
        window?.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }
}