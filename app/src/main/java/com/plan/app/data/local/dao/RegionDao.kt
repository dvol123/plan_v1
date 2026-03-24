package com.plan.app.data.local.dao

import androidx.room.*
import com.plan.app.data.local.entity.RegionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Region operations.
 */
@Dao
interface RegionDao {
    
    @Query("SELECT * FROM regions WHERE projectId = :projectId ORDER BY name ASC")
    fun getRegionsByProject(projectId: Long): Flow<List<RegionEntity>>
    
    @Query("SELECT * FROM regions WHERE projectId = :projectId")
    suspend fun getRegionsByProjectOnce(projectId: Long): List<RegionEntity>
    
    @Query("SELECT * FROM regions WHERE id = :regionId")
    suspend fun getRegionById(regionId: Long): RegionEntity?
    
    @Query("SELECT * FROM regions WHERE projectId = :projectId AND name LIKE '%' || :searchQuery || '%'")
    fun searchRegions(projectId: Long, searchQuery: String): Flow<List<RegionEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: RegionEntity): Long
    
    @Update
    suspend fun updateRegion(region: RegionEntity)
    
    @Delete
    suspend fun deleteRegion(region: RegionEntity)
    
    @Query("DELETE FROM regions WHERE id = :regionId")
    suspend fun deleteRegionById(regionId: Long)
    
    @Query("DELETE FROM regions WHERE projectId = :projectId")
    suspend fun deleteRegionsByProject(projectId: Long)
    
    @Query("UPDATE regions SET updatedAt = :updatedAt WHERE id = :regionId")
    suspend fun updateTimestamp(regionId: Long, updatedAt: Long = System.currentTimeMillis())
}
