package com.plan.app.data.local.dao

import androidx.room.*
import com.plan.app.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Project operations.
 */
@Dao
interface ProjectDao {
    
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>
    
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): ProjectEntity?
    
    @Query("SELECT * FROM projects WHERE name LIKE '%' || :searchQuery || '%' ORDER BY updatedAt DESC")
    fun searchProjects(searchQuery: String): Flow<List<ProjectEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long
    
    @Update
    suspend fun updateProject(project: ProjectEntity)
    
    @Delete
    suspend fun deleteProject(project: ProjectEntity)
    
    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)
    
    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateTimestamp(projectId: Long, updatedAt: Long = System.currentTimeMillis())
}
