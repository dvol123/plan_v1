package com.plan.app.presentation.ui.project

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.plan.app.R
import com.plan.app.domain.model.Cell
import com.plan.app.domain.model.Project
import com.plan.app.domain.model.Region
import com.plan.app.domain.model.State
import com.plan.app.presentation.viewmodel.ProjectViewModel
import com.plan.app.presentation.ui.components.RegionCardDialog
import com.plan.app.presentation.ui.components.CreateRegionDialog
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    projectId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val regions by viewModel.regions.collectAsStateWithLifecycle()
    val states by viewModel.states.collectAsStateWithLifecycle()
    val selectedCells by viewModel.selectedCells.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val cellSize by viewModel.cellSize.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    
    var showMenu by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var cellSizeSlider by remember { mutableStateOf(cellSize.toFloat()) }
    
    // Track if user has selected cells (for save button activation)
    val hasSelectedCells = selectedCells.isNotEmpty()
    
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }
    
    // Sync slider with cellSize changes
    LaunchedEffect(cellSize) {
        cellSizeSlider = cellSize.toFloat()
    }
    
    // Error handling
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            // Header with project name on top, search and menu below
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Top row: project name with back button
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.project?.name ?: stringResource(R.string.loading),
                                maxLines = 1,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (isEditing) {
                                Text(
                                    text = stringResource(R.string.editing_mode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = { }
                )
                
                // Bottom row: search bar and menu button (like first screen)
                if (!isEditing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            placeholder = { Text(stringResource(R.string.search_regions)) },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Search, 
                                    contentDescription = null
                                ) 
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                }
            }
            
            // Dropdown menu (outside Column to properly overlay)
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        viewModel.toggleEditMode()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isEditing) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.edit))
                        }
                    }
                )
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        // TODO: Export for PC
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
                        // TODO: Share via Wi-Fi/Bluetooth
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
                        viewModel.showDeleteConfirm()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.delete))
                        }
                    }
                )
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        viewModel.showClearConfirm()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.clear))
                        }
                    }
                )
            }
        },
        bottomBar = {
            ProjectBottomBar(
                isEditing = isEditing,
                hasSelectedCells = hasSelectedCells,
                showRegionCard = uiState.showRegionCard,
                isEditingRegion = uiState.isEditingRegion,
                hasRegionChanges = hasSelectedCells,
                onHomeClick = onNavigateBack,
                onEditRegionClick = { viewModel.startEditingRegion() },
                onSaveClick = {
                    // When in editing mode with selected cells, show create region dialog
                    if (isEditing && hasSelectedCells) {
                        viewModel.showCreateRegionDialog()
                    }
                },
                onSaveRegionClick = {
                    // Save region changes
                    viewModel.stopEditingRegion()
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                uiState.project?.let { project ->
                    // Main photo with overlay - now with zoom support
                    ZoomablePhotoWithOverlay(
                        project = project,
                        regions = regions,
                        states = states,
                        isEditing = isEditing,
                        selectedCells = selectedCells,
                        cellSize = cellSize,
                        highlightedRegionIds = uiState.highlightedRegionIds,
                        imageSize = imageSize,
                        onImageSizeChanged = { imageSize = it },
                        onRegionDoubleTap = { region ->
                            viewModel.selectRegion(region)
                        },
                        onCellDoubleTap = { cell ->
                            viewModel.onCellDoubleTap(cell)
                        },
                        onCellSingleTap = { cell ->
                            viewModel.onCellSingleTap(cell)
                        },
                        isZoomEnabled = true // Enable zoom in both view and edit modes
                    )
                    
                    // Cell size slider in editing mode
                    if (isEditing) {
                        CellSizeControls(
                            cellSize = cellSizeSlider,
                            onCellSizeChange = { newValue ->
                                cellSizeSlider = newValue
                            },
                            onCellSizeConfirmed = { 
                                viewModel.setCellSize(it.toInt())
                            },
                            hasRegions = regions.isNotEmpty(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Region card dialog - doesn't overlap bottom bar
    if (uiState.showRegionCard && uiState.selectedRegion != null) {
        RegionCardDialog(
            region = uiState.selectedRegion!!,
            states = states,
            isEditing = uiState.isEditingRegion,
            onDismiss = { viewModel.closeRegionCard() },
            onSave = { updatedRegion ->
                viewModel.updateRegion(updatedRegion)
            },
            onAddPhoto = { /* TODO */ },
            onAddVideo = { /* TODO */ }
        )
    }
    
    // Create region dialog
    if (uiState.showCreateRegionDialog && selectedCells.isNotEmpty()) {
        CreateRegionDialog(
            states = states,
            onDismiss = { viewModel.hideCreateRegionDialog() },
            onCreate = { name, stateId, type1, type2, description, note ->
                viewModel.createRegion(name, stateId, type1, type2, description, note)
            }
        )
    }
    
    // Delete confirmation dialog
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text(stringResource(R.string.delete_project)) },
            text = { Text(stringResource(R.string.delete_project_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject()
                    onNavigateBack()
                }) {
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
    
    // Clear confirmation dialog
    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearConfirm() },
            title = { Text(stringResource(R.string.clear_regions)) },
            text = { Text(stringResource(R.string.clear_regions_confirmation)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllRegions() }) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ZoomablePhotoWithOverlay(
    project: Project,
    regions: List<Region>,
    states: List<State>,
    isEditing: Boolean,
    selectedCells: Set<Cell>,
    cellSize: Int,
    highlightedRegionIds: Set<Long>,
    imageSize: IntSize,
    onImageSizeChanged: (IntSize) -> Unit,
    onRegionDoubleTap: (Region) -> Unit,
    onCellDoubleTap: (Cell) -> Unit,
    onCellSingleTap: (Cell) -> Unit,
    isZoomEnabled: Boolean
) {
    var photoWidth by remember { mutableStateOf(0) }
    var photoHeight by remember { mutableStateOf(0) }
    
    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onImageSizeChanged(size)
                photoWidth = size.width
                photoHeight = size.height
            }
            .then(
                if (isZoomEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = max(0.5f, min(5f, scale * zoom))
                            
                            val maxOffsetX = (size.width * (scale - 1) / 2)
                            val maxOffsetY = (size.height * (scale - 1) / 2)
                            
                            offsetX = max(-maxOffsetX, min(maxOffsetX, offsetX + pan.x))
                            offsetY = max(-maxOffsetY, min(maxOffsetY, offsetY + pan.y))
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Base photo with zoom transform
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(project.photoUri)
                .crossfade(true)
                .build(),
            contentDescription = project.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )
        
        // Overlay canvas for regions or grid
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(isEditing, cellSize) {
                    if (isEditing) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val cell = positionToCell(offset, cellSize, photoWidth, photoHeight)
                                onCellDoubleTap(cell)
                            },
                            onTap = { offset ->
                                val cell = positionToCell(offset, cellSize, photoWidth, photoHeight)
                                onCellSingleTap(cell)
                            }
                        )
                    }
                }
        ) {
            if (isEditing) {
                // Draw grid
                drawGrid(cellSize, photoWidth, photoHeight)
                
                // Draw selected cells
                selectedCells.forEach { cell ->
                    drawSelectedCell(cell, cellSize, photoWidth, photoHeight)
                }
            } else {
                // Draw regions
                regions.forEach { region ->
                    val state = states.find { it.id == region.stateId }
                    val isHighlighted = highlightedRegionIds.contains(region.id)
                    drawRegion(region, state, cellSize, photoWidth, photoHeight, isHighlighted)
                }
            }
        }
        
        // Region tap detection (non-editing mode)
        if (!isEditing) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(regions) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val tappedRegion = findTappedRegion(
                                    offset, regions, cellSize, photoWidth, photoHeight
                                )
                                tappedRegion?.let { onRegionDoubleTap(it) }
                            }
                        )
                    }
            ) {}
        }
    }
}

private fun positionToCell(offset: Offset, cellSize: Int, width: Int, height: Int): Cell {
    val cellWidth = width.toFloat() / cellSize
    val cellHeight = height.toFloat() / cellSize
    val col = (offset.x / cellWidth).toInt()
    val row = (offset.y / cellHeight).toInt()
    return Cell(row = row, col = col)
}

private fun findTappedRegion(
    offset: Offset,
    regions: List<Region>,
    cellSize: Int,
    width: Int,
    height: Int
): Region? {
    val cellWidth = width.toFloat() / cellSize
    val cellHeight = height.toFloat() / cellSize
    val col = (offset.x / cellWidth).toInt()
    val row = (offset.y / cellHeight).toInt()
    
    return regions.find { region ->
        region.cells.any { it.row == row && it.col == col }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    cellSize: Int,
    width: Int,
    height: Int
) {
    val cellWidth = size.width / cellSize
    val cellHeight = size.height / cellSize
    
    // Use a solid, highly visible color for the grid
    val gridColor = Color(0xFF000000) // Solid black
    val strokeWidth = 2f // Thicker lines for better visibility
    
    for (i in 0..cellSize) {
        // Vertical lines
        drawLine(
            color = gridColor,
            start = Offset(x = i * cellWidth, y = 0f),
            end = Offset(x = i * cellWidth, y = size.height),
            strokeWidth = strokeWidth
        )
        
        // Horizontal lines
        drawLine(
            color = gridColor,
            start = Offset(x = 0f, y = i * cellHeight),
            end = Offset(x = size.width, y = i * cellHeight),
            strokeWidth = strokeWidth
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectedCell(
    cell: Cell,
    cellSize: Int,
    width: Int,
    height: Int
) {
    val cellWidth = size.width / cellSize
    val cellHeight = size.height / cellSize
    
    drawRect(
        color = Color.Blue.copy(alpha = 0.5f),
        topLeft = Offset(
            x = cell.col * cellWidth,
            y = cell.row * cellHeight
        ),
        size = Size(cellWidth, cellHeight)
    )
    
    drawRect(
        color = Color.Blue,
        topLeft = Offset(
            x = cell.col * cellWidth,
            y = cell.row * cellHeight
        ),
        size = Size(cellWidth, cellHeight),
        style = Stroke(width = 2f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRegion(
    region: Region,
    state: State?,
    cellSize: Int,
    width: Int,
    height: Int,
    isHighlighted: Boolean
) {
    if (region.cells.isEmpty()) return
    
    // Calculate cell dimensions based on the grid cellSize
    // cellSize represents the number of divisions (e.g., 10 means 10x10 grid)
    val gridDivisions = if (cellSize > 0) cellSize else 10
    val cellWidth = size.width / gridDivisions.toFloat()
    val cellHeight = size.height / gridDivisions.toFloat()
    
    val color = state?.color?.let { Color(it) } ?: Color.Gray
    
    region.cells.forEach { cell ->
        drawRect(
            color = color.copy(alpha = if (isHighlighted) 0.7f else 0.5f),
            topLeft = Offset(
                x = cell.col * cellWidth,
                y = cell.row * cellHeight
            ),
            size = Size(cellWidth, cellHeight)
        )
        
        if (isHighlighted) {
            drawRect(
                color = Color.Yellow,
                topLeft = Offset(
                    x = cell.col * cellWidth,
                    y = cell.row * cellHeight
                ),
                size = Size(cellWidth, cellHeight),
                style = Stroke(width = 3f)
            )
        }
    }
}

@Composable
private fun CellSizeControls(
    cellSize: Float,
    onCellSizeChange: (Float) -> Unit,
    onCellSizeConfirmed: (Float) -> Unit,
    hasRegions: Boolean,
    modifier: Modifier = Modifier
) {
    var showWarning by remember { mutableStateOf(false) }
    var pendingCellSize by remember { mutableStateOf(cellSize) }
    
    // Available cell sizes are powers of 2: 1, 2, 4, 8, 16, 32
    val cellSizeOptions = listOf(1f, 2f, 4f, 8f, 16f, 32f)
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.cell_size),
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus button - decreases to previous power of 2
                IconButton(
                    onClick = { 
                        val currentIndex = cellSizeOptions.indexOf(cellSize)
                        if (currentIndex > 0) {
                            val newSize = cellSizeOptions[currentIndex - 1]
                            onCellSizeChange(newSize)
                            onCellSizeConfirmed(newSize)
                        }
                    }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease grid")
                }
                
                Slider(
                    value = cellSize,
                    onValueChange = { newValue ->
                        // Find nearest power of 2
                        val nearestPowerOf2 = cellSizeOptions.minByOrNull { 
                            kotlin.math.abs(it - newValue) 
                        } ?: cellSize
                        
                        if (hasRegions && nearestPowerOf2 != cellSize) {
                            pendingCellSize = nearestPowerOf2
                            showWarning = true
                        } else {
                            onCellSizeChange(nearestPowerOf2)
                            onCellSizeConfirmed(nearestPowerOf2)
                        }
                    },
                    valueRange = 1f..32f,
                    steps = 4, // 5 steps between 6 values
                    modifier = Modifier.weight(1f)
                )
                
                // Plus button - increases to next power of 2
                IconButton(
                    onClick = { 
                        val currentIndex = cellSizeOptions.indexOf(cellSize)
                        if (currentIndex < cellSizeOptions.size - 1) {
                            val newSize = cellSizeOptions[currentIndex + 1]
                            onCellSizeChange(newSize)
                            onCellSizeConfirmed(newSize)
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase grid")
                }
            }
            
            // Show current grid size
            Text(
                text = "${cellSize.toInt()} × ${cellSize.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
    
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text(stringResource(R.string.warning)) },
            text = { Text(stringResource(R.string.cell_size_change_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showWarning = false
                    onCellSizeChange(pendingCellSize)
                    onCellSizeConfirmed(pendingCellSize)
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ProjectBottomBar(
    isEditing: Boolean,
    hasSelectedCells: Boolean,
    showRegionCard: Boolean,
    isEditingRegion: Boolean,
    hasRegionChanges: Boolean,
    onHomeClick: () -> Unit,
    onEditRegionClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveRegionClick: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = false,
            onClick = onHomeClick,
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text(stringResource(R.string.home)) }
        )
        
        NavigationBarItem(
            selected = false,
            onClick = { },
            icon = { 
                Icon(
                    Icons.Default.Star, 
                    contentDescription = "Star",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            },
            label = { Text(stringResource(R.string.star)) },
            enabled = false
        )
        
        NavigationBarItem(
            selected = false,
            onClick = { if (showRegionCard) onEditRegionClick() },
            icon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = if (showRegionCard) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            label = { Text(stringResource(R.string.edit)) },
            enabled = showRegionCard
        )
        
        NavigationBarItem(
            selected = false,
            onClick = { 
                if (isEditing && hasSelectedCells) {
                    onSaveClick()
                } else if (showRegionCard && hasRegionChanges) {
                    onSaveRegionClick()
                }
            },
            icon = {
                val isEnabled = (isEditing && hasSelectedCells) || (showRegionCard && hasRegionChanges)
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Save",
                    tint = if (isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            label = { Text(stringResource(R.string.save)) },
            enabled = (isEditing && hasSelectedCells) || (showRegionCard && hasRegionChanges)
        )
    }
}
