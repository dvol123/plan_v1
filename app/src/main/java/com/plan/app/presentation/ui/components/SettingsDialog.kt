package com.plan.app.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plan.app.R

/**
 * Dialog for application settings.
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(0) }
    var selectedTheme by remember { mutableStateOf(0) }
    
    val languages = listOf(
        "English",
        "Русский",
        "中文"
    )
    
    val themes = listOf(
        "System",
        "Light",
        "Dark"
    )
    
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
                
                languages.forEachIndexed { index, language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedLanguage == index,
                            onClick = { selectedLanguage = index }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = language,
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
                
                themes.forEachIndexed { index, theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedTheme == index,
                            onClick = { selectedTheme = index }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = theme,
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
