package com.plan.app.data.local.dao

import androidx.room.*
import com.plan.app.data.local.entity.ContentEntity
import com.plan.app.data.local.entity.ContentType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Content operations.
 */
@Dao
interface ContentDao {
    
    @Query("SELECT * FROM contents WHERE regionId = :regionId ORDER BY sortOrder ASC")
    fun getContentsByRegion(regionId: Long): Flow<List<ContentEntity>>
    
    @Query("SELECT * FROM contents WHERE regionId = :regionId ORDER BY sortOrder ASC")
    suspend fun getContentsByRegionOnce(regionId: Long): List<ContentEntity>
    
    @Query("SELECT * FROM contents WHERE regionId = :regionId AND type = :type ORDER BY sortOrder ASC")
    suspend fun getContentsByType(regionId: Long, type: ContentType): List<ContentEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: ContentEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContents(contents: List<ContentEntity>)
    
    @Update
    suspend fun updateContent(content: ContentEntity)
    
    @Delete
    suspend fun deleteContent(content: ContentEntity)
    
    @Query("DELETE FROM contents WHERE id = :contentId")
    suspend fun deleteContentById(contentId: Long)
    
    @Query("DELETE FROM contents WHERE regionId = :regionId")
    suspend fun deleteContentsByRegion(regionId: Long)
    
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM contents WHERE regionId = :regionId")
    suspend fun getNextSortOrder(regionId: Long): Int
}
