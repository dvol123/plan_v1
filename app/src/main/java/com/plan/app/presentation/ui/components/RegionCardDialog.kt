package com.plan.app.presentation.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.io.File

/**
 * Dialog for displaying and editing region details.
 * This is a scrollable dialog that doesn't overlap the bottom bar.
 */
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
    
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraPermissionDenied by remember { mutableStateOf(false) }
    var pendingMediaType by remember { mutableStateOf<MediaType?>(null) }
    
    // Fullscreen media viewer state
    var showFullscreenMedia by remember { mutableStateOf(false) }
    var selectedMediaIndex by remember { mutableStateOf(0) }
    
    // Get media contents
    val mediaContents = remember(region.contents) {
        region.contents.filter { it.type == ContentType.PHOTO || it.type == ContentType.VIDEO }
    }
    
    // Camera launcher for photos
    val photoCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            onAddPhoto(cameraImageUri!!)
        }
    }
    
    // Camera launcher for videos
    val videoCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && cameraVideoUri != null) {
            onAddVideo(cameraVideoUri!!)
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
                    cameraImageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    cameraImageUri?.let { photoCameraLauncher.launch(it) }
                }
                MediaType.VIDEO -> {
                    val tempFile = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
                    cameraVideoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    cameraVideoUri?.let { videoCameraLauncher.launch(it) }
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
                                    cameraImageUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        tempFile
                                    )
                                    cameraImageUri?.let { photoCameraLauncher.launch(it) }
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
                                    cameraVideoUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        tempFile
                                    )
                                    cameraVideoUri?.let { videoCameraLauncher.launch(it) }
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
                        onStateSelected = { selectedStateId = it },
                        onCreateState = onCreateState
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
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
    onStateSelected: (Long?) -> Unit,
    onCreateState: (String, Int) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = stringResource(R.string.state),
            style = MaterialTheme.typography.labelMedium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        if (isEditing) {
            // Horizontal scrollable state chips
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
        } else {
            // View mode - show selected state
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
            } else {
                Text(
                    text = stringResource(R.string.no_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // Create state dialog
    if (showCreateDialog) {
        CreateStateDialog(
            existingStates = states,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color ->
                onCreateState(name, color)
                showCreateDialog = false
            },
            onSelectExisting = { state ->
                onStateSelected(state.id)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun FullscreenMediaViewer(
    mediaContents: List<Content>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    val content = mediaContents.getOrNull(currentIndex) ?: return
    
    // Video player state
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
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
                    .zIndex(1f)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Media content
            when (content.type) {
                ContentType.PHOTO -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(content.data)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                ContentType.VIDEO -> {
                    // Initialize ExoPlayer
                    LaunchedEffect(content.data) {
                        exoPlayer?.release()
                        exoPlayer = ExoPlayer.Builder(context).build().apply {
                            val mediaItem = androidx.media3.common.MediaItem.fromUri(content.data)
                            setMediaItem(mediaItem)
                            prepare()
                            playWhenReady = true
                        }
                    }
                    
                    // Cleanup
                    DisposableEffect(Unit) {
                        onDispose {
                            exoPlayer?.release()
                            exoPlayer = null
                        }
                    }
                    
                    exoPlayer?.let { player ->
                        AndroidView(
                            factory = {
                                PlayerView(it).apply {
                                    this.player = player
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                ContentType.TEXT -> {
                    // Not shown in fullscreen
                }
            }
            
            // Navigation arrows for multiple media
            if (mediaContents.size > 1) {
                // Previous button
                if (currentIndex > 0) {
                    IconButton(
                        onClick = { currentIndex-- },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
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
                        onClick = { currentIndex++ },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(16.dp)
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
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Enum for tracking pending media type when requesting camera permission
 */
private enum class MediaType {
    PHOTO,
    VIDEO
}
