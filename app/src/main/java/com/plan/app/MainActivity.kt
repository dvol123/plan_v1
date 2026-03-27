package com.plan.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                    // Error boundary wrapper with recovery
                    var hasError by remember { mutableStateOf(false) }
                    var errorCount by remember { mutableStateOf(0) }
                    
                    if (hasError && errorCount > 3) {
                        // Too many errors - show restart button
                        ErrorScreen(onRestart = {
                            hasError = false
                            errorCount = 0
                        })
                    } else if (hasError) {
                        // Show loading and auto-retry
                        LaunchedEffect(Unit) {
                            hasError = false
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Main content with error handling
                        AppNavigation(
                            onThemeChanged = { newThemeMode ->
                                themeMode = newThemeMode
                            },
                            onError = { error ->
                                Log.e(TAG, "Navigation error", error)
                                hasError = true
                                errorCount++
                            }
                        )
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

@Composable
private fun ErrorScreen(onRestart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Произошла ошибка",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Пожалуйста, перезапустите приложение",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRestart) {
            Text("Перезапустить")
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}
