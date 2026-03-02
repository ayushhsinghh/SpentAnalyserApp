package com.oracle.ee.spentanalyser

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oracle.ee.spentanalyser.ui.MainViewModel
import com.oracle.ee.spentanalyser.ui.navigation.Screen
import com.oracle.ee.spentanalyser.ui.navigation.bottomNavItems
import com.oracle.ee.spentanalyser.ui.screens.DashboardScreen
import com.oracle.ee.spentanalyser.ui.screens.LogsScreen
import com.oracle.ee.spentanalyser.ui.screens.TransactionsScreen
import com.oracle.ee.spentanalyser.ui.theme.SpentAnalyserTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readSmsGranted = permissions[Manifest.permission.READ_SMS] ?: false
        val receiveSmsGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        
        if (readSmsGranted && receiveSmsGranted) {
            Timber.d("SMS Permissions granted. Continuing...")
        } else {
            Timber.w("SMS Permissions denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            )
        )
        
        enableEdgeToEdge()
        setContent {
            val appContainer = (application as SpentAnalyserApplication).container

            SpentAnalyserTheme {
                val navController = rememberNavController()

                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MainViewModel(
                            appDao = appContainer.appDao,
                            smsRepository = appContainer.smsRepository,
                            llmInferenceRepository = appContainer.llmInferenceRepository
                        ) as T
                    }
                }
                
                val mainViewModel: MainViewModel = viewModel(factory = factory)

                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        NavigationBar {
                            bottomNavItems.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route,
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    ) {
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(viewModel = mainViewModel)
                        }
                        composable(Screen.Transactions.route) {
                            TransactionsScreen(viewModel = mainViewModel)
                        }
                        composable(Screen.Logs.route) {
                            LogsScreen(viewModel = mainViewModel)
                        }
                    }
                }
            }
        }
    }
}