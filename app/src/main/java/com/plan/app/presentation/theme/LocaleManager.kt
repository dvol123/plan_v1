package com.plan.app.presentation.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.plan.app.presentation.ui.components.AppLanguage
import com.plan.app.presentation.ui.components.AppPreferences
import java.util.Locale

/**
 * CompositionLocal for the current app locale.
 */
val LocalAppLocale = compositionLocalOf { Locale.ENGLISH }

/**
 * CompositionLocal for locale version - used to trigger recomposition
 */
val LocalLocaleVersion = compositionLocalOf { 0 }

/**
 * Manager for handling locale changes without activity restart.
 */
object LocaleManager {
    private val _currentLocale: MutableState<Locale> = mutableStateOf(Locale.ENGLISH)
    private val _localeVersion: MutableState<Int> = mutableStateOf(0)
    
    val currentLocaleState: State<Locale>
        get() = _currentLocale
    
    val localeVersionState: State<Int>
        get() = _localeVersion
    
    val currentLocale: Locale
        get() = _currentLocale.value
    
    val localeVersion: Int
        get() = _localeVersion.value

    fun setLocale(locale: Locale) {
        _currentLocale.value = locale
        Locale.setDefault(locale)
    }

    fun setLocaleFromCode(languageCode: String) {
        val locale = when (languageCode) {
            "ru" -> Locale("ru")
            "zh" -> Locale("zh")
            else -> Locale.ENGLISH
        }
        setLocale(locale)
    }
    
    /**
     * Increment locale version to trigger recomposition
     */
    fun notifyLocaleChanged() {
        _localeVersion.value++
    }

    /**
     * Apply locale to a context and return the updated context.
     */
    fun updateContextLocale(context: Context, locale: Locale): Context {
        val resources = context.resources
        val config = Configuration(resources.configuration)
        
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
}

/**
 * Composable that provides localized context throughout the app.
 * This replaces LocalContext with a context that has the correct locale applied,
 * so all stringResource() calls will use the selected language.
 */
@Composable
fun LocaleProvider(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val savedLanguageCode = AppPreferences.getLanguage(context)
    
    // Observe locale changes from LocaleManager
    val currentLocale = LocaleManager.currentLocaleState.value
    val localeVersion = LocaleManager.localeVersionState.value
    
    // Initialize locale on first composition
    LaunchedEffect(savedLanguageCode) {
        LocaleManager.setLocaleFromCode(savedLanguageCode)
    }

    // Create localized context - reactive to locale changes
    val localizedContext = remember(currentLocale, localeVersion) {
        LocaleManager.updateContextLocale(context, currentLocale)
    }
    
    // Create localized configuration
    val localizedConfig = remember(localizedContext) {
        localizedContext.resources.configuration
    }

    CompositionLocalProvider(
        LocalAppLocale provides currentLocale,
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig,
        LocalLocaleVersion provides localeVersion
    ) {
        content()
    }
}

/**
 * Update locale and save preference.
 */
fun updateAppLocale(context: Context, language: AppLanguage) {
    val locale = when (language) {
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.RUSSIAN -> Locale("ru")
        AppLanguage.CHINESE -> Locale("zh")
    }
    
    AppPreferences.saveLanguage(context, language.code)
    LocaleManager.setLocale(locale)
    LocaleManager.notifyLocaleChanged()
}
