package com.plan.app.data.repository

import com.plan.app.data.local.dao.ProjectDao
import com.plan.app.data.local.entity.ProjectEntity
import com.plan.app.domain.model.Project
import com.plan.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ProjectRepository.
 */
@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao
) : ProjectRepository {
    
    override fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getProjectById(projectId: Long): Project? {
        return projectDao.getProjectById(projectId)?.toDomain()
    }
    
    override fun searchProjects(query: String): Flow<List<Project>> {
        return projectDao.searchProjects(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun insertProject(project: Project): Long {
        return projectDao.insertProject(project.toEntity())
    }
    
    override suspend fun updateProject(project: Project) {
        projectDao.updateProject(project.toEntity())
    }
    
    override suspend fun deleteProject(project: Project) {
        projectDao.deleteProject(project.toEntity())
    }
    
    override suspend fun deleteProjectById(projectId: Long) {
        projectDao.deleteProjectById(projectId)
    }
    
    override suspend fun getAllProjectsOnce(): List<Project> {
        return projectDao.getAllProjectsOnce().map { it.toDomain() }
    }
    
    override suspend fun insert(project: Project): Long {
        return projectDao.insertProject(project.toEntity())
    }
    
    private fun ProjectEntity.toDomain(): Project {
        return Project(
            id = id,
            name = name,
            photoUri = photoUri,
            type1 = type1,
            type2 = type2,
            description = description,
            note = note,
            cellSize = cellSize,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    private fun Project.toEntity(): ProjectEntity {
        return ProjectEntity(
            id = id,
            name = name,
            photoUri = photoUri,
            type1 = type1,
            type2 = type2,
            description = description,
            note = note,
            cellSize = cellSize,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
