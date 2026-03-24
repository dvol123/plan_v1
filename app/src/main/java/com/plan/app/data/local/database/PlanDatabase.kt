package com.plan.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.plan.app.data.local.dao.*
import com.plan.app.data.local.entity.*

/**
 * Room Database for the Plan application.
 */
@Database(
    entities = [
        ProjectEntity::class,
        StateEntity::class,
        RegionEntity::class,
        ContentEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PlanDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun stateDao(): StateDao
    abstract fun regionDao(): RegionDao
    abstract fun contentDao(): ContentDao
}
