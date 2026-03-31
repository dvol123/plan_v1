package com.plan.app.presentation.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Manager for handling locale throughout the app.
 * Locale is applied through Activity.attachBaseContext() and requires
 * an activity restart to take effect after changes.
 */
object LocaleManager {
    /**
     * Apply locale to context resources. Call this from Activity's attachBaseContext.
     */
    fun applyLocaleToContext(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "ru" -> Locale("ru")
            "zh" -> Locale("zh")
            else -> Locale.ENGLISH
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.createConfigurationContext(config)
        }
    }
    
    /**
     * Get the locale for a given language code.
     */
    fun getLocaleForCode(languageCode: String): Locale {
        return when (languageCode) {
            "ru" -> Locale("ru")
            "zh" -> Locale("zh")
            else -> Locale.ENGLISH
        }
    }
}
