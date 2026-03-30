package com.plan.app.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plan.app.domain.manager.ExportManager
import com.plan.app.domain.manager.ProjectManager
import com.plan.app.domain.model.Project
import com.plan.app.domain.usecase.GetProjectsUseCase
import com.plan.app.domain.usecase.ManageProjectUseCase
import com.plan.app.domain.usecase.ManageRegionUseCase
import com.plan.app.presentation.ui.components.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
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
    val showDeleteAllConfirm: Boolean = false,
    val showExportDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val errorMessage: String? = null,
    val exportSuccess: Boolean = false,
    val exportMessage: String? = null,
    val importSuccess: Boolean = false,
    val importMessage: String? = null
)

/**
 * ViewModel for Main Screen.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getProjectsUseCase: GetProjectsUseCase,
    private val manageProjectUseCase: ManageProjectUseCase,
    private val manageRegionUseCase: ManageRegionUseCase,
    private val projectManager: ProjectManager,
    private val exportManager: ExportManager
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
    
    fun toggleProjectSelection(project: Project) {
        // If clicking on already selected project, deselect it
        if (_uiState.value.selectedProject?.id == project.id) {
            _uiState.value = _uiState.value.copy(selectedProject = null)
        } else {
            _uiState.value = _uiState.value.copy(selectedProject = project)
        }
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
    
    fun deleteAllProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Delete all regions first, then all projects
                val allProjects = projects.value
                for (project in allProjects) {
                    manageRegionUseCase.deleteAllByProject(project.id)
                    manageProjectUseCase.delete(project)
                }
                selectProject(null)
                hideDeleteAllConfirm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun showDeleteAllConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteAllConfirm = true)
    }
    
    fun hideDeleteAllConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteAllConfirm = false)
    }
    
    // Export selected project to ZIP (for sharing - JSON format)
    fun exportProjectToZip(_context: Context, outputFile: File, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val project = _uiState.value.selectedProject
                if (project == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        exportSuccess = false,
                        exportMessage = "No project selected"
                    )
                    onComplete(false, "No project selected")
                    return@launch
                }
                
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
    
    // Export project as HTML report in ZIP (for viewing on PC)
    fun exportProjectForPC(context: Context, outputFile: File, project: Project? = null, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val targetProject = project ?: _uiState.value.selectedProject
                if (targetProject == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        exportSuccess = false,
                        exportMessage = "No project selected"
                    )
                    onComplete(false, "No project selected")
                    return@launch
                }
                
                val languageCode = AppPreferences.getLanguage(context)
                val result = exportManager.exportForPCToZip(targetProject, outputFile, languageCode)
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
    
    // Export all projects to ZIP (JSON format for sharing)
    fun exportAllProjects(outputFile: File, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = exportManager.exportAllProjects(outputFile)
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
    
    // Export all projects to ZIP (for sharing)
    fun exportAllProjectsToZip(outputFile: File, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = exportManager.exportAllProjects(outputFile)
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
    
    // Export all projects as HTML reports in ZIP (for viewing on PC)
    fun exportAllProjectsForPC(context: Context, outputFile: File, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val languageCode = AppPreferences.getLanguage(context)
                val result = exportManager.exportAllProjectsForPC(outputFile, languageCode)
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
    
    // Import project from ZIP
    fun importProjectFromZip(zipUri: Uri, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = exportManager.importFromZip(zipUri)
                val message = if (result.success) {
                    if (result.importedCount > 1) {
                        "Imported ${result.importedCount} projects"
                    } else {
                        "Import successful"
                    }
                } else result.error
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importSuccess = result.success,
                    importMessage = message
                )
                onComplete(result.success, message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importSuccess = false,
                    importMessage = e.message
                )
                onComplete(false, e.message)
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(
            exportSuccess = false,
            exportMessage = null
        )
    }
    
    fun clearImportMessage() {
        _uiState.value = _uiState.value.copy(
            importSuccess = false,
            importMessage = null
        )
    }
}
