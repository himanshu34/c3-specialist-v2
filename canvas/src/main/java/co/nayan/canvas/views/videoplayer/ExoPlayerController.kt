package co.nayan.canvas.views.videoplayer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File
import kotlin.math.roundToLong

class ExoplayerController(
    private val context: Context,
    private val playerView: ZoomableExoPlayerView,
    private var playWhenReady: Boolean = true
) : LifecycleObserver {

    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var player: SimpleExoPlayer? = null
    private val _exoPlaybackState = MutableLiveData<ExoPlayerPlayBackState>()
    val exoPlaybackState: LiveData<ExoPlayerPlayBackState> = _exoPlaybackState
    private var isMediaPlaybackCompleted = false

    private val eventListener = object : EventListener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) _exoPlaybackState.value = OnMediaPlaybackStart
            else _exoPlaybackState.value = OnMediaPlaybackStopped
        }

        override fun onPlaybackStateChanged(state: Int) {
            super.onPlaybackStateChanged(state)
            when (state) {
                STATE_IDLE -> {

                }
                STATE_BUFFERING -> {
                    _exoPlaybackState.value = OnMediaPlaybackStopped
                }
                STATE_READY -> {
                    _exoPlaybackState.value = OnMediaPlaybackReady
                }
                STATE_ENDED -> {
                    isMediaPlaybackCompleted = true
                }
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            super.onPlayerError(error)
            _exoPlaybackState.value = OnMediaPlaybackError(error)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        createPlayer()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        releasePlayer()
    }

    private fun createPlayer() {
        if (player == null)
            player = SimpleExoPlayer.Builder(context, DefaultRenderersFactory(context))
                .setTrackSelector(DefaultTrackSelector(context))
                .setLoadControl(getLoadControl()).build()
        playerView.player = player
        // When changing track, retain the latest frame instead of showing a black screen
        playerView.setKeepContentOnPlayerReset(true)
    }

    fun reloadPlayer(mediaUrl: String?) {
        releasePlayer()
        createPlayer()
        initializePlayer(mediaUrl)
    }

    fun initializePlayer(videoUrl: String?) {
        isMediaPlaybackCompleted = false
        val mediaSource = buildMediaSource(Uri.parse(videoUrl))
        player?.apply {
            repeatMode = REPEAT_MODE_ALL
            playWhenReady = this.playWhenReady
            seekTo(currentWindow, playbackPosition)
            addListener(eventListener)
            prepare(mediaSource, false, false)
        } ?: run { createPlayer() }
    }

    fun getCurrentTimeStamp(): Long {
        var normalizedTimeStamp = 0L
        player?.let {
            normalizedTimeStamp = (it.currentPosition.toDouble() / 30).roundToLong() * 30
        }
        return normalizedTimeStamp
    }

    fun seekTo(normalizedTimeStamp: Long?) {
        normalizedTimeStamp?.let { player?.seekTo(it) }
    }

    fun isMediaPlaybackCompleted() = isMediaPlaybackCompleted

    private fun releasePlayer() {
        player?.let {
            playWhenReady = it.playWhenReady
            playbackPosition = it.currentPosition
            currentWindow = it.currentWindowIndex
            it.release()
            clearVideoCache()
            player = null
        }
    }

    // Before changing the values consider reading the following article
    // https://blogs.akamai.com/2019/10/enhancing-a-streaming-quality-for-exoplayer---part-2-exoplayers-buffering-strategy-how-to-lower-.html
    private fun getLoadControl(): LoadControl {
        val builder: DefaultLoadControl.Builder =
            DefaultLoadControl.Builder()
        builder.setBufferDurationsMs(
            15000, // DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            50000, //*.DEFAULT_MAX_BUFFER_MS,
            1000, // *.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            1000 // *.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_RE-BUFFER_MS
        )
        return builder.createDefaultLoadControl()
    }

    private fun buildMediaSource(uri: Uri): MediaSource {
        val cacheDataSourceFactory = CacheDataSourceFactory(
            VideoCache.get(context),
            DefaultHttpDataSourceFactory("exoplayer-c3")
        )
        return ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(uri)
    }

    fun startPlayback() {
        player?.let { it.playWhenReady = true }
    }

    fun togglePlayback() {
        player?.let { it.playWhenReady = !it.playWhenReady }
    }

    fun forwardBy(position: Int) {
        player?.let {
            pausePlayback()
            if ((it.contentPosition + position) < it.duration)
                it.seekTo(it.contentPosition + position)
            else it.seekTo(it.duration)
        }
    }

    fun rewindBy(position: Int) {
        player?.let {
            pausePlayback()
            if ((it.contentPosition - position) > 0)
                it.seekTo(it.contentPosition - position)
            else it.seekTo(0)
        }
    }

    private fun pausePlayback() {
        player?.let { it.playWhenReady = false }
    }

    private fun clearVideoCache() {
        val cacheFolder = File(context.cacheDir, VideoCache.videoCacheDir)
        cacheFolder.delete()
    }

    object VideoCache {
        private var downloadCache: SimpleCache? = null
        private const val maxCacheSize: Long = 20 * 1024 * 1024
        const val videoCacheDir: String = "media"

        fun get(context: Context): SimpleCache {
            if (downloadCache == null) {
                val cacheFolder = File(context.cacheDir, videoCacheDir)
                val cacheEvictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
                downloadCache = SimpleCache(cacheFolder, cacheEvictor, ExoDatabaseProvider(context))
            }
            return downloadCache as SimpleCache
        }
    }
}

open class ExoPlayerPlayBackState
object OnMediaPlaybackReady : ExoPlayerPlayBackState()
object OnMediaPlaybackStopped : ExoPlayerPlayBackState()
object OnMediaPlaybackStart : ExoPlayerPlayBackState()
class OnMediaPlaybackError(val error: ExoPlaybackException) : ExoPlayerPlayBackState()