package com.plan.app.data.local.dao

import androidx.room.*
import com.plan.app.data.local.entity.StateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for State operations.
 */
@Dao
interface StateDao {
    
    @Query("SELECT * FROM states ORDER BY name ASC")
    fun getAllStates(): Flow<List<StateEntity>>
    
    @Query("SELECT * FROM states")
    suspend fun getAllStatesOnce(): List<StateEntity>
    
    @Query("SELECT * FROM states WHERE id = :stateId")
    suspend fun getStateById(stateId: Long): StateEntity?
    
    @Query("SELECT * FROM states WHERE name = :name AND color = :color LIMIT 1")
    suspend fun getStateByNameAndColor(name: String, color: Int): StateEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: StateEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStates(states: List<StateEntity>)
    
    @Update
    suspend fun updateState(state: StateEntity)
    
    @Delete
    suspend fun deleteState(state: StateEntity)
}
