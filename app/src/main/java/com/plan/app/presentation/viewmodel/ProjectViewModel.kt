package com.plan.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plan.app.domain.manager.ExportManager
import com.plan.app.domain.manager.GridManager
import com.plan.app.domain.manager.ProjectManager
import com.plan.app.domain.model.Cell
import com.plan.app.domain.model.Content
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
    val exportMessage: String? = null
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
                    isLoading = false
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
        _uiState.value = _uiState.value.copy(
            selectedRegion = region,
            showRegionCard = region != null,
            isEditingRegion = false
        )
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
                    isEditingRegion = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
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
    
    // Export operations
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
    
    fun exportProjectForPC(outputDir: java.io.File, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val project = _uiState.value.project ?: return@launch
                val result = exportManager.exportForPC(project, outputDir)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = result,
                    exportMessage = if (result) "Export successful" else "Export failed"
                )
                onComplete(result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = false,
                    exportMessage = e.message
                )
                onComplete(false)
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
