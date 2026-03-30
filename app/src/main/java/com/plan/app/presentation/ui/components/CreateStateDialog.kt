package com.plan.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plan.app.R
import com.plan.app.domain.model.State

/**
 * Dialog for creating a new state or selecting an existing one.
 * Used in region dialogs for state selection.
 */
@Composable
fun CreateStateDialog(
    existingStates: List<State>,
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit,
    onSelectExisting: (State) -> Unit
) {
    var stateName by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }
    
    // Filter existing states by name input
    val filteredStates = remember(existingStates, stateName) {
        if (stateName.isBlank()) {
            emptyList()
        } else {
            existingStates.filter { it.name.contains(stateName, ignoreCase = true) }
        }
    }
    
    // Check if exact match exists
    val exactMatch = existingStates.find { it.name.equals(stateName, ignoreCase = true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_state)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // State name input
                OutlinedTextField(
                    value = stateName,
                    onValueChange = { stateName = it },
                    label = { Text(stringResource(R.string.state_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = if (exactMatch != null) {
                        { Text(stringResource(R.string.state_already_exists), color = MaterialTheme.colorScheme.error) }
                    } else null
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Show existing states (dropdown-like list)
                if (filteredStates.isNotEmpty() && stateName.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.existing_states),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredStates.size) { index ->
                            val state = filteredStates[index]
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectExisting(state)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
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
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = stringResource(R.string.select),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Color picker (shown when creating new state - name entered and no exact match)
                @Suppress("KotlinConstantConditions")
                if (stateName.isNotBlank() && exactMatch == null) {
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
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (stateName.isNotBlank() && exactMatch == null) {
                        onCreate(stateName, State.PREDEFINED_COLORS[selectedColorIndex])
                    }
                },
                enabled = stateName.isNotBlank() && exactMatch == null
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
