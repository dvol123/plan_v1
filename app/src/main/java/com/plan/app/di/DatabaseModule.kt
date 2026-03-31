package com.plan.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add originalFileName column to contents table
            db.execSQL("ALTER TABLE contents ADD COLUMN originalFileName TEXT")
        }
    }
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): PlanDatabase {
        return Room.databaseBuilder(
            context,
            PlanDatabase::class.java,
            "plan_database"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
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
