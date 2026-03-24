package com.plan.app.presentation.ui.components

import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plan.app.R
import java.util.Locale

/**
 * Supported languages in the app.
 */
enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    CHINESE("zh", "中文")
}

/**
 * Preferences helper for storing settings.
 */
object AppPreferences {
    private const val PREFS_NAME = "plan_app_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_THEME = "selected_theme"
    
    fun saveLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
    }
    
    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "en") ?: "en"
    }
    
    fun saveTheme(context: Context, themeMode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME, themeMode)
            .apply()
    }
    
    fun getTheme(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, 0) // 0 = System default
    }
}

/**
 * Apply the saved language to the app context.
 */
fun applyLanguage(context: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        val config = context.resources.configuration
        config.setLocales(localeList)
        context.createConfigurationContext(config)
    } else {
        val config = context.resources.configuration
        @Suppress("DEPRECATION")
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.createConfigurationContext(config)
    }
}

/**
 * Dialog for application settings.
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Get current saved language
    val savedLanguageCode = remember { AppPreferences.getLanguage(context) }
    val savedTheme = remember { AppPreferences.getTheme(context) }
    
    var selectedLanguage by remember { mutableStateOf(AppLanguage.entries.find { it.code == savedLanguageCode } ?: AppLanguage.ENGLISH) }
    var selectedTheme by remember { mutableStateOf(savedTheme) }
    var showRestartMessage by remember { mutableStateOf(false) }
    
    val languages = AppLanguage.entries
    
    val themes = listOf(
        0 to stringResource(R.string.theme_system),
        1 to stringResource(R.string.theme_light),
        2 to stringResource(R.string.theme_dark)
    )
    
    // Show restart message when language is changed
    if (showRestartMessage) {
        AlertDialog(
            onDismissRequest = { 
                showRestartMessage = false
                onDismiss()
            },
            title = { Text(stringResource(R.string.settings)) },
            text = { Text(stringResource(R.string.restart_required)) },
            confirmButton = {
                TextButton(onClick = { 
                    showRestartMessage = false
                    onDismiss()
                    // The activity will be recreated when user navigates back
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings)) },
            text = {
                Column {
                    // Language selection
                    Text(
                        text = stringResource(R.string.language),
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    languages.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedLanguage == language,
                                onClick = { 
                                    if (selectedLanguage != language) {
                                        selectedLanguage = language
                                        AppPreferences.saveLanguage(context, language.code)
                                        showRestartMessage = true
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Theme selection
                    Text(
                        text = stringResource(R.string.theme),
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    themes.forEach { (themeMode, themeName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedTheme == themeMode,
                                onClick = { 
                                    selectedTheme = themeMode
                                    AppPreferences.saveTheme(context, themeMode)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = themeName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
