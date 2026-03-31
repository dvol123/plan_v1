package com.plan.app.domain.usecase

import com.plan.app.domain.model.Region
import com.plan.app.domain.repository.ContentRepository
import com.plan.app.domain.repository.RegionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for managing a single region.
 */
@Singleton
class ManageRegionUseCase @Inject constructor(
    private val regionRepository: RegionRepository,
    private val contentRepository: ContentRepository
) {
    suspend fun getById(regionId: Long): Region? {
        return regionRepository.getRegionById(regionId)
    }
    
    suspend fun create(region: Region): Long {
        return regionRepository.insertRegion(region)
    }
    
    suspend fun update(region: Region) {
        regionRepository.updateRegion(region)
    }
    
    suspend fun delete(region: Region) {
        contentRepository.deleteContentsByRegion(region.id)
        regionRepository.deleteRegion(region)
    }
    
    suspend fun deleteAllByProject(projectId: Long) {
        regionRepository.deleteRegionsByProject(projectId)
    }
}
