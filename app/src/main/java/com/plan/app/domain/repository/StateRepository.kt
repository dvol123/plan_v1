package com.plan.app.domain.repository

import com.plan.app.domain.model.State
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for State operations.
 */
interface StateRepository {
    fun getAllStates(): Flow<List<State>>
    suspend fun getAllStatesOnce(): List<State>
    suspend fun getStateById(stateId: Long): State?
    suspend fun getStateByNameAndColor(name: String, color: Int): State?
    suspend fun getOrCreate(name: String, color: Int): State
    suspend fun insertState(state: State): Long
    suspend fun insertStates(states: List<State>)
    suspend fun updateState(state: State)
    suspend fun deleteState(state: State)
}
