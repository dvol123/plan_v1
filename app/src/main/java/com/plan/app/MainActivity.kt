package com.plan.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
                    AppNavigation(
                        onThemeChanged = { newThemeMode ->
                            themeMode = newThemeMode
                        }
                    )
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
