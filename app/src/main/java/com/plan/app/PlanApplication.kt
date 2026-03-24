package com.plan.app

import android.app.Application
import android.graphics.Color
import com.plan.app.data.local.dao.StateDao
import com.plan.app.data.local.entity.StateEntity
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        prepopulateStates()
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
