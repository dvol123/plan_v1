package com.plan.app.presentation.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.plan.app.R
import com.plan.app.domain.model.Project
import com.plan.app.presentation.viewmodel.MainViewModel
import com.plan.app.presentation.ui.components.SettingsDialog
import com.plan.app.presentation.ui.components.CreateProjectDialog
import com.plan.app.presentation.ui.components.EditProjectDialog
import com.plan.app.presentation.ui.components.ZoomableImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onProjectClick: (Long) -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    onThemeChanged: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var showMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var fullscreenPhotoUri by remember { mutableStateOf<String?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraPermissionDenied by remember { mutableStateOf(false) }
    var showViewDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    
    // Camera launcher - must be declared before cameraPermissionLauncher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedPhotoUri = cameraImageUri
            viewModel.showCreateDialog()
        }
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, launch camera
            val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            cameraImageUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            cameraImageUri?.let { cameraLauncher.launch(it) }
        } else {
            // Permission denied
            showCameraPermissionDenied = true
        }
    }
    
    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPhotoUri = uri
        if (uri != null) {
            viewModel.showCreateDialog()
        }
    }
    
    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importProjectFromZip(it) { success, error ->
                val message = if (success) context.getString(R.string.import_success) else error
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Export launcher - creates HTML report in ZIP (for viewing on PC)
    val exportProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            val tempFile = File(context.cacheDir, "export_html_temp.zip")
            viewModel.exportProjectForPC(tempFile) { success, error ->
                if (success) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, error ?: context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
                tempFile.delete()
            }
        }
    }
    
    // Export all launcher - creates HTML reports for all projects
    val exportAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            val tempFile = File(context.cacheDir, "export_all_html_temp.zip")
            viewModel.exportAllProjectsForPC(tempFile) { success, error ->
                if (success) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, error ?: context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
                tempFile.delete()
            }
        }
    }
    
    // Share launcher - creates JSON ZIP for importing on another device
    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            val tempFile = File(context.cacheDir, "share_temp.zip")
            uiState.selectedProject?.let { project ->
                viewModel.exportProjectToZip(context, tempFile) { success, error ->
                    if (success) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        // Create share intent
                        val shareUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            tempFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
                    } else {
                        Toast.makeText(context, error ?: context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    // Share all launcher - shares all projects
    val shareAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            val tempFile = File(context.cacheDir, "share_all_temp.zip")
            viewModel.exportAllProjectsToZip(tempFile) { success, error ->
                if (success) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    // Create share intent
                    val shareUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
                } else {
                    Toast.makeText(context, error ?: context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
                tempFile.delete()
            }
        }
    }
    
    // Filter projects based on search query
    val filteredProjects = remember(projects, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            projects
        } else {
            projects.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
        }
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
    
    // View project dialog (read-only)
    if (showViewDialog && uiState.selectedProject != null) {
        ViewProjectDialog(
            project = uiState.selectedProject!!,
            onDismiss = { showViewDialog = false }
        )
    }
    
    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.export_project)) },
            text = { Text(stringResource(R.string.choose_what_to_export)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    if (uiState.selectedProject != null) {
                        exportProjectLauncher.launch("${uiState.selectedProject!!.name}.zip")
                    } else {
                        exportAllLauncher.launch("all_projects.zip")
                    }
                }) {
                    Text(if (uiState.selectedProject != null) stringResource(R.string.export_selected) else stringResource(R.string.export_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Share dialog
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(stringResource(R.string.share)) },
            text = { Text(stringResource(R.string.choose_what_to_share)) },
            confirmButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    if (uiState.selectedProject != null) {
                        shareLauncher.launch("${uiState.selectedProject!!.name}_share.zip")
                    } else {
                        Toast.makeText(context, context.getString(R.string.select_project_first), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(if (uiState.selectedProject != null) stringResource(R.string.share_selected) else stringResource(R.string.select_project_first))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showShareDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    // Share all button
                    TextButton(onClick = {
                        showShareDialog = false
                        shareAllLauncher.launch("all_projects_share.zip")
                    }) {
                        Text(stringResource(R.string.share_all))
                    }
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Search bar in header as per specification
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        placeholder = { 
                            Text(
                                stringResource(R.string.search_projects),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            ) 
                        },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.Search, 
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            ) 
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                showAddMenu = true
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.add))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                // Import
                                importLauncher.launch("application/zip")
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.import_project))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                // Export - show dialog to choose
                                showExportDialog = true
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.export_project))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                // Show share dialog
                                showShareDialog = true
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.share))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                viewModel.showSettingsDialog()
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings))
                                }
                            }
                        )
                    }
                    
                    // Add submenu for camera/gallery
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                showAddMenu = false
                                // Check camera permission first
                                when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                                    PackageManager.PERMISSION_GRANTED -> {
                                        // Permission already granted, launch camera
                                        val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                                        cameraImageUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            tempFile
                                        )
                                        cameraImageUri?.let { cameraLauncher.launch(it) }
                                    }
                                    else -> {
                                        // Request permission
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.take_photo))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                showAddMenu = false
                                photoPickerLauncher.launch("image/*")
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.choose_from_gallery))
                                }
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            MainBottomBar(
                selectedProject = uiState.selectedProject,
                onViewClick = {
                    uiState.selectedProject?.let { project ->
                        onProjectClick(project.id)
                    }
                },
                onEditClick = {
                    viewModel.showEditDialog()
                },
                onViewDetailsClick = {
                    showViewDialog = true
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Project list
            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_projects),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(filteredProjects, key = { it.id }) { project ->
                        ProjectListItem(
                            project = project,
                            isSelected = uiState.selectedProject?.id == project.id,
                            onSelect = { viewModel.toggleProjectSelection(project) },
                            onThumbnailClick = {
                                fullscreenPhotoUri = project.photoUri
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Fullscreen photo dialog
    if (fullscreenPhotoUri != null) {
        Dialog(
            onDismissRequest = { fullscreenPhotoUri = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // ZoomableImage placed first (bottom layer)
                ZoomableImage(
                    model = fullscreenPhotoUri,
                    contentDescription = "Full screen photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                // Close button placed after (top layer) so it receives touch events
                IconButton(
                    onClick = { fullscreenPhotoUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
    
    // Create Project Dialog
    if (uiState.showCreateDialog && selectedPhotoUri != null) {
        CreateProjectDialog(
            photoUri = selectedPhotoUri!!,
            onDismiss = {
                viewModel.hideCreateDialog()
                selectedPhotoUri = null
            },
            onCreate = { name, type1, type2, description, note ->
                selectedPhotoUri?.let { uri ->
                    viewModel.createProject(
                        Project(
                            name = name,
                            photoUri = uri.toString(),
                            type1 = type1,
                            type2 = type2,
                            description = description,
                            note = note
                        )
                    )
                    selectedPhotoUri = null
                }
            }
        )
    }
    
    // Edit Project Dialog
    if (uiState.showEditDialog && uiState.selectedProject != null) {
        EditProjectDialog(
            project = uiState.selectedProject!!,
            onDismiss = { viewModel.hideEditDialog() },
            onSave = { project ->
                viewModel.updateProject(project)
            },
            onDelete = {
                viewModel.hideEditDialog()
                viewModel.showDeleteConfirm()
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text(stringResource(R.string.delete_project)) },
            text = { Text(stringResource(R.string.delete_project_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject()
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Settings Dialog
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            onDismiss = { viewModel.hideSettingsDialog() },
            onThemeChanged = onThemeChanged
        )
    }
    
    // Error handling
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
    
    // Loading indicator
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ProjectListItem(
    project: Project,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onThumbnailClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() }, // Single tap selects/deselects
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!project.description.isNullOrBlank()) {
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Thumbnail - clickable for fullscreen view
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(project.photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = project.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onThumbnailClick),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun MainBottomBar(
    selectedProject: Project?,
    onViewClick: () -> Unit,
    onEditClick: () -> Unit,
    onViewDetailsClick: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = { /* Already on main screen */ },
            icon = { 
                Icon(Icons.Default.Home, contentDescription = "Home")
            },
            label = { Text(stringResource(R.string.home)) }
        )
        NavigationBarItem(
            selected = false,
            onClick = { if (selectedProject != null) onViewClick() },
            icon = { 
                Icon(
                    Icons.Default.Star, 
                    contentDescription = "View",
                    tint = if (selectedProject != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            label = { Text(stringResource(R.string.star)) },
            enabled = selectedProject != null
        )
        NavigationBarItem(
            selected = false,
            onClick = { if (selectedProject != null) onEditClick() },
            icon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = if (selectedProject != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            label = { Text(stringResource(R.string.edit)) },
            enabled = selectedProject != null
        )
        NavigationBarItem(
            selected = false,
            onClick = { if (selectedProject != null) onViewDetailsClick() },
            icon = {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "View Details",
                    tint = if (selectedProject != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            label = { Text(stringResource(R.string.view)) },
            enabled = selectedProject != null
        )
    }
}

/**
 * Read-only view dialog for viewing all project fields
 */
@Composable
private fun ViewProjectDialog(
    project: Project,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_name_label, project.name)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!project.type1.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.type1) + ":",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(text = project.type1, style = MaterialTheme.typography.bodyMedium)
                }
                
                if (!project.type2.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.type2) + ":",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(text = project.type2, style = MaterialTheme.typography.bodyMedium)
                }
                
                if (!project.description.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.description) + ":",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(text = project.description, style = MaterialTheme.typography.bodyMedium)
                }
                
                if (!project.note.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.note) + ":",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(text = project.note, style = MaterialTheme.typography.bodyMedium)
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
