package com.plan.app.di

import android.content.Context
import com.plan.app.domain.model.State
import com.plan.app.domain.repository.StateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * Hilt Module for application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
}

/**
 * Initializer for pre-populating the database with default states.
 */
object DatabaseInitializer {
    
    suspend fun initializeStates(stateRepository: StateRepository) {
        val existingStates = stateRepository.getAllStatesOnce()
        if (existingStates.isEmpty()) {
            val defaultStates = State.PREDEFINED_COLORS.mapIndexed { index, color ->
                State(
                    name = State.PREDEFINED_STATE_NAMES[index],
                    color = color
                )
            }
            stateRepository.insertStates(defaultStates)
        }
    }
}
