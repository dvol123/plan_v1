package com.plan.app.presentation.viewmodel

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
class ProjectViewModel @Inject constructor(
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
        _uiState.value = _uiState.value.copy(isViewMode = newMode)
        gridManager.setEditingMode(!newMode)
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
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { it.id }
                .toSet()
            _uiState.value = _uiState.value.copy(highlightedRegionIds = matchingIds)
        } else {
            _uiState.value = _uiState.value.copy(highlightedRegionIds = emptySet())
        }
    }
    
    // Grid operations
    fun onCellDoubleTap(cell: Cell) {
        gridManager.startNewSelection(cell)
        _uiState.value = _uiState.value.copy(showCreateRegionDialog = true)
    }
    
    fun onCellSingleTap(cell: Cell) {
        gridManager.toggleCellSelection(cell)
    }
    
    fun clearSelection() {
        gridManager.clearSelection()
    }
    
    fun setCellSize(size: Int) {
        gridManager.setCellSize(size)
        // Hide grid controls after user confirms a size (if size > 1, it means user changed it)
        if (size > 1) {
            _uiState.value = _uiState.value.copy(showGridSizeControls = false)
        }
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
    
    private fun copyMediaToPermanentStorage(context: Context, sourceUri: Uri, regionId: Long, type: String): String {
        // Create directory for media files
        val mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }
        
        // Create unique filename
        val timestamp = System.currentTimeMillis()
        val extension = if (type == "photo") "jpg" else "mp4"
        val fileName = "${type}_${regionId}_$timestamp.$extension"
        val destFile = File(mediaDir, fileName)
        
        // Copy from source to destination
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        return destFile.absolutePath
    }
    
    // Export operations
    // Export as HTML report in ZIP (for viewing on PC)
    fun exportProjectForPC(outputFile: java.io.File, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val project = _uiState.value.project ?: return@launch
                val result = exportManager.exportForPCToZip(project, outputFile)
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
