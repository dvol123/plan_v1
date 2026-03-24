package com.plan.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plan.app.domain.manager.ProjectManager
import com.plan.app.domain.model.Project
import com.plan.app.domain.usecase.GetProjectsUseCase
import com.plan.app.domain.usecase.ManageProjectUseCase
import com.plan.app.domain.usecase.ManageRegionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Main Screen.
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val selectedProject: Project? = null,
    val searchQuery: String = "",
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showExportDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for Main Screen.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getProjectsUseCase: GetProjectsUseCase,
    private val manageProjectUseCase: ManageProjectUseCase,
    private val manageRegionUseCase: ManageRegionUseCase,
    private val projectManager: ProjectManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    val projects: StateFlow<List<Project>> = getProjectsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    init {
        initializeDatabase()
    }
    
    private fun initializeDatabase() {
        // Database is initialized via Hilt
    }
    
    fun selectProject(project: Project?) {
        _uiState.value = _uiState.value.copy(selectedProject = project)
    }
    
    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
    
    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }
    
    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }
    
    fun showEditDialog() {
        _uiState.value = _uiState.value.copy(showEditDialog = true)
    }
    
    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(showEditDialog = false)
    }
    
    fun showDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }
    
    fun hideDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }
    
    fun showExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = true)
    }
    
    fun hideExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = false)
    }
    
    fun showSettingsDialog() {
        _uiState.value = _uiState.value.copy(showSettingsDialog = true)
    }
    
    fun hideSettingsDialog() {
        _uiState.value = _uiState.value.copy(showSettingsDialog = false)
    }
    
    fun createProject(project: Project) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val projectId = manageProjectUseCase.create(project)
                projectManager.setCurrentProject(project.copy(id = projectId))
                hideCreateDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun updateProject(project: Project) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                manageProjectUseCase.update(project)
                selectProject(project)
                hideEditDialog()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun deleteProject() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                _uiState.value.selectedProject?.let { project ->
                    manageRegionUseCase.deleteAllByProject(project.id)
                    manageProjectUseCase.delete(project)
                    selectProject(null)
                }
                hideDeleteConfirm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
