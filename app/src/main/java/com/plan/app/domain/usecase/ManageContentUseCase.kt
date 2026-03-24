package com.plan.app.domain.usecase

import com.plan.app.domain.model.Content
import com.plan.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for managing content.
 */
@Singleton
class ManageContentUseCase @Inject constructor(
    private val contentRepository: ContentRepository
) {
    fun getByRegion(regionId: Long): Flow<List<Content>> {
        return contentRepository.getContentsByRegion(regionId)
    }
    
    suspend fun getByRegionOnce(regionId: Long): List<Content> {
        return contentRepository.getContentsByRegionOnce(regionId)
    }
    
    suspend fun add(content: Content): Long {
        return contentRepository.insertContent(content)
    }
    
    suspend fun addAll(contents: List<Content>) {
        contentRepository.insertContents(contents)
    }
    
    suspend fun delete(content: Content) {
        contentRepository.deleteContent(content)
    }
    
    suspend fun deleteByRegion(regionId: Long) {
        contentRepository.deleteContentsByRegion(regionId)
    }
}
