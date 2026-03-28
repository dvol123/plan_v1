package com.plan.app.presentation.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.plan.app.R
import com.plan.app.domain.model.Content
import com.plan.app.domain.model.ContentType
import com.plan.app.domain.model.Region
import com.plan.app.domain.model.State
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import net.engawapg.lib.zoomable.ScrollGesturePropagation
import java.io.File
import kotlinx.coroutines.launch

/**
 * Dialog for displaying and editing region details.
 * This is a scrollable dialog that doesn't overlap the bottom bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RegionCardDialog(
    region: Region,
    states: List<State>,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (Region) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onAddVideo: (Uri) -> Unit,
    onCreateState: (String, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    
    var name by remember { mutableStateOf(region.name) }
    var selectedStateId by remember { mutableStateOf(region.stateId) }
    var type1 by remember { mutableStateOf(region.type1 ?: "") }
    var type2 by remember { mutableStateOf(region.type2 ?: "") }
    var description by remember { mutableStateOf(region.description ?: "") }
    var note by remember { mutableStateOf(region.note ?: "") }
    
    // State field state
    var stateText by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var pendingNewState by remember { mutableStateOf<Pair<String, Int>?>(null) }
    
    var cameraImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraVideoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var showCameraPermissionDenied by remember { mutableStateOf(false) }
    var pendingMediaType by remember { mutableStateOf<MediaType?>(null) }
    
    // Convert saved strings back to URIs
    val cameraImageUri = cameraImageUriString?.let { Uri.parse(it) }
    val cameraVideoUri = cameraVideoUriString?.let { Uri.parse(it) }
    
    // Fullscreen media viewer state
    var showFullscreenMedia by remember { mutableStateOf(false) }
    var selectedMediaIndex by remember { mutableStateOf(0) }
    
    // Get media contents
    val mediaContents = remember(region.contents) {
        region.contents.filter { it.type == ContentType.PHOTO || it.type == ContentType.VIDEO }
    }
    
    // Camera launcher for photos - check file existence regardless of success flag
    val photoCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        // Check if file exists regardless of success flag
        // (success may be false on rotation, but file still exists)
        cameraImageUri?.let { uri ->
            val fileExists = try {
                context.contentResolver.openInputStream(uri)?.close()
                true
            } catch (e: Exception) {
                false
            }
            if (fileExists) {
                onAddPhoto(uri)
                cameraImageUriString = null // Clear after successful add
            } else {
                // Clean up temp file if camera was cancelled
                try {
                    val path = uri.path
                    if (path != null) {
                        File(path).delete()
                    }
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }
    
    // Camera launcher for videos - check file existence regardless of success flag
    val videoCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        // Check if file exists regardless of success flag
        cameraVideoUri?.let { uri ->
            val fileExists = try {
                context.contentResolver.openInputStream(uri)?.close()
                true
            } catch (e: Exception) {
                false
            }
            if (fileExists) {
                onAddVideo(uri)
                cameraVideoUriString = null // Clear after successful add
            } else {
                // Clean up temp file if camera was cancelled
                try {
                    val path = uri.path
                    if (path != null) {
                        File(path).delete()
                    }
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, launch appropriate camera
            when (pendingMediaType) {
                MediaType.PHOTO -> {
                    val tempFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    cameraImageUriString = uri.toString()
                    photoCameraLauncher.launch(uri)
                }
                MediaType.VIDEO -> {
                    val tempFile = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    cameraVideoUriString = uri.toString()
                    videoCameraLauncher.launch(uri)
                }
                null -> {}
            }
        } else {
            showCameraPermissionDenied = true
        }
        pendingMediaType = null
    }
    
    // Camera permission denied dialog
    if (showCameraPermissionDenied) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDenied = false },
            title = { Text(stringResource(R.string.permission_required)) },
            text = { Text(stringResource(R.string.camera_permission_denied)) },
            confirmButton = {
                TextButton(onClick = { showCameraPermissionDenied = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
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
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                
                HorizontalDivider()
                
                // Content - scrollable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp)
                ) {
                    // Media gallery carousel
                    MediaGallerySection(
                        mediaContents = mediaContents,
                        isEditing = isEditing,
                        onMediaClick = { index ->
                            selectedMediaIndex = index
                            showFullscreenMedia = true
                        },
                        onAddPhoto = {
                            pendingMediaType = MediaType.PHOTO
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                                PackageManager.PERMISSION_GRANTED -> {
                                    val tempFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        tempFile
                                    )
                                    cameraImageUriString = uri.toString()
                                    photoCameraLauncher.launch(uri)
                                }
                                else -> {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                        onAddVideo = {
                            pendingMediaType = MediaType.VIDEO
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                                PackageManager.PERMISSION_GRANTED -> {
                                    val tempFile = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        tempFile
                                    )
                                    cameraVideoUriString = uri.toString()
                                    videoCameraLauncher.launch(uri)
                                }
                                else -> {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Name field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.region_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isEditing,
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // State selector - between name and types
                    StateSelectorSection(
                        states = states,
                        selectedStateId = selectedStateId,
                        isEditing = isEditing,
                        stateText = stateText,
                        selectedColorIndex = selectedColorIndex,
                        onStateTextChanged = { stateText = it },
                        onColorSelected = { selectedColorIndex = it },
                        onStateSelected = { selectedStateId = it },
                        onNewStatePrepared = { name, color -> pendingNewState = Pair(name, color) }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Type 1
                    OutlinedTextField(
                        value = type1,
                        onValueChange = { type1 = it },
                        label = { Text(stringResource(R.string.type1)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isEditing,
                        minLines = 2,
                        maxLines = 4
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Type 2
                    OutlinedTextField(
                        value = type2,
                        onValueChange = { type2 = it },
                        label = { Text(stringResource(R.string.type2)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isEditing,
                        minLines = 2,
                        maxLines = 4
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
                                // Check if we need to create a new state first
                                val pending = pendingNewState
                                if (pending != null) {
                                    // Create new state, then save region
                                    onCreateState(pending.first, pending.second)
                                    // Note: stateId will be set after state is created
                                    // For now, save without stateId - it will be updated when state is created
                                }
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
    }
    
    // Fullscreen media viewer
    if (showFullscreenMedia && mediaContents.isNotEmpty()) {
        FullscreenMediaViewer(
            mediaContents = mediaContents,
            initialIndex = selectedMediaIndex,
            onDismiss = { showFullscreenMedia = false }
        )
    }
}

@Composable
private fun MediaGallerySection(
    mediaContents: List<Content>,
    isEditing: Boolean,
    onMediaClick: (Int) -> Unit,
    onAddPhoto: () -> Unit,
    onAddVideo: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.media),
            style = MaterialTheme.typography.labelLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show placeholder if no media and not editing
            if (mediaContents.isEmpty() && !isEditing) {
                item {
                    MediaPlaceholder()
                }
            } else {
                // Existing media items
                items(mediaContents.indices.toList()) { index ->
                    MediaThumbnail(
                        content = mediaContents[index],
                        onClick = { onMediaClick(index) }
                    )
                }
            }
            
            // Add buttons in editing mode
            if (isEditing) {
                item {
                    AddMediaButton(
                        text = stringResource(R.string.add_photo),
                        icon = Icons.Default.AddAPhoto,
                        onClick = onAddPhoto
                    )
                }
                
                item {
                    AddMediaButton(
                        text = stringResource(R.string.add_video),
                        icon = Icons.Default.VideoCall,
                        onClick = onAddVideo
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.ImageNotSupported,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.no_media),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    content: Content,
    onClick: () -> Unit
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
                            Icons.Default.PlayCircleFilled,
                            contentDescription = "Video",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
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
private fun StateSelectorSection(
    states: List<State>,
    selectedStateId: Long?,
    isEditing: Boolean,
    stateText: String,
    selectedColorIndex: Int,
    onStateTextChanged: (String) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStateSelected: (Long?) -> Unit,
    onNewStatePrepared: (String, Int) -> Unit
) {
    // Find currently selected state
    val selectedState = states.find { it.id == selectedStateId }
    
    // Initialize stateText from selected state if empty
    var initializedStateText by remember { mutableStateOf(false) }
    LaunchedEffect(selectedState) {
        if (!initializedStateText && selectedState != null && stateText.isEmpty()) {
            onStateTextChanged(selectedState.name)
            initializedStateText = true
        }
    }
    
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
    
    // Track dropdown visibility
    var showDropdown by remember { mutableStateOf(false) }
    
    Column {
        // === STATE FIELD (отдельное поле выше) ===
        Text(
            text = stringResource(R.string.state),
            style = MaterialTheme.typography.labelMedium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        if (isEditing) {
            // State input with dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = stateText,
                    onValueChange = { 
                        onStateTextChanged(it)
                        showDropdown = it.isNotBlank()
                        // Auto-select if matches existing state
                        val match = states.find { s -> s.name.equals(it, ignoreCase = true) }
                        if (match != null) {
                            onStateSelected(match.id)
                        } else if (it.isNotBlank()) {
                            // New state - prepare it with selected color
                            onNewStatePrepared(it, State.PREDEFINED_COLORS[selectedColorIndex])
                        }
                    },
                    label = { Text(stringResource(R.string.state_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        // Show color indicator if state matched
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
                if (showDropdown && filteredStates.isNotEmpty()) {
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
                            items(filteredStates.size) { index ->
                                val state = filteredStates[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onStateTextChanged(state.name)
                                            onStateSelected(state.id)
                                            showDropdown = false
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
            
            // === COLOR FIELD (отдельное поле ниже, только при создании нового state) ===
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
                                .clickable { 
                                    onColorSelected(index)
                                    onNewStatePrepared(stateText, State.PREDEFINED_COLORS[index])
                                }
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
        } else {
            // View mode - show selected state
            if (selectedState != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(selectedState.color))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedState.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.no_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenMediaViewer(
    mediaContents: List<Content>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Pager state for swipe between photos
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaContents.size }
    )
    
    // Current content based on pager
    val currentIndex = pagerState.currentPage
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .zIndex(2f)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // HorizontalPager for swipe between media
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageContent = mediaContents.getOrNull(page)
                
                when (pageContent?.type) {
                    ContentType.PHOTO -> {
                        ZoomablePhoto(
                            contentData = pageContent.data
                        )
                    }
                    ContentType.VIDEO -> {
                        // Each video page has its own player managed by VideoPlayer composable
                        VideoPlayer(
                            videoUri = pageContent.data,
                            isCurrentPage = page == currentIndex
                        )
                    }
                    ContentType.TEXT -> {
                        // Not shown in fullscreen
                    }
                    null -> {
                        // Empty
                    }
                }
            }
            
            // Navigation arrows for multiple media
            if (mediaContents.size > 1) {
                // Previous button
                if (currentIndex > 0) {
                    IconButton(
                        onClick = { 
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(currentIndex - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                            .zIndex(2f)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                // Next button
                if (currentIndex < mediaContents.size - 1) {
                    IconButton(
                        onClick = { 
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(currentIndex + 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(16.dp)
                            .zIndex(2f)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                // Page indicator
                Text(
                    text = "${currentIndex + 1} / ${mediaContents.size}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .zIndex(2f)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Zoomable photo composable using the Zoomable library.
 * This implementation properly handles:
 * - Pinch-to-zoom
 * - Double-tap to zoom
 * - Pan when zoomed
 * - Swipe between photos in HorizontalPager (via scrollGesturePropagation)
 */
@Composable
private fun ZoomablePhoto(
    contentData: String
) {
    val context = LocalContext.current
    
    // Create zoom state with proper configuration for HorizontalPager
    val zoomState = rememberZoomState()
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(contentData)
                .crossfade(true)
                .build(),
            contentDescription = "Photo",
            modifier = Modifier
                .fillMaxSize()
                .zoomable(
                    zoomState = zoomState,
                    // ContentEdge: scroll gesture is propagated when content is scrolled to edge
                    // This allows HorizontalPager swipe to work when not zoomed or at content edge
                    scrollGesturePropagation = ScrollGesturePropagation.ContentEdge
                ),
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                // Set content size for proper zoom behavior
                zoomState.setContentSize(state.painter.intrinsicSize)
            }
        )
    }
}

/**
 * Video player composable with proper lifecycle management
 * Each instance manages its own ExoPlayer
 */
@Composable
private fun VideoPlayer(
    videoUri: String,
    isCurrentPage: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Create ExoPlayer for this video
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            prepare()
        }
    }
    
    // Manage playback based on page visibility
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
        }
    }
    
    // Lifecycle awareness
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    try { exoPlayer.pause() } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_STOP -> {
                    try { exoPlayer.pause() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "Error releasing ExoPlayer", e)
            }
        }
    }
    
    // Render video - ExoPlayer handles rotation automatically
    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Enum for tracking pending media type when requesting camera permission
 */
private enum class MediaType {
    PHOTO,
    VIDEO
}
