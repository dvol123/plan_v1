package com.plan.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.plan.app.presentation.navigation.AppNavigation
import com.plan.app.presentation.theme.PlanTheme
import com.plan.app.presentation.ui.components.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * Main Activity for the Plan application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun attachBaseContext(newBase: Context) {
        // Apply saved locale before activity is created - Russian by default
        val prefs = newBase.getSharedPreferences("plan_app_prefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("selected_language", "ru") ?: "ru"
        
        val locale = when (languageCode) {
            "ru" -> Locale("ru")
            "zh" -> Locale("zh")
            else -> Locale.ENGLISH
        }
        
        val context = applyLocaleToContext(newBase, locale)
        super.attachBaseContext(context)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set up uncaught exception handler to prevent white screen
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            // Don't crash - just log
        }
        
        setContent {
            // Observe theme changes
            var themeMode by remember { mutableIntStateOf(AppPreferences.getTheme(this@MainActivity)) }
            
            val darkTheme = when (themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> isSystemInDarkTheme() // System default
            }
            
            PlanTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Error boundary wrapper
                    var hasError by remember { mutableStateOf(false) }
                    
                    if (hasError) {
                        // Fallback UI on error
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "An error occurred. Please restart the app.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        try {
                            AppNavigation(
                                onThemeChanged = { newThemeMode ->
                                    themeMode = newThemeMode
                                },
                                onError = { error ->
                                    Log.e(TAG, "Navigation error", error)
                                    hasError = true
                                }
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in AppNavigation", e)
                            hasError = true
                        }
                    }
                }
            }
        }
    }
    
    private fun applyLocaleToContext(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        
        val resources = context.resources
        val config = android.content.res.Configuration(resources.configuration)
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val localeList = android.os.LocaleList(locale)
            android.os.LocaleList.setDefault(localeList)
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
