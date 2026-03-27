package com.plan.app.presentation.ui.project

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.File
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
    
    // Handle region card close after save
    LaunchedEffect(uiState.shouldCloseRegionCard) {
        if (uiState.shouldCloseRegionCard) {
            viewModel.closeRegionCard()
            viewModel.onRegionCardClosed()
        }
    }
    
    // Export message handling
    LaunchedEffect(uiState.exportMessage) {
        uiState.exportMessage?.let {
            val resId = if (uiState.exportSuccess) R.string.export_success else R.string.export_error
            Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
            viewModel.clearExportMessage()
        }
    }
    
    // Export launcher - creates HTML report in ZIP (for viewing on PC)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            val tempFile = File(context.cacheDir, "temp_export_html.zip")
            viewModel.exportProjectForPC(tempFile) { success, error ->
                if (success) {
                    try {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(outputStream)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProjectScreen", "Error writing export file", e)
                        Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                    }
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
    ) { uri ->
        uri?.let {
            val tempFile = File(context.cacheDir, "temp_share.zip")
            viewModel.exportProjectToZip(tempFile) { success, error ->
                if (success) {
                    try {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(outputStream)
                            }
                        }
                        // Share the file
                        val shareUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            tempFile
                        )
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share)))
                    } catch (e: Exception) {
                        android.util.Log.e("ProjectScreen", "Error sharing file", e)
                        Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, error ?: context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
                tempFile.delete()
            }
        }
    }
    
    Scaffold(
        topBar = {
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
                    actions = {
                        // Menu button always visible
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
                
                // Bottom row: search bar (only in view mode)
                if (!isEditing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            placeholder = { 
                                Text(
                                    stringResource(R.string.search_regions),
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
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // Dropdown menu
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Toggle edit/view mode
                    if (isEditing) {
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                viewModel.toggleEditMode()
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.view_mode))
                                }
                            }
                        )
                        // Delete and Clear only in editing mode
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
                    } else {
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                viewModel.toggleEditMode()
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.edit_mode))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                showMenu = false
                                uiState.project?.let { project ->
                                    exportLauncher.launch("${project.name}_report.zip")
                                }
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
                                uiState.project?.let { project ->
                                    shareLauncher.launch("${project.name}_share.zip")
                                }
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.share))
                                }
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            ProjectBottomBar(
                isEditing = isEditing,
                hasSelectedCells = hasSelectedCells,
                selectedRegion = uiState.selectedRegion,
                showRegionCard = uiState.showRegionCard,
                isEditingRegion = uiState.isEditingRegion,
                hasRegionChanges = hasSelectedCells,
                onHomeClick = {
                    // If region is selected, deselect it; otherwise navigate back
                    if (uiState.selectedRegion != null) {
                        viewModel.deselectRegion()
                    } else {
                        onNavigateBack()
                    }
                },
                onEditRegionClick = { viewModel.editRegion() },
                onViewRegionClick = { viewModel.viewRegion() },
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
                        selectedRegionId = uiState.selectedRegion?.id,
                        imageSize = imageSize,
                        onImageSizeChanged = { imageSize = it },
                        onRegionDoubleTap = { region ->
                            // Toggle selection: if already selected, deselect; otherwise select
                            if (uiState.selectedRegion?.id == region.id) {
                                viewModel.deselectRegion()
                            } else {
                                viewModel.selectRegion(region)
                            }
                        },
                        onCellSingleTap = { cell ->
                            viewModel.onCellSingleTap(cell)
                        },
                        isZoomEnabled = true // Enable zoom in both view and edit modes
                    )
                    
                    // Cell size slider in editing mode - only show if flag is set and no regions exist
                    if (isEditing && uiState.showGridSizeControls && regions.isEmpty()) {
                        CellSizeControls(
                            cellSize = cellSizeSlider,
                            onCellSizeChange = { newValue ->
                                cellSizeSlider = newValue
                            },
                            onCellSizeConfirmed = { 
                                viewModel.setCellSize(it.toInt())
                            },
                            hasRegions = false, // Always false here since we check regions.isEmpty() above
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
            onAddPhoto = { uri ->
                uiState.selectedRegion?.let { region ->
                    viewModel.addPhotoToRegion(context, region.id, uri)
                }
            },
            onAddVideo = { uri ->
                uiState.selectedRegion?.let { region ->
                    viewModel.addVideoToRegion(context, region.id, uri)
                }
            },
            onCreateState = { name, color ->
                viewModel.createState(name, color) { newState ->
                    // Auto-select the newly created state for this region
                    uiState.selectedRegion?.let { region ->
                        viewModel.updateRegion(region.copy(stateId = newState.id))
                    }
                }
            }
        )
    }
    
    // Create region dialog
    if (uiState.showCreateRegionDialog && selectedCells.isNotEmpty()) {
        CreateRegionDialog(
            states = states,
            onDismiss = { viewModel.hideCreateRegionDialog() },
            onCreate = { name, stateId, type1, type2, description, note ->
                viewModel.createRegion(name, stateId, type1, type2, description, note)
            },
            onCreateState = { name, color, onCreated ->
                viewModel.createState(name, color, onCreated)
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
    selectedRegionId: Long?,
    imageSize: IntSize,
    onImageSizeChanged: (IntSize) -> Unit,
    onRegionDoubleTap: (Region) -> Unit,
    onCellSingleTap: (Cell) -> Unit,
    isZoomEnabled: Boolean
) {
    var photoWidth by remember { mutableStateOf(0) }
    var photoHeight by remember { mutableStateOf(0) }
    
    // Original image dimensions for proper scaling
    var originalImageWidth by remember { mutableStateOf(0) }
    var originalImageHeight by remember { mutableStateOf(0) }
    
    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Calculate the actual displayed image bounds (after ContentScale.Fit)
    val imageDisplayRect = remember(photoWidth, photoHeight, originalImageWidth, originalImageHeight) {
        if (photoWidth > 0 && photoHeight > 0 && originalImageWidth > 0 && originalImageHeight > 0) {
            val containerAspect = photoWidth.toFloat() / photoHeight
            val imageAspect = originalImageWidth.toFloat() / originalImageHeight
            
            if (containerAspect > imageAspect) {
                // Container is wider - image is constrained by height
                val displayWidth = photoHeight * imageAspect
                val offsetX = (photoWidth - displayWidth) / 2
                android.graphics.RectF(offsetX, 0f, offsetX + displayWidth, photoHeight.toFloat())
            } else {
                // Container is taller - image is constrained by width
                val displayHeight = photoWidth / imageAspect
                val offsetY = (photoHeight - displayHeight) / 2
                android.graphics.RectF(0f, offsetY, photoWidth.toFloat(), offsetY + displayHeight)
            }
        } else {
            android.graphics.RectF(0f, 0f, photoWidth.toFloat(), photoHeight.toFloat())
        }
    }
    
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
                            
                            val maxOffsetX = (photoWidth * (scale - 1) / 2)
                            val maxOffsetY = (photoHeight * (scale - 1) / 2)
                            
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
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                // Get original image dimensions
                state.result.drawable?.let { drawable ->
                    originalImageWidth = drawable.intrinsicWidth
                    originalImageHeight = drawable.intrinsicHeight
                }
            }
        )
        
        // Overlay canvas for regions or grid - now using imageDisplayRect for proper positioning
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(isEditing, cellSize, imageDisplayRect) {
                    if (isEditing) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val cell = positionToCell(tapOffset, cellSize, imageDisplayRect)
                                onCellSingleTap(cell)
                            }
                        )
                    }
                }
        ) {
            if (isEditing) {
                // Draw existing regions first (in editing mode)
                regions.forEach { region ->
                    val state = states.find { it.id == region.stateId }
                    drawRegion(region, state, cellSize, imageDisplayRect, isHighlighted = false, isSelected = false, isInEditMode = true)
                }
                
                // Draw grid
                drawGrid(cellSize, imageDisplayRect)
                
                // Draw selected cells
                selectedCells.forEach { cell ->
                    drawSelectedCell(cell, cellSize, imageDisplayRect)
                }
            } else {
                // Draw regions
                regions.forEach { region ->
                    val state = states.find { it.id == region.stateId }
                    val isHighlighted = highlightedRegionIds.contains(region.id)
                    val isSelected = selectedRegionId == region.id
                    drawRegion(region, state, cellSize, imageDisplayRect, isHighlighted, isSelected)
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
                    .pointerInput(regions, cellSize, imageDisplayRect) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val tappedRegion = findTappedRegion(
                                    offset, regions, cellSize, imageDisplayRect
                                )
                                tappedRegion?.let { onRegionDoubleTap(it) }
                            }
                        )
                    }
            ) {}
        }
    }
}

private fun positionToCell(offset: Offset, cellSize: Int, imageDisplayRect: android.graphics.RectF): Cell {
    // Convert tap offset to image-relative coordinates
    val imageRelativeX = offset.x - imageDisplayRect.left
    val imageRelativeY = offset.y - imageDisplayRect.top
    
    val cellWidth = imageDisplayRect.width() / cellSize
    val cellHeight = imageDisplayRect.height() / cellSize
    val col = (imageRelativeX / cellWidth).toInt()
    val row = (imageRelativeY / cellHeight).toInt()
    return Cell(row = row, col = col)
}

private fun findTappedRegion(
    offset: Offset,
    regions: List<Region>,
    cellSize: Int,
    imageDisplayRect: android.graphics.RectF
): Region? {
    // Convert tap offset to image-relative coordinates
    val imageRelativeX = offset.x - imageDisplayRect.left
    val imageRelativeY = offset.y - imageDisplayRect.top
    
    val cellWidth = imageDisplayRect.width() / cellSize
    val cellHeight = imageDisplayRect.height() / cellSize
    val col = (imageRelativeX / cellWidth).toInt()
    val row = (imageRelativeY / cellHeight).toInt()
    
    // Find all regions containing this cell, then return the smallest one
    // (smallest by number of cells) to allow selecting smaller regions inside larger ones
    return regions
        .filter { region -> region.cells.any { it.row == row && it.col == col } }
        .minByOrNull { it.cells.size }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    cellSize: Int,
    imageDisplayRect: android.graphics.RectF
) {
    val cellWidth = imageDisplayRect.width() / cellSize
    val cellHeight = imageDisplayRect.height() / cellSize
    
    // Use a solid, highly visible color for the grid
    val gridColor = Color(0xFF000000) // Solid black
    val strokeWidth = 2f // Thicker lines for better visibility
    
    for (i in 0..cellSize) {
        // Vertical lines
        drawLine(
            color = gridColor,
            start = Offset(x = imageDisplayRect.left + i * cellWidth, y = imageDisplayRect.top),
            end = Offset(x = imageDisplayRect.left + i * cellWidth, y = imageDisplayRect.bottom),
            strokeWidth = strokeWidth
        )
        
        // Horizontal lines
        drawLine(
            color = gridColor,
            start = Offset(x = imageDisplayRect.left, y = imageDisplayRect.top + i * cellHeight),
            end = Offset(x = imageDisplayRect.right, y = imageDisplayRect.top + i * cellHeight),
            strokeWidth = strokeWidth
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectedCell(
    cell: Cell,
    cellSize: Int,
    imageDisplayRect: android.graphics.RectF
) {
    val cellWidth = imageDisplayRect.width() / cellSize
    val cellHeight = imageDisplayRect.height() / cellSize
    
    drawRect(
        color = Color.Blue.copy(alpha = 0.5f),
        topLeft = Offset(
            x = imageDisplayRect.left + cell.col * cellWidth,
            y = imageDisplayRect.top + cell.row * cellHeight
        ),
        size = Size(cellWidth, cellHeight)
    )
    
    drawRect(
        color = Color.Blue,
        topLeft = Offset(
            x = imageDisplayRect.left + cell.col * cellWidth,
            y = imageDisplayRect.top + cell.row * cellHeight
        ),
        size = Size(cellWidth, cellHeight),
        style = Stroke(width = 2f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRegion(
    region: Region,
    state: State?,
    cellSize: Int,
    imageDisplayRect: android.graphics.RectF,
    isHighlighted: Boolean,
    isSelected: Boolean = false,
    isInEditMode: Boolean = false
) {
    if (region.cells.isEmpty()) return
    
    // Calculate cell dimensions based on the grid cellSize
    // cellSize represents the number of divisions (e.g., 10 means 10x10 grid)
    val gridDivisions = if (cellSize > 0) cellSize else 10
    val cellWidth = imageDisplayRect.width() / gridDivisions
    val cellHeight = imageDisplayRect.height() / gridDivisions
    
    val color = state?.color?.let { Color(it) } ?: Color.Gray
    
    region.cells.forEach { cell ->
        val alpha = when {
            isSelected -> 0.7f
            isHighlighted -> 0.6f
            isInEditMode -> 0.3f // More transparent in edit mode to see grid better
            else -> 0.5f
        }
        
        drawRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(
                x = imageDisplayRect.left + cell.col * cellWidth,
                y = imageDisplayRect.top + cell.row * cellHeight
            ),
            size = Size(cellWidth, cellHeight)
        )
        
        // Draw border for highlighted, selected, or edit mode regions
        if (isHighlighted || isSelected || isInEditMode) {
            drawRect(
                color = when {
                    isSelected -> Color.White
                    isHighlighted -> Color.Yellow
                    else -> Color.Gray.copy(alpha = 0.5f) // Subtle border in edit mode
                },
                topLeft = Offset(
                    x = imageDisplayRect.left + cell.col * cellWidth,
                    y = imageDisplayRect.top + cell.row * cellHeight
                ),
                size = Size(cellWidth, cellHeight),
                style = Stroke(width = if (isSelected) 4f else 3f)
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cell_size),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                
                // Show lock icon if regions exist
                if (hasRegions) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Grid locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus button - disabled if regions exist
                IconButton(
                    onClick = { 
                        val currentIndex = cellSizeOptions.indexOf(cellSize)
                        if (currentIndex > 0) {
                            val newSize = cellSizeOptions[currentIndex - 1]
                            onCellSizeChange(newSize)
                            onCellSizeConfirmed(newSize)
                        }
                    },
                    enabled = !hasRegions
                ) {
                    Icon(
                        Icons.Default.Remove, 
                        contentDescription = "Decrease grid",
                        tint = if (hasRegions) 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Slider(
                    value = cellSize,
                    onValueChange = { newValue ->
                        // Only allow changes if no regions exist
                        if (!hasRegions) {
                            val nearestPowerOf2 = cellSizeOptions.minByOrNull { 
                                kotlin.math.abs(it - newValue) 
                            } ?: cellSize
                            onCellSizeChange(nearestPowerOf2)
                            onCellSizeConfirmed(nearestPowerOf2)
                        }
                    },
                    valueRange = 1f..32f,
                    steps = 4, // 5 steps between 6 values
                    modifier = Modifier.weight(1f),
                    enabled = !hasRegions
                )
                
                // Plus button - disabled if regions exist
                IconButton(
                    onClick = { 
                        val currentIndex = cellSizeOptions.indexOf(cellSize)
                        if (currentIndex < cellSizeOptions.size - 1) {
                            val newSize = cellSizeOptions[currentIndex + 1]
                            onCellSizeChange(newSize)
                            onCellSizeConfirmed(newSize)
                        }
                    },
                    enabled = !hasRegions
                ) {
                    Icon(
                        Icons.Default.Add, 
                        contentDescription = "Increase grid",
                        tint = if (hasRegions) 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Show current grid size
            Text(
                text = "${cellSize.toInt()} × ${cellSize.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            // Show info message if locked
            if (hasRegions) {
                Text(
                    text = stringResource(R.string.grid_locked_info),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ProjectBottomBar(
    isEditing: Boolean,
    hasSelectedCells: Boolean,
    selectedRegion: Region?,
    showRegionCard: Boolean,
    isEditingRegion: Boolean,
    hasRegionChanges: Boolean,
    onHomeClick: () -> Unit,
    onEditRegionClick: () -> Unit,
    onViewRegionClick: () -> Unit,
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
        
        // Star button (placeholder)
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
        
        // Edit button - active when a region is selected in view mode
        NavigationBarItem(
            selected = false,
            onClick = { 
                if (selectedRegion != null) {
                    onEditRegionClick()
                } else if (showRegionCard) {
                    onEditRegionClick()
                }
            },
            icon = {
                val isEnabled = selectedRegion != null || showRegionCard
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = if (isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            label = { Text(stringResource(R.string.edit)) },
            enabled = selectedRegion != null || showRegionCard
        )
        
        // View/Check button - changes label based on mode
        NavigationBarItem(
            selected = false,
            onClick = { 
                if (selectedRegion != null) {
                    onViewRegionClick()
                } else if (isEditing && hasSelectedCells) {
                    onSaveClick()
                } else if (showRegionCard && hasRegionChanges) {
                    onSaveRegionClick()
                }
            },
            icon = {
                val isEnabled = selectedRegion != null || (isEditing && hasSelectedCells) || (showRegionCard && hasRegionChanges)
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (isEditing) "Create" else "View",
                    tint = if (isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            },
            label = { 
                Text(
                    if (isEditing) stringResource(R.string.create_label) 
                    else stringResource(R.string.view)
                ) 
            },
            enabled = selectedRegion != null || (isEditing && hasSelectedCells) || (showRegionCard && hasRegionChanges)
        )
    }
}
