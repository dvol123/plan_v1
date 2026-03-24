package com.plan.app.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.plan.app.R
import com.plan.app.domain.model.Cell
import com.plan.app.domain.model.State

/**
 * Dialog for creating a new region.
 */
@Composable
fun CreateRegionDialog(
    states: List<State>,
    onDismiss: () -> Unit,
    onCreate: (name: String, stateId: Long?, type1: String?, type2: String?, description: String?, note: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedStateId by remember { mutableStateOf<Long?>(null) }
    var type1 by remember { mutableStateOf("") }
    var type2 by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.create_region),
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Name (required)
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text(stringResource(R.string.region_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.field_required)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // State selector
                Text(
                    text = stringResource(R.string.state),
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Simple state chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    states.take(5).forEach { state ->
                        FilterChip(
                            selected = selectedStateId == state.id,
                            onClick = { 
                                selectedStateId = if (selectedStateId == state.id) null else state.id
                            },
                            label = { Text(state.name, maxLines = 1) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Type 1
                OutlinedTextField(
                    value = type1,
                    onValueChange = { type1 = it },
                    label = { Text(stringResource(R.string.type1)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Type 2
                OutlinedTextField(
                    value = type2,
                    onValueChange = { type2 = it },
                    label = { Text(stringResource(R.string.type2)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Note
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
                                    selectedStateId,
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
