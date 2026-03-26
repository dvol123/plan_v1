package com.plan.app

import android.app.Application
import android.graphics.Color
import com.plan.app.data.local.dao.StateDao
import com.plan.app.data.local.entity.StateEntity
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Application class for the Plan app.
 * Initializes Hilt dependency injection and pre-populates database.
 */
@HiltAndroidApp
class PlanApplication : Application() {
    
    @Inject
    lateinit var stateDao: StateDao
    
    private val applicationScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        cleanOldCacheFiles()
        prepopulateStates()
    }
    
    /**
     * Clean old temporary files from cache to prevent storage bloat.
     * This is called on app startup to clean up files from previous sessions.
     */
    private fun cleanOldCacheFiles() {
        applicationScope.launch {
            try {
                val cacheDir = this@PlanApplication.cacheDir
                val now = System.currentTimeMillis()
                val oneHourMs = 60 * 60 * 1000L
                
                // Clean old temporary files (older than 1 hour)
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        // Delete old temp files
                        if (file.lastModified() < now - oneHourMs) {
                            val name = file.name.lowercase()
                            // Only delete known temp file patterns
                            if (name.startsWith("temp_") || 
                                name.startsWith("export_") || 
                                name.startsWith("import_") ||
                                name.startsWith("camera_") ||
                                name.startsWith("photo_") ||
                                name.startsWith("video_")) {
                                file.delete()
                            }
                        }
                    } else if (file.isDirectory) {
                        // Clean export directories
                        if (file.name.startsWith("export_")) {
                            file.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlanApplication", "Error cleaning cache", e)
            }
        }
    }
    
    private fun prepopulateStates() {
        applicationScope.launch {
            // Check if states already exist
            val existingStates = stateDao.getAllStatesOnce()
            if (existingStates.isEmpty()) {
                // Pre-populate with 7 classic colors: red, orange, yellow, green, cyan, blue, violet
                val defaultStates = listOf(
                    StateEntity(name = "Red", color = Color.RED),
                    StateEntity(name = "Orange", color = Color.parseColor("#FFA500")),
                    StateEntity(name = "Yellow", color = Color.YELLOW),
                    StateEntity(name = "Green", color = Color.GREEN),
                    StateEntity(name = "Cyan", color = Color.CYAN),
                    StateEntity(name = "Blue", color = Color.BLUE),
                    StateEntity(name = "Violet", color = Color.parseColor("#8A2BE2"))
                )
                stateDao.insertStates(defaultStates)
            }
        }
    }
}
