package com.plan.app.domain.repository

import com.plan.app.domain.model.Region
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Region operations.
 */
interface RegionRepository {
    fun getRegionsByProject(projectId: Long): Flow<List<Region>>
    suspend fun getRegionsByProjectOnce(projectId: Long): List<Region>
    suspend fun getRegionById(regionId: Long): Region?
    fun searchRegions(projectId: Long, query: String): Flow<List<Region>>
    suspend fun insertRegion(region: Region): Long
    suspend fun updateRegion(region: Region)
    suspend fun deleteRegion(region: Region)
    suspend fun deleteRegionsByProject(projectId: Long)
}
