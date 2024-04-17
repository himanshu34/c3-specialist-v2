package co.nayan.c3specialist_v2.storage

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LearningVideoSharedStorage @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson
) {
    private val sharedPrefs by lazy {
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val listType = object : TypeToken<MutableList<String>>() {}.type
    private val pairListType = object : TypeToken<MutableList<Pair<String, String>>>() {}.type

    fun learningVideosCompletedFor(): MutableList<Pair<String, String>> {
        val stringValue = sharedPrefs.getString(LEARNING_VIDEOS, "[]")
        return gson.fromJson(stringValue, pairListType)
    }

    fun saveLearningVideoCompletedFor(
        unSynced: MutableList<Pair<String, String>>,
        userId: String,
        applicationModeName: String
    ) {
        unSynced.add(Pair(userId, applicationModeName))
        sharedPrefs.edit { putString(LEARNING_VIDEOS, gson.toJson(unSynced)) }
    }

    fun appLearningVideoCompletedFor(): MutableList<String> {
        val stringValue = sharedPrefs.getString(APPLICATION_LEARNING_VIDEO, "[]")
        return gson.fromJson(stringValue, listType)
    }

    fun saveAppLearningVideoCompletedFor(unSynced: MutableList<String>, userId: String) {
        unSynced.add(userId)
        sharedPrefs.edit { putString(APPLICATION_LEARNING_VIDEO, gson.toJson(unSynced)) }
    }

    fun driverLearningVideoCompletedFor(): MutableList<String> {
        val stringValue = sharedPrefs.getString(DRIVER_LEARNING_VIDEO, "[]")
        return gson.fromJson(stringValue, listType)
    }

    fun saveDriverLearningVideoCompletedFor(unSynced: MutableList<String>, userId: String) {
        unSynced.add(userId)
        sharedPrefs.edit { putString(DRIVER_LEARNING_VIDEO, gson.toJson(unSynced)) }
    }

    fun isLearningVideosEnabled() = sharedPrefs.getBoolean(SHOULD_SHOW_LEARNING_VIDEOS, true)

    fun updateLearningVideosStatus(status: Boolean) {
        sharedPrefs.edit { putBoolean(SHOULD_SHOW_LEARNING_VIDEOS, status) }
    }

    fun clearPreferences() {
        sharedPrefs.edit { clear() }
    }

    companion object {
        const val SHARED_PREFS_NAME = "LearningVideosPrefs"
        const val LEARNING_VIDEOS = "LearningVideos"
        const val APPLICATION_LEARNING_VIDEO = "ApplicationLearningVideo"
        const val DRIVER_LEARNING_VIDEO = "DriverLearningVideo"
        const val SHOULD_SHOW_LEARNING_VIDEOS = "should_show_learning_videos"
    }
}