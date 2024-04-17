package com.nayan.nayancamv2.util

import android.content.Context
import android.media.MediaPlayer
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlin.math.ln

class CommonUtils {
    companion object {
        fun playSound(resourceId: Int, context: Context, soundVolume: Int) {
            if (resourceId == 0) {
                Timber.w("Invalid resource ID for playing sound.")
                return
            }

            var mediaPlayer: MediaPlayer? = null
            try {
                val maxVolume = 10.0
                val volume = (1 - ln(maxVolume - soundVolume.toDouble()) / ln(maxVolume)).toFloat()
                mediaPlayer = MediaPlayer.create(context, resourceId).apply {
                    setVolume(volume, volume)
                    setOnCompletionListener { mp ->
                        Timber.d("Playback completed.")
                        mp.release()
                    }
                    start()
                }
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                Timber.e(e, "Error playing sound.")
            } finally {
                mediaPlayer?.setOnErrorListener { mp, what, extra ->
                    Timber.e("MediaPlayer error: what = $what, extra = $extra")
                    mp.release()
                    true
                }
            }
        }
        /**
         * Read and parse to Long first line from file
         */
        fun File.readOneLineOrNull(): Double? {
            val text: String?
            var value: Double? = null
            try {
                if (this.exists()) {
                    val fs = FileInputStream(this)
                    val sr = InputStreamReader(fs)
                    val br = BufferedReader(sr)
                    text = br.readLine()
                    br.close()
                    sr.close()
                    fs.close()

                    value = text.toDouble()
                }

            } catch (ex: Exception) {
                Firebase.crashlytics.recordException(ex)
                return null
            }

            return value
        }
    }
}