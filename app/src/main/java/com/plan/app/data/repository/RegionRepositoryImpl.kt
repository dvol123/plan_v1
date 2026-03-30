package com.plan.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plan.app.data.local.dao.ContentDao
import com.plan.app.data.local.dao.RegionDao
import com.plan.app.data.local.entity.ContentEntity
import com.plan.app.data.local.entity.RegionEntity
import com.plan.app.domain.model.Cell
import com.plan.app.domain.model.Content
import com.plan.app.domain.model.ContentType
import com.plan.app.domain.model.Region
import com.plan.app.domain.repository.RegionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of RegionRepository.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class RegionRepositoryImpl @Inject constructor(
    private val regionDao: RegionDao,
    private val contentDao: ContentDao,
    private val gson: Gson
) : RegionRepository {
    
    override fun getRegionsByProject(projectId: Long): Flow<List<Region>> {
        return regionDao.getRegionsByProject(projectId).mapLatest { entities ->
            entities.map { entity -> entity.toDomainWithContents() }
        }
    }
    
    override suspend fun getRegionsByProjectOnce(projectId: Long): List<Region> {
        return regionDao.getRegionsByProjectOnce(projectId).map { entity -> 
            entity.toDomainWithContents() 
        }
    }
    
    override suspend fun getRegionById(regionId: Long): Region? {
        return regionDao.getRegionById(regionId)?.toDomainWithContents()
    }
    
    override fun searchRegions(projectId: Long, query: String): Flow<List<Region>> {
        return regionDao.searchRegions(projectId, query).mapLatest { entities ->
            entities.map { entity -> entity.toDomainWithContents() }
        }
    }
    
    override suspend fun insertRegion(region: Region): Long {
        return regionDao.insertRegion(region.toEntity())
    }
    
    override suspend fun updateRegion(region: Region) {
        regionDao.updateRegion(region.toEntity())
    }
    
    override suspend fun deleteRegion(region: Region) {
        regionDao.deleteRegion(region.toEntity())
    }
    
    override suspend fun deleteRegionsByProject(projectId: Long) {
        regionDao.deleteRegionsByProject(projectId)
    }
    
    private suspend fun RegionEntity.toDomainWithContents(): Region {
        val cellList: List<Cell> = try {
            gson.fromJson(cellsJson, object : TypeToken<List<Cell>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        // Load contents for this region
        val contentEntities = contentDao.getContentsByRegionOnce(id)
        android.util.Log.d("RegionRepository", "Loaded ${contentEntities.size} contents for region $id")
        val contents = contentEntities.map { it.toDomain() }
        
        return Region(
            id = id,
            projectId = projectId,
            name = name,
            stateId = stateId,
            type1 = type1,
            type2 = type2,
            description = description,
            note = note,
            cells = cellList,
            contents = contents,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    private fun Region.toEntity(): RegionEntity {
        return RegionEntity(
            id = id,
            projectId = projectId,
            name = name,
            stateId = stateId,
            type1 = type1,
            type2 = type2,
            description = description,
            note = note,
            cellsJson = gson.toJson(cells),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    // Extension to convert ContentEntity to Content domain model
    private fun ContentEntity.toDomain(): Content {
        return Content(
            id = id,
            regionId = regionId,
            type = when (type) {
                com.plan.app.data.local.entity.ContentType.TEXT -> ContentType.TEXT
                com.plan.app.data.local.entity.ContentType.PHOTO -> ContentType.PHOTO
                com.plan.app.data.local.entity.ContentType.VIDEO -> ContentType.VIDEO
            },
            data = data,
            sortOrder = sortOrder,
            createdAt = createdAt
        )
    }
}
