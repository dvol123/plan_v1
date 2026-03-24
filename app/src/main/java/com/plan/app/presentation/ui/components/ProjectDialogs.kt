package com.plan.app.presentation.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.plan.app.R

/**
 * Dialog for creating a new project.
 */
@Composable
fun CreateProjectDialog(
    photoUri: Uri,
    onDismiss: () -> Unit,
    onCreate: (name: String, type1: String?, type2: String?, description: String?, note: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type1 by remember { mutableStateOf("") }
    var type2 by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.create_project),
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Photo preview
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Selected photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Name field (required)
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text(stringResource(R.string.project_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.field_required)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Type 1 field
                OutlinedTextField(
                    value = type1,
                    onValueChange = { type1 = it },
                    label = { Text(stringResource(R.string.type1)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Type 2 field
                OutlinedTextField(
                    value = type2,
                    onValueChange = { type2 = it },
                    label = { Text(stringResource(R.string.type2)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Note field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onCreate(
                                    name.trim(),
                                    type1.trim().ifBlank { null },
                                    type2.trim().ifBlank { null },
                                    description.trim().ifBlank { null },
                                    note.trim().ifBlank { null }
                                )
                            } else {
                                nameError = true
                            }
                        }
                    ) {
                        Text(stringResource(R.string.create))
                    }
                }
            }
        }
    }
}

/**
 * Dialog for editing project fields.
 */
@Composable
fun EditProjectDialog(
    projectName: String,
    type1: String?,
    type2: String?,
    description: String?,
    note: String?,
    onDismiss: () -> Unit,
    onSave: (type1: String?, type2: String?, description: String?, note: String?) -> Unit,
    onDelete: () -> Unit
) {
    var type1State by remember { mutableStateOf(type1 ?: "") }
    var type2State by remember { mutableStateOf(type2 ?: "") }
    var descriptionState by remember { mutableStateOf(description ?: "") }
    var noteState by remember { mutableStateOf(note ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_project)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.project_name_label, projectName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = type1State,
                    onValueChange = { type1State = it },
                    label = { Text(stringResource(R.string.type1)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = type2State,
                    onValueChange = { type2State = it },
                    label = { Text(stringResource(R.string.type2)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = descriptionState,
                    onValueChange = { descriptionState = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = noteState,
                    onValueChange = { noteState = it },
                    label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    type1State.trim().ifBlank { null },
                    type2State.trim().ifBlank { null },
                    descriptionState.trim().ifBlank { null },
                    noteState.trim().ifBlank { null }
                )
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
