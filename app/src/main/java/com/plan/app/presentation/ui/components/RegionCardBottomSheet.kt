package com.plan.app.presentation.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.plan.app.R
import com.plan.app.domain.model.Content
import com.plan.app.domain.model.ContentType
import com.plan.app.domain.model.Region
import com.plan.app.domain.model.State

/**
 * Bottom sheet for displaying and editing region details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionCardBottomSheet(
    region: Region,
    states: List<State>,
    isEditing: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (Region) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onAddVideo: (Uri) -> Unit
) {
    val context = LocalContext.current
    
    var name by remember { mutableStateOf(region.name) }
    var selectedStateId by remember { mutableStateOf(region.stateId) }
    var type1 by remember { mutableStateOf(region.type1 ?: "") }
    var type2 by remember { mutableStateOf(region.type2 ?: "") }
    var description by remember { mutableStateOf(region.description ?: "") }
    var note by remember { mutableStateOf(region.note ?: "") }
    
    // Media picker launchers - use OpenDocument for persistent URI permission
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            onAddPhoto(it)
        }
    }
    
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            onAddVideo(it)
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) stringResource(R.string.edit_region) else stringResource(R.string.region_details),
                    style = MaterialTheme.typography.titleLarge
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Media gallery carousel
            if (region.contents.isNotEmpty() || isEditing) {
                Text(
                    text = stringResource(R.string.media),
                    style = MaterialTheme.typography.labelLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Existing media items
                    items(region.contents.filter { it.type == ContentType.PHOTO || it.type == ContentType.VIDEO }) { content ->
                        MediaThumbnail(
                            content = content,
                            onClick = {
                                // Open full-screen view
                            }
                        )
                    }
                    
                    // Add buttons in editing mode
                    if (isEditing) {
                        item {
                            AddMediaButton(
                                text = stringResource(R.string.add_photo),
                                icon = Icons.Default.AddAPhoto,
                                onClick = { photoPicker.launch(arrayOf("image/*")) }
                            )
                        }
                        
                        item {
                            AddMediaButton(
                                text = stringResource(R.string.add_video),
                                icon = Icons.Default.VideoCall,
                                onClick = { videoPicker.launch(arrayOf("video/*")) }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.region_name)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEditing,
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // State selector
            if (isEditing) {
                Text(
                    text = stringResource(R.string.state),
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                StateSelector(
                    states = states,
                    selectedStateId = selectedStateId,
                    onStateSelected = { selectedStateId = it }
                )
            } else {
                val currentState = states.find { it.id == selectedStateId }
                if (currentState != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(currentState.color))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentState.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Type 1
            OutlinedTextField(
                value = type1,
                onValueChange = { type1 = it },
                label = { Text(stringResource(R.string.type1)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEditing,
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Type 2
            OutlinedTextField(
                value = type2,
                onValueChange = { type2 = it },
                label = { Text(stringResource(R.string.type2)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEditing,
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isEditing,
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
                enabled = isEditing,
                minLines = 2,
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Save button (only in editing mode)
            if (isEditing) {
                Button(
                    onClick = {
                        onSave(region.copy(
                            name = name,
                            stateId = selectedStateId,
                            type1 = type1.ifBlank { null },
                            type2 = type2.ifBlank { null },
                            description = description.ifBlank { null },
                            note = note.ifBlank { null }
                        ))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MediaThumbnail(
    content: Content,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit = {},
    isEditing: Boolean = false
) {
    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (content.type) {
                ContentType.PHOTO -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(content.data)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                ContentType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VideoFile,
                            contentDescription = "Video",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                ContentType.TEXT -> {
                    // Text content is not shown in gallery
                }
            }
        }
    }
}

@Composable
private fun AddMediaButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StateSelector(
    states: List<State>,
    selectedStateId: Long?,
    onStateSelected: (Long?) -> Unit
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
                    // Create new state - this would be done through viewModel
                    showCreateDialog = false
                    newStateName = ""
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
