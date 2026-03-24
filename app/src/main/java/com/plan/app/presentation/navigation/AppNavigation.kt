package com.plan.app.presentation.navigation

import androidx.compose.runtime.Composable
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
 * Main navigation host.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onProjectClick = { projectId ->
                    navController.navigate(Screen.Project.createRoute(projectId))
                }
            )
        }
        
        composable(Screen.Project.route) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
            ProjectScreen(
                projectId = projectId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
