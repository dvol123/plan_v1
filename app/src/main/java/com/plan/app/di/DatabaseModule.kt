package com.plan.app.di

import android.content.Context
import androidx.room.Room
import com.plan.app.data.local.dao.*
import com.plan.app.data.local.database.PlanDatabase
import com.plan.app.data.repository.*
import com.plan.app.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module for providing database and repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): PlanDatabase {
        return Room.databaseBuilder(
            context,
            PlanDatabase::class.java,
            "plan_database"
        ).build()
    }
    
    @Provides
    fun provideProjectDao(database: PlanDatabase): ProjectDao {
        return database.projectDao()
    }
    
    @Provides
    fun provideStateDao(database: PlanDatabase): StateDao {
        return database.stateDao()
    }
    
    @Provides
    fun provideRegionDao(database: PlanDatabase): RegionDao {
        return database.regionDao()
    }
    
    @Provides
    fun provideContentDao(database: PlanDatabase): ContentDao {
        return database.contentDao()
    }
    
    @Provides
    @Singleton
    fun provideProjectRepository(projectDao: ProjectDao): ProjectRepository {
        return ProjectRepositoryImpl(projectDao)
    }
    
    @Provides
    @Singleton
    fun provideStateRepository(stateDao: StateDao): StateRepository {
        return StateRepositoryImpl(stateDao)
    }
    
    @Provides
    @Singleton
    fun provideRegionRepository(
        regionDao: RegionDao,
        contentDao: ContentDao,
        gson: com.google.gson.Gson
    ): RegionRepository {
        return RegionRepositoryImpl(regionDao, contentDao, gson)
    }
    
    @Provides
    @Singleton
    fun provideContentRepository(contentDao: ContentDao): ContentRepository {
        return ContentRepositoryImpl(contentDao)
    }
    
    @Provides
    @Singleton
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }
}
