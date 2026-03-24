package com.plan.app.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plan.app.domain.model.Project

/**
 * Overloaded EditProjectDialog that accepts a Project object.
 */
@Composable
fun EditProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (Project) -> Unit,
    onDelete: () -> Unit
) {
    var type1 by remember { mutableStateOf(project.type1 ?: "") }
    var type2 by remember { mutableStateOf(project.type2 ?: "") }
    var description by remember { mutableStateOf(project.description ?: "") }
    var note by remember { mutableStateOf(project.note ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(com.plan.app.R.string.edit_project)) },
        text = {
            Column {
                Text(
                    text = stringResource(com.plan.app.R.string.project_name_label, project.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = type1,
                    onValueChange = { type1 = it },
                    label = { Text(stringResource(com.plan.app.R.string.type1)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = type2,
                    onValueChange = { type2 = it },
                    label = { Text(stringResource(com.plan.app.R.string.type2)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(com.plan.app.R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(com.plan.app.R.string.note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(project.copy(
                    type1 = type1.trim().ifBlank { null },
                    type2 = type2.trim().ifBlank { null },
                    description = description.trim().ifBlank { null },
                    note = note.trim().ifBlank { null }
                ))
            }) {
                Text(stringResource(com.plan.app.R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(com.plan.app.R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(com.plan.app.R.string.cancel))
                }
            }
        }
    )
}
