package com.plan.app.domain.usecase

import com.plan.app.domain.model.Project
import com.plan.app.domain.repository.ProjectRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for managing a single project.
 */
@Singleton
class ManageProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository
) {
    suspend fun getById(projectId: Long): Project? {
        return projectRepository.getProjectById(projectId)
    }
    
    suspend fun create(project: Project): Long {
        return projectRepository.insertProject(project)
    }
    
    suspend fun update(project: Project) {
        projectRepository.updateProject(project)
    }
    
    suspend fun delete(project: Project) {
        projectRepository.deleteProject(project)
    }
    
    suspend fun deleteById(projectId: Long) {
        projectRepository.deleteProjectById(projectId)
    }
}
