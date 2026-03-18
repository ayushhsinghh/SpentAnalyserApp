package com.oracle.ee.spentanalyser.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    // ── Bottom Navigation (Primary) ──
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Transactions : Screen("transactions", "Transactions", Icons.AutoMirrored.Filled.List)

    // ── Navigation Drawer (Secondary/Management) ──
    object Models : Screen("models", "Models", Icons.Default.ModelTraining)
    object Monitoring : Screen("monitoring", "Monitoring", Icons.Default.Analytics)
    object Logs : Screen("logs", "App Logs", Icons.Default.Memory)
    object Inbox : Screen("inbox", "Device Inbox", Icons.Default.Mail)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    
    // ── Deep Links / Dynamic Routes ──
    object DrillDown : Screen("drill_down/{type}/{name}", "Analytics", Icons.Default.Analytics) {
        fun createRoute(type: String, name: String) = "drill_down/$type/${android.net.Uri.encode(name)}"
    }
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Transactions
)

val drawerItems = listOf(
    Screen.Models,
    Screen.Monitoring,
    Screen.Logs,
    Screen.Inbox,
    Screen.Settings
)
