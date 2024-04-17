package co.nayan.c3v2.core.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.*

object LocaleHelper {

    private const val SHARED_PREFS_NAME = "C3SpecialistPrefs"
    private const val APP_LANGUAGE = "AppLanguage"

    fun wrapContext(context: Context): Context {
        val savedLocale = createLocaleFromSavedLanguage(context) ?: return context
        Locale.setDefault(savedLocale)

        val newConfig = Configuration()
        newConfig.setLocale(savedLocale)

        return context.createConfigurationContext(newConfig)
    }

    private fun getAppLanguage(context: Context): String? {
        return getSharedPrefs(context).getString(APP_LANGUAGE, "en")
    }

    private fun createLocaleFromSavedLanguage(context: Context): Locale? {
        val language = getAppLanguage(context)
        return if (language.isNullOrEmpty()) null
        else Locale(language)
    }

    private fun getSharedPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }
}