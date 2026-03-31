package com.plan.app.domain.usecase

import com.plan.app.domain.model.Project
import com.plan.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for getting all projects.
 */
@Singleton
class GetProjectsUseCase @Inject constructor(
    private val projectRepository: ProjectRepository
) {
    operator fun invoke(): Flow<List<Project>> {
        return projectRepository.getAllProjects()
    }
}
