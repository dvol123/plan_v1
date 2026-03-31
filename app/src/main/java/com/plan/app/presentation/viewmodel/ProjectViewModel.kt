package com.plan.app.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plan.app.domain.manager.ExportManager
import com.plan.app.domain.manager.GridManager
import com.plan.app.domain.manager.ProjectManager
import com.plan.app.domain.model.Cell
import com.plan.app.domain.model.Content
import com.plan.app.domain.model.ContentType
import com.plan.app.domain.model.Project
import com.plan.app.domain.model.Region
import com.plan.app.domain.model.State
import com.plan.app.domain.repository.RegionRepository
import com.plan.app.domain.repository.StateRepository
import com.plan.app.domain.usecase.ManageContentUseCase
import com.plan.app.domain.usecase.ManageProjectUseCase
import com.plan.app.domain.usecase.ManageRegionUseCase
import com.plan.app.domain.usecase.ManageStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.plan.app.presentation.ui.components.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * UI State for Project Screen.
 */
data class ProjectUiState(
    val isLoading: Boolean = false,
    val project: Project? = null,
    val selectedRegion: Region? = null,
    val isViewMode: Boolean = true,
    val showRegionCard: Boolean = false,
    val isEditingRegion: Boolean = false,
    val searchQuery: String = "",
    val highlightedRegionIds: Set<Long> = emptySet(),
    val showCreateRegionDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showClearConfirm: Boolean = false,
    val errorMessage: String? = null,
    val exportSuccess: Boolean = false,
    val exportMessage: String? = null,
    val shouldCloseRegionCard: Boolean = false,
    val showGridSizeControls: Boolean = true // Controls visibility of grid size controls
)

/**
 * ViewModel for Project Screen.
 */
@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProjectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manageProjectUseCase: ManageProjectUseCase,
    private val regionRepository: RegionRepository,
    private val manageRegionUseCase: ManageRegionUseCase,
    private val manageContentUseCase: ManageContentUseCase,
    private val stateRepository: StateRepository,
    private val manageStateUseCase: ManageStateUseCase,
    private val projectManager: ProjectManager,
    private val gridManager: GridManager,
    private val exportManager: ExportManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()
    
    private val _projectId = MutableStateFlow(0L)
    
    // Use flatMapLatest to properly react to projectId changes
    val regions: StateFlow<List<Region>> = _projectId
        .flatMapLatest { projectId ->
            if (projectId > 0) {
                regionRepository.getRegionsByProject(projectId)
            } else {
                MutableStateFlow(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val states: StateFlow<List<State>> = stateRepository.getAllStates()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val selectedCells: StateFlow<Set<Cell>> = gridManager.selectedCells
    
    val isEditing: StateFlow<Boolean> = gridManager.isEditing
    
    val cellSize: StateFlow<Int> = gridManager.cellSize
    
    fun loadProject(projectId: Long) {
        _projectId.value = projectId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val project = manageProjectUseCase.getById(projectId)
                _uiState.value = _uiState.value.copy(
                    project = project,
                    isLoading = false,
                    // Show grid controls only if no regions exist (cellSize is still at default)
                    showGridSizeControls = project?.cellSize == 1
                )
                project?.let {
                    projectManager.setCurrentProject(it)
                    gridManager.setCellSize(it.cellSize)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message,
                    isLoading = false
                )
            }
        }
    }
    
    fun toggleEditMode() {
        val newMode = !_uiState.value.isViewMode
        val wasInEditMode = !_uiState.value.isViewMode // true if we're exiting edit mode
        
        _uiState.value = _uiState.value.copy(isViewMode = newMode)
        gridManager.setEditingMode(!newMode)
        
        // When exiting edit mode, lock grid controls if cellSize was changed (> 1)
        // This ensures the grid size can be adjusted during the first editing session
        // but is locked for subsequent sessions
        if (wasInEditMode && gridManager.cellSize.value > 1) {
            _uiState.value = _uiState.value.copy(showGridSizeControls = false)
        }
    }
    
    fun selectRegion(region: Region?) {
        // Just select the region without showing the card
        _uiState.value = _uiState.value.copy(
            selectedRegion = region,
            showRegionCard = false,
            isEditingRegion = false
        )
    }
    
    fun deselectRegion() {
        _uiState.value = _uiState.value.copy(
            selectedRegion = null,
            showRegionCard = false,
            isEditingRegion = false
        )
    }
    
    fun viewRegion() {
        if (_uiState.value.selectedRegion != null) {
            _uiState.value = _uiState.value.copy(
                showRegionCard = true,
                isEditingRegion = false
            )
        }
    }
    
    fun editRegion() {
        if (_uiState.value.selectedRegion != null) {
            _uiState.value = _uiState.value.copy(
                showRegionCard = true,
                isEditingRegion = true
            )
        }
    }
    
    fun startEditingRegion() {
        _uiState.value = _uiState.value.copy(isEditingRegion = true)
    }
    
    fun stopEditingRegion() {
        _uiState.value = _uiState.value.copy(isEditingRegion = false)
    }
    
    fun closeRegionCard() {
        _uiState.value = _uiState.value.copy(
            showRegionCard = false,
            selectedRegion = null,
            isEditingRegion = false
        )
    }
    
    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isNotBlank()) {
            val matchingIds = regions.value
                .filter { region ->
                    region.name.contains(query, ignoreCase = true) ||
                    region.type1?.contains(query, ignoreCase = true) == true ||
                    region.type2?.contains(query, ignoreCase = true) == true
                }
                .map { it.id }
                .toSet()
            _uiState.value = _uiState.value.copy(highlightedRegionIds = matchingIds)
        } else {
            _uiState.value = _uiState.value.copy(highlightedRegionIds = emptySet())
        }
    }
    
    // Grid operations
    fun onCellSingleTap(cell: Cell) {
        gridManager.toggleCellSelection(cell)
    }
    
    fun clearSelection() {
        gridManager.clearSelection()
    }
    
    fun setCellSize(size: Int) {
        gridManager.setCellSize(size)
        // Note: Grid controls visibility is managed in toggleEditMode()
        // Controls remain visible during the first editing session to allow adjustments
        // They get locked only when exiting edit mode (after user confirms their choice)
        viewModelScope.launch {
            _uiState.value.project?.let { project ->
                manageProjectUseCase.update(project.copy(cellSize = size))
            }
        }
    }
    
    // Region CRUD
    fun showCreateRegionDialog() {
        _uiState.value = _uiState.value.copy(showCreateRegionDialog = true)
    }
    
    fun hideCreateRegionDialog() {
        _uiState.value = _uiState.value.copy(showCreateRegionDialog = false)
        clearSelection()
    }
    
    fun createRegion(name: String, stateId: Long?, type1: String?, type2: String?, description: String?, note: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val project = _uiState.value.project ?: return@launch
                val cells = gridManager.getSelectedCellsList()
                
                val region = Region(
                    projectId = project.id,
                    name = name,
                    stateId = stateId,
                    type1 = type1,
                    type2 = type2,
                    description = description,
                    note = note,
                    cells = cells
                )
                
                manageRegionUseCase.create(region)
                hideCreateRegionDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun updateRegion(region: Region) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                manageRegionUseCase.update(region)
                _uiState.value = _uiState.value.copy(
                    selectedRegion = region,
                    isEditingRegion = false,
                    shouldCloseRegionCard = true // Signal to close dialog after save
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun onRegionCardClosed() {
        _uiState.value = _uiState.value.copy(shouldCloseRegionCard = false)
    }
    
    fun showDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }
    
    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }
    
    fun deleteProject() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                _uiState.value.project?.let { project ->
                    manageRegionUseCase.deleteAllByProject(project.id)
                    manageProjectUseCase.delete(project)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun showClearConfirm() {
        _uiState.value = _uiState.value.copy(showClearConfirm = true)
    }
    
    fun hideClearConfirm() {
        _uiState.value = _uiState.value.copy(showClearConfirm = false)
    }
    
    fun clearAllRegions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                _uiState.value.project?.let { project ->
                    manageRegionUseCase.deleteAllByProject(project.id)
                }
                hideClearConfirm()
                // Reset grid controls visibility when all regions are cleared
                _uiState.value = _uiState.value.copy(showGridSizeControls = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    // Content operations
    fun addContent(regionId: Long, content: Content) {
        viewModelScope.launch {
            try {
                manageContentUseCase.add(content.copy(regionId = regionId))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    fun deleteContent(content: Content) {
        viewModelScope.launch {
            try {
                manageContentUseCase.delete(content)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun setUnsavedChanges(hasChanges: Boolean) {
        projectManager.setUnsavedChanges(hasChanges)
    }
    
    // State operations
    fun createState(name: String, color: Int, onCreated: (State) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val state = manageStateUseCase.getOrCreate(name, color)
                onCreated(state)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }
    
    // Media operations
    fun addPhotoToRegion(context: Context, regionId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ProjectViewModel", "Adding photo to region $regionId, uri: $uri")
                
                // Copy file to app's permanent storage
                val savedPath = copyMediaToPermanentStorage(context, uri, regionId, "photo")
                android.util.Log.d("ProjectViewModel", "Photo saved to: $savedPath")
                
                // Get next sort order
                val sortOrder = manageContentUseCase.getByRegionOnce(regionId).size
                
                // Create content record
                val content = Content(
                    regionId = regionId,
                    type = ContentType.PHOTO,
                    data = savedPath,
                    sortOrder = sortOrder
                )
                val contentId = manageContentUseCase.add(content)
                android.util.Log.d("ProjectViewModel", "Content inserted with id: $contentId")
                
                // Refresh the selected region
                refreshSelectedRegion()
                android.util.Log.d("ProjectViewModel", "Region refreshed, contents count: ${_uiState.value.selectedRegion?.contents?.size}")
            } catch (e: Exception) {
                android.util.Log.e("ProjectViewModel", "Failed to save photo", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save photo: ${e.message}")
            }
        }
    }
    
    fun addVideoToRegion(context: Context, regionId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ProjectViewModel", "Adding video to region $regionId, uri: $uri")
                
                // Copy file to app's permanent storage
                val savedPath = copyMediaToPermanentStorage(context, uri, regionId, "video")
                android.util.Log.d("ProjectViewModel", "Video saved to: $savedPath")
                
                // Get next sort order
                val sortOrder = manageContentUseCase.getByRegionOnce(regionId).size
                
                // Create content record
                val content = Content(
                    regionId = regionId,
                    type = ContentType.VIDEO,
                    data = savedPath,
                    sortOrder = sortOrder
                )
                val contentId = manageContentUseCase.add(content)
                android.util.Log.d("ProjectViewModel", "Content inserted with id: $contentId")
                
                // Refresh the selected region
                refreshSelectedRegion()
                android.util.Log.d("ProjectViewModel", "Region refreshed, contents count: ${_uiState.value.selectedRegion?.contents?.size}")
            } catch (e: Exception) {
                android.util.Log.e("ProjectViewModel", "Failed to save video", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save video: ${e.message}")
            }
        }
    }
    
    fun addFileToRegion(context: Context, regionId: Long, uri: Uri) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ProjectViewModel", "Adding file to region $regionId, uri: $uri")
                
                // Copy file to app's permanent storage
                val savedPath = copyMediaToPermanentStorage(context, uri, regionId, "file")
                android.util.Log.d("ProjectViewModel", "File saved to: $savedPath")
                
                // Get next sort order
                val sortOrder = manageContentUseCase.getByRegionOnce(regionId).size
                
                // Create content record
                val content = Content(
                    regionId = regionId,
                    type = ContentType.FILE,
                    data = savedPath,
                    sortOrder = sortOrder
                )
                val contentId = manageContentUseCase.add(content)
                android.util.Log.d("ProjectViewModel", "Content inserted with id: $contentId")
                
                // Refresh the selected region
                refreshSelectedRegion()
                android.util.Log.d("ProjectViewModel", "Region refreshed, contents count: ${_uiState.value.selectedRegion?.contents?.size}")
            } catch (e: Exception) {
                android.util.Log.e("ProjectViewModel", "Failed to save file", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save file: ${e.message}")
            }
        }
    }
    
    private suspend fun refreshSelectedRegion() {
        _uiState.value.selectedRegion?.let { currentRegion ->
            android.util.Log.d("ProjectViewModel", "Refreshing region ${currentRegion.id}")
            val updatedRegion = regionRepository.getRegionById(currentRegion.id)
            if (updatedRegion != null) {
                android.util.Log.d("ProjectViewModel", "Updated region contents: ${updatedRegion.contents.size}")
                _uiState.value = _uiState.value.copy(selectedRegion = updatedRegion)
            } else {
                android.util.Log.w("ProjectViewModel", "Updated region is null!")
            }
        }
    }
    
    private suspend fun copyMediaToPermanentStorage(context: Context, sourceUri: Uri, regionId: Long, type: String): String {
        return withContext(Dispatchers.IO) {
            // Create directory for media files
            val mediaDir = File(context.filesDir, "media")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            // Create unique filename
            val timestamp = System.currentTimeMillis()
            val extension = when (type) {
                "photo" -> "jpg"
                "video" -> "mp4"
                "file" -> {
                    // Try to get extension from URI
                    sourceUri.lastPathSegment?.substringAfterLast(".", "bin") ?: "bin"
                }
                else -> "bin"
            }
            val fileName = "${type}_${regionId}_$timestamp.$extension"
            val destFile = File(mediaDir, fileName)
            
            if (type == "photo") {
                // For photos: handle EXIF orientation
                copyPhotoWithCorrectOrientation(context, sourceUri, destFile)
            } else {
                // For videos and files: just copy the file
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            destFile.absolutePath
        }
    }
    
    /**
     * Copy photo with correct orientation based on EXIF data.
     * This ensures the photo is displayed correctly regardless of how it was captured.
     * Uses sampling for large images to prevent OutOfMemoryError.
     */
    private fun copyPhotoWithCorrectOrientation(context: Context, sourceUri: Uri, destFile: File) {
        try {
            // Open input stream and copy to a temp file first (to read EXIF)
            val tempFile = File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // Read EXIF orientation from temp file
            val exif = ExifInterface(tempFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            // Calculate rotation angle from EXIF orientation
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f // Handle flip separately if needed
                else -> 0f
            }
            
            // Also check for flipped orientations
            val needsFlip = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
                           orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
                           orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
                           orientation == ExifInterface.ORIENTATION_TRANSPOSE
            
            if (rotationDegrees != 0f || needsFlip) {
                // Decode bitmap with sampling to prevent OOM
                val options = BitmapFactory.Options().apply {
                    // First decode with inJustDecodeBounds to get dimensions
                    inJustDecodeBounds = true
                    BitmapFactory.decodeFile(tempFile.absolutePath, this)
                    
                    // Calculate sample size for reasonable image size (max 2048px)
                    val maxDimension = 2048
                    val maxDim = maxOf(outWidth, outHeight)
                    inSampleSize = if (maxDim > maxDimension) {
                        Math.ceil(maxDim.toDouble() / maxDimension).toInt()
                    } else {
                        1
                    }
                    
                    // Now decode with sample size
                    inJustDecodeBounds = false
                }
                
                val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)
                if (bitmap != null) {
                    try {
                        val matrix = Matrix()
                        
                        // Handle rotation
                        if (rotationDegrees != 0f) {
                            matrix.postRotate(rotationDegrees)
                        }
                        
                        // Handle flips
                        when (orientation) {
                            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                            ExifInterface.ORIENTATION_TRANSPOSE -> {
                                matrix.postRotate(90f)
                                matrix.postScale(-1f, 1f)
                            }
                            ExifInterface.ORIENTATION_TRANSVERSE -> {
                                matrix.postRotate(270f)
                                matrix.postScale(-1f, 1f)
                            }
                        }
                        
                        // Create rotated/flipped bitmap
                        val rotatedBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                        
                        // Save to destination (as normal orientation, no EXIF rotation needed)
                        FileOutputStream(destFile).use { outputStream ->
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        }
                        
                        // Recycle bitmaps
                        if (rotatedBitmap != bitmap) {
                            rotatedBitmap.recycle()
                        }
                        bitmap.recycle()
                        
                        // Delete temp file
                        tempFile.delete()
                        return
                    } catch (oom: OutOfMemoryError) {
                        android.util.Log.e("ProjectViewModel", "OOM while processing image, using fallback", oom)
                        // Try to recycle the bitmap
                        bitmap.recycle()
                        // Fallback: just copy the file as-is without transformation
                        tempFile.copyTo(destFile, overwrite = true)
                        tempFile.delete()
                    }
                }
            }
            
            // If no rotation needed or bitmap decode failed, just copy the file
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
            
        } catch (oom: OutOfMemoryError) {
            android.util.Log.e("ProjectViewModel", "Out of memory processing photo", oom)
            // Fallback: just copy the file as-is
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProjectViewModel", "Fallback copy also failed", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProjectViewModel", "Error handling photo orientation", e)
            // Fallback: just copy the file as-is
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }
    
    // Export operations
    // Export as HTML report in ZIP (for viewing on PC)
    fun exportProjectForPC(outputFile: java.io.File, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val project = _uiState.value.project ?: return@launch
                val languageCode = AppPreferences.getLanguage(context)
                val result = exportManager.exportForPCToZip(project, outputFile, languageCode)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = result.success,
                    exportMessage = if (result.success) "Export successful" else result.error
                )
                onComplete(result.success, result.error)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = false,
                    exportMessage = e.message
                )
                onComplete(false, e.message)
            }
        }
    }
    
    // Export as JSON in ZIP (for sharing/importing on another device)
    fun exportProjectToZip(outputFile: java.io.File, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val project = _uiState.value.project ?: return@launch
                val result = exportManager.exportToZip(project, outputFile)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = result.success,
                    exportMessage = if (result.success) "Export successful" else result.error
                )
                onComplete(result.success, result.error)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = false,
                    exportMessage = e.message
                )
                onComplete(false, e.message)
            }
        }
    }
    
    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(
            exportSuccess = false,
            exportMessage = null
        )
    }
}
