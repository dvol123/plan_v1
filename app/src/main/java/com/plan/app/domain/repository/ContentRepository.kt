package com.plan.app.domain.repository

import com.plan.app.domain.model.Content
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Content operations.
 */
interface ContentRepository {
    fun getContentsByRegion(regionId: Long): Flow<List<Content>>
    suspend fun getContentsByRegionOnce(regionId: Long): List<Content>
    suspend fun insertContent(content: Content): Long
    suspend fun insertContents(contents: List<Content>)
    suspend fun insert(content: Content): Long
    suspend fun deleteContent(content: Content)
    suspend fun deleteContentsByRegion(regionId: Long)
}
