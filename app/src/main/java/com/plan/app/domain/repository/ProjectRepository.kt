package com.plan.app.domain.repository

import com.plan.app.domain.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Project operations.
 */
interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    suspend fun getProjectById(projectId: Long): Project?
    fun searchProjects(query: String): Flow<List<Project>>
    suspend fun insertProject(project: Project): Long
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(project: Project)
    suspend fun deleteProjectById(projectId: Long)
}
