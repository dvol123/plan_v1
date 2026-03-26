package com.plan.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onCreateState: (String, Int, (State) -> Unit) -> Unit = { _, _, _ -> }
) {
    var name by remember { mutableStateOf("") }
    var selectedStateId by remember { mutableStateOf<Long?>(null) }
    var type1 by remember { mutableStateOf("") }
    var type2 by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    
    // State field
    var stateText by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var showStateDropdown by remember { mutableStateOf(false) }
    
    // Filter states by input
    val filteredStates = remember(states, stateText) {
        if (stateText.isBlank()) {
            states
        } else {
            states.filter { it.name.contains(stateText, ignoreCase = true) }
        }
    }
    
    // Check if current text matches an existing state
    val matchedState = states.find { it.name.equals(stateText, ignoreCase = true) }
    
    // Determine if we're creating a new state (text entered but no match)
    val isCreatingNewState = stateText.isNotBlank() && matchedState == null
    
    // Update selectedStateId when matched state changes
    LaunchedEffect(matchedState) {
        selectedStateId = matchedState?.id
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.create_region),
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
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
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // === STATE FIELD (отдельное поле выше цвета) ===
                Text(
                    text = stringResource(R.string.state),
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // State input with dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stateText,
                        onValueChange = { 
                            stateText = it
                            showStateDropdown = it.isNotBlank()
                        },
                        label = { Text(stringResource(R.string.state_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            // Show color indicator if state selected
                            matchedState?.let { state ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(state.color))
                                )
                            }
                        }
                    )
                    
                    // Dropdown with existing states
                    if (showStateDropdown && filteredStates.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp,
                            tonalElevation = 2.dp
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 150.dp)
                            ) {
                                items(count = filteredStates.size) { index ->
                                    val state = filteredStates[index]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                stateText = state.name
                                                selectedStateId = state.id
                                                showStateDropdown = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(state.color))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = state.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // === COLOR FIELD (отдельное поле, показывается только при создании нового state) ===
                if (isCreatingNewState) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = stringResource(R.string.select_color),
                        style = MaterialTheme.typography.labelMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Color picker row
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
                            ) {
                                if (selectedColorIndex == index) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Preview of new state
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(State.PREDEFINED_COLORS[selectedColorIndex]).copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(State.PREDEFINED_COLORS[selectedColorIndex]))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "New: $stateText",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
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
                                // If creating new state, create it first
                                if (isCreatingNewState) {
                                    onCreateState(stateText, State.PREDEFINED_COLORS[selectedColorIndex]) { newState ->
                                        onCreate(
                                            name.trim(),
                                            newState.id,
                                            type1.trim().ifBlank { null },
                                            type2.trim().ifBlank { null },
                                            description.trim().ifBlank { null },
                                            note.trim().ifBlank { null }
                                        )
                                    }
                                } else {
                                    onCreate(
                                        name.trim(),
                                        selectedStateId,
                                        type1.trim().ifBlank { null },
                                        type2.trim().ifBlank { null },
                                        description.trim().ifBlank { null },
                                        note.trim().ifBlank { null }
                                    )
                                }
                            } else {
                                nameError = true
                            }
                        }
                    ) {
                        Text(stringResource(R.string.create))
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
