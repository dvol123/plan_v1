package com.plan.app.presentation.ui.project

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.plan.app.presentation.ui.components.RegionCardBottomSheet
import com.plan.app.presentation.ui.components.CreateRegionDialog
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    
    var showMenu by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var cellSizeSlider by remember { mutableStateOf(cellSize.toFloat()) }
    
    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.project?.name ?: stringResource(R.string.loading),
                            maxLines = 1
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
                actions = {
                    // Search bar in header
                    if (!isEditing) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .width(150.dp)
                                .height(40.dp),
                            placeholder = { Text(stringResource(R.string.search_regions), fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Search, 
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                ) 
                            },
                            singleLine = true
                        )
                    }
                    
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showMenu = false
                                viewModel.toggleEditMode()
                            },
                            leadingIcon = { 
                                Icon(
                                    if (isEditing) Icons.Default.Visibility else Icons.Default.Edit, 
                                    contentDescription = null
                                ) 
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_project)) },
                            onClick = {
                                showMenu = false
                                // TODO: Export for PC
                            },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share)) },
                            onClick = {
                                showMenu = false
                                // TODO: Share via Wi-Fi/Bluetooth
                            },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showMenu = false
                                viewModel.showDeleteConfirm()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clear)) },
                            onClick = {
                                showMenu = false
                                viewModel.showClearConfirm()
                            },
                            leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            ProjectBottomBar(
                isEditing = isEditing,
                hasSelectedCells = selectedCells.isNotEmpty(),
                showRegionCard = uiState.showRegionCard,
                isEditingRegion = uiState.isEditingRegion,
                hasRegionChanges = false, // TODO: Track changes
                onHomeClick = onNavigateBack,
                onEditRegionClick = { viewModel.startEditingRegion() },
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
                    // Main photo with overlay
                    PhotoWithOverlay(
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
                        }
                    )
                    
                    // Cell size slider in editing mode
                    if (isEditing) {
                        CellSizeControls(
                            cellSize = cellSizeSlider,
                            onCellSizeChange = { cellSizeSlider = it },
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
    
    // Region card bottom sheet
    if (uiState.showRegionCard && uiState.selectedRegion != null) {
        RegionCardBottomSheet(
            region = uiState.selectedRegion!!,
            states = states,
            isEditing = uiState.isEditingRegion,
            sheetState = sheetState,
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
private fun PhotoWithOverlay(
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
    onCellSingleTap: (Cell) -> Unit
) {
    var photoWidth by remember { mutableStateOf(0) }
    var photoHeight by remember { mutableStateOf(0) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onImageSizeChanged(size)
                photoWidth = size.width
                photoHeight = size.height
            }
    ) {
        // Base photo
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(project.photoUri)
                .crossfade(true)
                .build(),
            contentDescription = project.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        
        // Overlay canvas for regions or grid
        Canvas(
            modifier = Modifier
                .fillMaxSize()
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
                    drawRegion(region, state, photoWidth, photoHeight, isHighlighted)
                }
            }
        }
        
        // Region tap detection (non-editing mode)
        if (!isEditing) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
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
    
    for (i in 0..cellSize) {
        // Vertical lines
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(x = i * cellWidth, y = 0f),
            end = Offset(x = i * cellWidth, y = size.height),
            strokeWidth = 1f
        )
        
        // Horizontal lines
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(x = 0f, y = i * cellHeight),
            end = Offset(x = size.width, y = i * cellHeight),
            strokeWidth = 1f
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
    width: Int,
    height: Int,
    isHighlighted: Boolean
) {
    if (region.cells.isEmpty()) return
    
    // Calculate cell size based on image dimensions
    // This is simplified - actual implementation would need photo dimensions
    val cellWidth = size.width / 10f // Placeholder
    val cellHeight = size.height / 10f // Placeholder
    
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
                IconButton(
                    onClick = { 
                        if (cellSize > 1) {
                            onCellSizeChange(cellSize / 2)
                            onCellSizeConfirmed(cellSize / 2)
                        }
                    }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                
                Slider(
                    value = cellSize,
                    onValueChange = { newValue ->
                        if (hasRegions && newValue != cellSize) {
                            showWarning = true
                        } else {
                            onCellSizeChange(newValue)
                        }
                    },
                    valueRange = 1f..32f,
                    steps = 4,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { 
                        if (cellSize < 32) {
                            onCellSizeChange(cellSize * 2)
                            onCellSizeConfirmed(cellSize * 2)
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
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
                    onCellSizeConfirmed(cellSize)
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
            onClick = { if (hasRegionChanges) onSaveRegionClick() },
            icon = {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Save",
                    tint = if (showRegionCard && hasRegionChanges) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            label = { Text(stringResource(R.string.save)) },
            enabled = showRegionCard && hasRegionChanges
        )
    }
}
