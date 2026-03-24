package com.plan.app.domain.manager

import com.plan.app.domain.model.Project
import com.plan.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for centralized project state management.
 */
@Singleton
class ProjectManager @Inject constructor(
    private val projectRepository: ProjectRepository
) {
    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()
    
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()
    
    fun setCurrentProject(project: Project?) {
        _currentProject.value = project
        _hasUnsavedChanges.value = false
    }
    
    fun setUnsavedChanges(hasChanges: Boolean) {
        _hasUnsavedChanges.value = hasChanges
    }
    
    suspend fun refreshCurrentProject() {
        _currentProject.value?.let { project ->
            _currentProject.value = projectRepository.getProjectById(project.id)
        }
    }
    
    fun clearCurrentProject() {
        _currentProject.value = null
        _hasUnsavedChanges.value = false
    }
}
