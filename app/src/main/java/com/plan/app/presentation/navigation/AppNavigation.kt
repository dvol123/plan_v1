package com.plan.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.plan.app.presentation.ui.main.MainScreen
import com.plan.app.presentation.ui.project.ProjectScreen

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Project : Screen("project/{projectId}") {
        fun createRoute(projectId: Long) = "project/$projectId"
    }
}

/**
 * Simple throttle to prevent rapid clicks - no coroutines needed
 */
private class ClickThrottle(private val minDelayMs: Long = 500) {
    private var lastClickTime: Long = 0
    
    fun canClick(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < minDelayMs) {
            return false
        }
        lastClickTime = now
        return true
    }
}

/**
 * Main navigation host.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onThemeChanged: (Int) -> Unit = {}
) {
    // Simple throttle to prevent rapid clicks - survives recomposition
    val clickThrottle = remember { ClickThrottle() }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onProjectClick = { projectId ->
                    if (clickThrottle.canClick()) {
                        navController.navigate(Screen.Project.createRoute(projectId))
                    }
                },
                onThemeChanged = onThemeChanged
            )
        }
        
        composable(Screen.Project.route) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
            ProjectScreen(
                projectId = projectId,
                onNavigateBack = {
                    if (clickThrottle.canClick()) {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
