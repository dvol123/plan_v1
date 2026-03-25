package com.plan.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.plan.app.R
import com.plan.app.domain.model.State

/**
 * Dialog for creating a new region.
 */
@Composable
fun CreateRegionDialog(
    states: List<State>,
    onDismiss: () -> Unit,
    onCreate: (name: String, stateId: Long?, type1: String?, type2: String?, description: String?, note: String?) -> Unit,
    onCreateState: (String, Int) -> Unit = { _, _ -> }
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
                
                // State chips with create button
                CreateRegionStateSelector(
                    states = states,
                    selectedStateId = selectedStateId,
                    onStateSelected = { selectedStateId = it },
                    onCreateState = onCreateState
                )
                
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

@Composable
private fun CreateRegionStateSelector(
    states: List<State>,
    selectedStateId: Long?,
    onStateSelected: (Long?) -> Unit,
    onCreateState: (String, Int) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newStateName by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }
    
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        states.forEach { state ->
            FilterChip(
                selected = selectedStateId == state.id,
                onClick = { 
                    onStateSelected(if (selectedStateId == state.id) null else state.id)
                },
                label = { Text(state.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(state.color))
                    )
                }
            )
        }
        
        // Create new state button
        FilterChip(
            selected = false,
            onClick = { showCreateDialog = true },
            label = { Text(stringResource(R.string.new_state)) },
            leadingIcon = {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )
    }
    
    // Create state dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.create_state)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newStateName,
                        onValueChange = { newStateName = it },
                        label = { Text(stringResource(R.string.state_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.select_color),
                        style = MaterialTheme.typography.labelMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        State.PREDEFINED_COLORS.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(color))
                                    .clickable { selectedColorIndex = index }
                                    .then(
                                        if (selectedColorIndex == index) {
                                            Modifier.padding(4.dp)
                                        } else Modifier
                                    )
                            ) {
                                if (selectedColorIndex == index) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newStateName.isNotBlank()) {
                        onCreateState(newStateName, State.PREDEFINED_COLORS[selectedColorIndex])
                        showCreateDialog = false
                        newStateName = ""
                    }
                }) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
