package com.chomu.aiagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chomu.aiagent.ui.screens.ChatScreen
import com.chomu.aiagent.ui.screens.PermissionsScreen
import com.chomu.aiagent.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    object Permissions : Screen("permissions")
    object Chat : Screen("chat")
    object Settings : Screen("settings")
}

@Composable
fun ChomuNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Permissions.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Permissions.route) {
            PermissionsScreen(
                onPermissionsGranted = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
