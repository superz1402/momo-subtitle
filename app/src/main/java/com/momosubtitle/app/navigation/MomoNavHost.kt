package com.momosubtitle.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.momosubtitle.feature.editor.EditorScreen
import com.momosubtitle.feature.home.HomeScreen
import com.momosubtitle.feature.imports.ImportScreen
import com.momosubtitle.feature.models.ModelsScreen
import com.momosubtitle.feature.processing.ProcessingScreen
import com.momosubtitle.feature.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val MODELS = "models"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val PROCESSING = "processing/{runId}"
    const val EDITOR = "editor/{runId}"

    fun processing(runId: String) = "processing/$runId"
    fun editor(runId: String) = "editor/$runId"
}

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Filled.Home),
    MODELS(Routes.MODELS, "Models", Icons.Filled.Build),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
}

@Composable
fun MomoRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = TopLevelDestination.entries

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevel.map { it.route }) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onStartProject = { navController.navigate(Routes.IMPORT) },
                    onOpenRun = { runId -> navController.navigate(Routes.processing(runId)) }
                )
            }
            composable(Routes.MODELS) { ModelsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.IMPORT) {
                ImportScreen(
                    onStarted = { runId ->
                        navController.navigate(Routes.processing(runId)) { popUpTo(Routes.HOME) }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PROCESSING) { entry ->
                val runId = entry.arguments?.getString("runId").orEmpty()
                ProcessingScreen(runId = runId, onBack = { navController.popBackStack() })
            }
            composable(Routes.EDITOR) { entry ->
                val runId = entry.arguments?.getString("runId").orEmpty()
                EditorScreen(runId = runId, onBack = { navController.popBackStack() })
            }
        }
    }
}
