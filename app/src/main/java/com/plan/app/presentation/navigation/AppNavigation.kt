package com.plan.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.plan.app.presentation.ui.main.MainScreen
import com.plan.app.presentation.ui.project.ProjectScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * Debounce helper to prevent rapid clicks
 */
class DebounceNavigator(
    private val scope: CoroutineScope,
    private val delayMs: Long = 300
) {
    private var navigationJob: Job? = null
    
    fun navigate(action: () -> Unit) {
        navigationJob?.cancel()
        navigationJob = scope.launch {
            delay(delayMs)
            action()
        }
    }
}

/**
 * Main navigation host.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onThemeChanged: (Int) -> Unit = {},
    onError: (Exception) -> Unit = {}
) {
    // Debounce navigator to prevent rapid clicks
    val debounceNavigator = remember { DebounceNavigator(CoroutineScope(Dispatchers.Main)) }
    
    // Track if navigation is in progress to prevent double navigation
    var isNavigating by remember { mutableStateOf(false) }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onProjectClick = { projectId ->
                    if (!isNavigating) {
                        isNavigating = true
                        debounceNavigator.navigate {
                            try {
                                navController.navigate(Screen.Project.createRoute(projectId))
                            } catch (e: Exception) {
                                onError(e)
                            } finally {
                                isNavigating = false
                            }
                        }
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
                    if (!isNavigating) {
                        isNavigating = true
                        debounceNavigator.navigate {
                            try {
                                navController.popBackStack()
                            } catch (e: Exception) {
                                onError(e)
                            } finally {
                                isNavigating = false
                            }
                        }
                    }
                }
            )
        }
    }
}
