package com.nayan.nayancamv2.util

import android.media.AudioManager
import androidx.lifecycle.LifecycleService
import com.nayan.nayancamv2.between

object VolumeChangeManager {

    var previousVolume = HashMap<Int, Int?>()

    fun setUpVolume(audio: AudioManager) {
        previousVolume[AudioManager.STREAM_ALARM] = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        previousVolume[AudioManager.STREAM_DTMF] = audio.getStreamVolume(AudioManager.STREAM_DTMF)
        previousVolume[AudioManager.STREAM_MUSIC] = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        previousVolume[AudioManager.STREAM_NOTIFICATION] = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        previousVolume[AudioManager.STREAM_RING] = audio.getStreamVolume(AudioManager.STREAM_RING)
        previousVolume[AudioManager.STREAM_SYSTEM] = audio.getStreamVolume(AudioManager.STREAM_SYSTEM)
        previousVolume[AudioManager.STREAM_VOICE_CALL] = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
    }

    fun isVolumeChanged(audio: AudioManager): Boolean {
        var isChanged = false

        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != previousVolume[AudioManager.STREAM_ALARM]) {
            val vol = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val min = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audio.getStreamMinVolume(AudioManager.STREAM_ALARM)
            } else 0

            if (vol.between(min, max)) {
                previousVolume[AudioManager.STREAM_ALARM] = vol
            } else {
                val newVol = if (vol == min) vol + 1 else vol - 1
                audio.setStreamVolume(AudioManager.STREAM_ALARM, newVol, AudioManager.ADJUST_SAME)
            }

            isChanged = true
        }

        if (audio.getStreamVolume(AudioManager.STREAM_DTMF) != previousVolume[AudioManager.STREAM_DTMF]) {
            val vol = audio.getStreamVolume(AudioManager.STREAM_DTMF)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_DTMF)
            val min = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audio.getStreamMinVolume(AudioManager.STREAM_DTMF)
            } else 0

            if (vol.between(min, max)) {
                previousVolume[AudioManager.STREAM_DTMF] = vol
            } else {
                val newVol = if (vol == min) vol + 1 else vol - 1
                audio.setStreamVolume(AudioManager.STREAM_DTMF, newVol, AudioManager.ADJUST_SAME)

            }
            isChanged = true
        }

        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != previousVolume[AudioManager.STREAM_MUSIC]) {
            val vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val min = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } else 0

            if (vol.between(min, max)) {
                previousVolume[AudioManager.STREAM_MUSIC] = vol
            } else {
                val newVol = if (vol == min) vol + 1 else vol - 1
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, AudioManager.ADJUST_SAME)
            }
            isChanged = true
        }

        if (audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) != previousVolume[AudioManager.STREAM_NOTIFICATION]) {
            val vol = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            val min = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audio.getStreamMinVolume(AudioManager.STREAM_NOTIFICATION)
            } else 0

            if (vol.between(min, max)) {
                previousVolume[AudioManager.STREAM_NOTIFICATION] = vol
            } else {
                val newVol = if (vol == min) vol + 1 else vol - 1
                audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, newVol, AudioManager.ADJUST_SAME)
            }
            isChanged = true
        }

        if (audio.getStreamVolume(AudioManager.STREAM_RING) != previousVolume[AudioManager.STREAM_RING]) {
            val vol = audio.getStreamVolume(AudioManager.STREAM_RING)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
            val min = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audio.getStreamMinVolume(AudioManager.STREAM_RING)
            } else 0

            if (vol.between(min, max)) {
                previousVolume[AudioManager.STREAM_RING] = vol
            } else {
                val newVol = if (vol == min) vol + 1 else vol - 1
                audio.setStreamVolume(AudioManager.STREAM_RING, newVol, AudioManager.ADJUST_SAME)
            }
            isChanged = true
        }

        if (audio.getStreamVolume(AudioManager.STREAM_SYSTEM) != previousVolume[AudioManager.STREAM_SYSTEM]) {
            val vol = audio.getStreamVolume(AudioManager.STREAM_SYSTEM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)
            val min = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audio.getStreamMinVolume(AudioManager.STREAM_SYSTEM)
            } else 0

            if (vol.between(min, max)) {
                previousVolume[AudioManager.STREAM_SYSTEM] = vol
            } else {
                val newVol = if (vol == min) vol + 1 else vol - 1
                audio.setStreamVolume(AudioManager.STREAM_SYSTEM, newVol, AudioManager.ADJUST_SAME)
            }
            isChanged = true
        }

        if (audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL) != previousVolume[AudioManager.STREAM_VOICE_CALL]) {
            val vol = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val min = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audio.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL)
            } else 0

            if (vol.between(min, max)) {
                previousVolume[AudioManager.STREAM_VOICE_CALL] = vol
            } else {
                val newVol = if (vol == min) vol + 1 else vol - 1
                audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, newVol, AudioManager.ADJUST_SAME)
            }
            isChanged = true
        }

        return isChanged
    }
}