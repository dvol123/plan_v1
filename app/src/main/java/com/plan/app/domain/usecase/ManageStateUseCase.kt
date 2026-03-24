package com.plan.app.domain.usecase

import com.plan.app.domain.model.State
import com.plan.app.domain.repository.StateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for managing states.
 */
@Singleton
class ManageStateUseCase @Inject constructor(
    private val stateRepository: StateRepository
) {
    fun getAll(): Flow<List<State>> {
        return stateRepository.getAllStates()
    }
    
    suspend fun getAllOnce(): List<State> {
        return stateRepository.getAllStatesOnce()
    }
    
    suspend fun getById(stateId: Long): State? {
        return stateRepository.getStateById(stateId)
    }
    
    suspend fun getOrCreate(name: String, color: Int): State {
        val existing = stateRepository.getStateByNameAndColor(name, color)
        if (existing != null) return existing
        
        val id = stateRepository.insertState(State(name = name, color = color))
        return State(id = id, name = name, color = color)
    }
    
    suspend fun create(state: State): Long {
        return stateRepository.insertState(state)
    }
}
