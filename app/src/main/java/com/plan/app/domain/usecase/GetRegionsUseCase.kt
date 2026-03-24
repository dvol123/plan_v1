package com.plan.app.domain.usecase

import com.plan.app.domain.model.Region
import com.plan.app.domain.repository.RegionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for getting regions by project.
 */
@Singleton
class GetRegionsUseCase @Inject constructor(
    private val regionRepository: RegionRepository
) {
    operator fun invoke(projectId: Long): Flow<List<Region>> {
        return regionRepository.getRegionsByProject(projectId)
    }
    
    suspend fun getOnce(projectId: Long): List<Region> {
        return regionRepository.getRegionsByProjectOnce(projectId)
    }
}
