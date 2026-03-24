package com.plan.app.data.repository

import com.plan.app.data.local.dao.StateDao
import com.plan.app.data.local.entity.StateEntity
import com.plan.app.domain.model.State
import com.plan.app.domain.repository.StateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of StateRepository.
 */
@Singleton
class StateRepositoryImpl @Inject constructor(
    private val stateDao: StateDao
) : StateRepository {
    
    override fun getAllStates(): Flow<List<State>> {
        return stateDao.getAllStates().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getAllStatesOnce(): List<State> {
        return stateDao.getAllStatesOnce().map { it.toDomain() }
    }
    
    override suspend fun getStateById(stateId: Long): State? {
        return stateDao.getStateById(stateId)?.toDomain()
    }
    
    override suspend fun getStateByNameAndColor(name: String, color: Int): State? {
        return stateDao.getStateByNameAndColor(name, color)?.toDomain()
    }
    
    override suspend fun insertState(state: State): Long {
        return stateDao.insertState(state.toEntity())
    }
    
    override suspend fun insertStates(states: List<State>) {
        stateDao.insertStates(states.map { it.toEntity() })
    }
    
    override suspend fun updateState(state: State) {
        stateDao.updateState(state.toEntity())
    }
    
    override suspend fun deleteState(state: State) {
        stateDao.deleteState(state.toEntity())
    }
    
    private fun StateEntity.toDomain(): State {
        return State(id = id, name = name, color = color)
    }
    
    private fun State.toEntity(): StateEntity {
        return StateEntity(id = id, name = name, color = color)
    }
}
