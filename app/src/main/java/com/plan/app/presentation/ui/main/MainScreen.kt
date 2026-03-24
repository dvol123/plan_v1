package com.plan.app.presentation.ui.main

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var showMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var fullscreenPhotoUri by remember { mutableStateOf<String?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPhotoUri = uri
        if (uri != null) {
            viewModel.showCreateDialog()
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedPhotoUri = cameraImageUri
            viewModel.showCreateDialog()
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.app_name))
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
                                // TODO: Implement import
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
                                // TODO: Implement share
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
                                viewModel.showExportDialog()
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
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                // TODO: Implement exit with confirmation
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.exit))
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
                                // Create temp file for camera image
                                val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                                cameraImageUri = Uri.fromFile(tempFile)
                                cameraLauncher.launch(cameraImageUri)
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
                    // Show view-only dialog
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text(stringResource(R.string.search_projects)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            
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
                            onSelect = { viewModel.selectProject(project) },
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
                IconButton(
                    onClick = { fullscreenPhotoUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                
                ZoomableImage(
                    model = fullscreenPhotoUri,
                    contentDescription = "Full screen photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
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
                    // Navigate to project screen in editing mode
                    projects.lastOrNull()?.let { onProjectClick(it.id) }
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
            onDismiss = { viewModel.hideSettingsDialog() }
        )
    }
    
    // Error handling
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
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
            .clickable(onClick = onSelect),
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
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
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
