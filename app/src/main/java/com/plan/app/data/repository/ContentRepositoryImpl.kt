package com.plan.app.data.repository

import com.plan.app.data.local.dao.ContentDao
import com.plan.app.data.local.entity.ContentEntity
import com.plan.app.domain.model.Content
import com.plan.app.domain.model.ContentType
import com.plan.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ContentRepository.
 */
@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val contentDao: ContentDao
) : ContentRepository {
    
    override fun getContentsByRegion(regionId: Long): Flow<List<Content>> {
        return contentDao.getContentsByRegion(regionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getContentsByRegionOnce(regionId: Long): List<Content> {
        return contentDao.getContentsByRegionOnce(regionId).map { it.toDomain() }
    }
    
    override suspend fun insertContent(content: Content): Long {
        return contentDao.insertContent(content.toEntity())
    }
    
    override suspend fun insertContents(contents: List<Content>) {
        contentDao.insertContents(contents.map { it.toEntity() })
    }

    override suspend fun insert(content: Content): Long {
        return contentDao.insertContent(content.toEntity())
    }

    override suspend fun deleteContent(content: Content) {
        contentDao.deleteContent(content.toEntity())
    }
    
    override suspend fun deleteContentsByRegion(regionId: Long) {
        contentDao.deleteContentsByRegion(regionId)
    }
    
    private fun ContentEntity.toDomain(): Content {
        return Content(
            id = id,
            regionId = regionId,
            type = when (type) {
                com.plan.app.data.local.entity.ContentType.TEXT -> ContentType.TEXT
                com.plan.app.data.local.entity.ContentType.PHOTO -> ContentType.PHOTO
                com.plan.app.data.local.entity.ContentType.VIDEO -> ContentType.VIDEO
                com.plan.app.data.local.entity.ContentType.FILE -> ContentType.FILE
            },
            data = data,
            sortOrder = sortOrder,
            createdAt = createdAt
        )
    }
    
    private fun Content.toEntity(): ContentEntity {
        return ContentEntity(
            id = id,
            regionId = regionId,
            type = when (type) {
                ContentType.TEXT -> com.plan.app.data.local.entity.ContentType.TEXT
                ContentType.PHOTO -> com.plan.app.data.local.entity.ContentType.PHOTO
                ContentType.VIDEO -> com.plan.app.data.local.entity.ContentType.VIDEO
                ContentType.FILE -> com.plan.app.data.local.entity.ContentType.FILE
            },
            data = data,
            sortOrder = sortOrder,
            createdAt = createdAt
        )
    }
}
