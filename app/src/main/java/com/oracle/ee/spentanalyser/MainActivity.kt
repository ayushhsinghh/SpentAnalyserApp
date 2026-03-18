package com.oracle.ee.spentanalyser

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oracle.ee.spentanalyser.presentation.dashboard.DashboardScreen
import com.oracle.ee.spentanalyser.presentation.dashboard.DashboardViewModel
import com.oracle.ee.spentanalyser.presentation.logs.LogsScreen
import com.oracle.ee.spentanalyser.presentation.logs.LogsViewModel
import com.oracle.ee.spentanalyser.presentation.models.ModelsScreen
import com.oracle.ee.spentanalyser.presentation.models.ModelsViewModel
import com.oracle.ee.spentanalyser.presentation.monitoring.MonitoringScreen
import com.oracle.ee.spentanalyser.presentation.monitoring.MonitoringViewModel
import com.oracle.ee.spentanalyser.presentation.transactions.TransactionsScreen
import com.oracle.ee.spentanalyser.presentation.transactions.TransactionsViewModel
import com.oracle.ee.spentanalyser.presentation.analytics.DrillDownScreen
import com.oracle.ee.spentanalyser.presentation.analytics.DrillDownViewModel
import com.oracle.ee.spentanalyser.presentation.analytics.EntityType
import com.oracle.ee.spentanalyser.presentation.inbox.InboxScreen
import com.oracle.ee.spentanalyser.presentation.inbox.InboxViewModel
import com.oracle.ee.spentanalyser.presentation.settings.SettingsScreen
import com.oracle.ee.spentanalyser.presentation.settings.SettingsViewModel
import com.oracle.ee.spentanalyser.ui.navigation.Screen
import com.oracle.ee.spentanalyser.ui.navigation.bottomNavItems
import com.oracle.ee.spentanalyser.ui.navigation.drawerItems
import com.oracle.ee.spentanalyser.ui.theme.SpentAnalyserTheme
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readSmsGranted = permissions[Manifest.permission.READ_SMS] ?: false
        val receiveSmsGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        val notificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false

        if (readSmsGranted && receiveSmsGranted) {
            Timber.d("SMS Permissions granted. Continuing...")
        } else {
            Timber.w("SMS Permissions denied.")
        }
        
        if (notificationsGranted) {
            Timber.d("Notification Permissions granted. Model downloading will show foreground indicators.")
        } else {
            Timber.w("Notification Permissions denied. Downloading will run silently and might be killed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher.launch(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS
                )
            }
        )

        enableEdgeToEdge()
        setContent {
            val appContainer = (application as SpentAnalyserApplication).container

            SpentAnalyserTheme {
                // ── ViewModel Factories ──
                val dashboardFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DashboardViewModel(
                            transactionRepository = appContainer.transactionRepository,
                            modelRepository = appContainer.modelRepository,
                            llmEngine = appContainer.llmEngine,
                            parseSmsUseCase = appContainer.parseSmsUseCase,
                            workManager = appContainer.workManager,
                            preferencesManager = appContainer.preferencesManager,
                            smsLogRepository = appContainer.smsLogRepository
                        ) as T
                    }
                }

                val transactionsFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return TransactionsViewModel(
                            transactionRepository = appContainer.transactionRepository,
                            smsLogRepository = appContainer.smsLogRepository
                        ) as T
                    }
                }

                val logsFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return LogsViewModel(
                            smsLogRepository = appContainer.smsLogRepository,
                            transactionRepository = appContainer.transactionRepository,
                            parseSmsUseCase = appContainer.parseSmsUseCase
                        ) as T
                    }
                }

                val modelsFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return ModelsViewModel(
                            modelRepository = appContainer.modelRepository,
                            llmEngine = appContainer.llmEngine,
                            workManager = appContainer.workManager
                        ) as T
                    }
                }

                val monitoringFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MonitoringViewModel(
                            systemMonitor = appContainer.systemMonitor
                        ) as T
                    }
                }

                val drillDownFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DrillDownViewModel(
                            transactionRepository = appContainer.transactionRepository,
                            smsLogRepository = appContainer.smsLogRepository
                        ) as T
                    }
                }

                val settingsFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return SettingsViewModel(
                            preferencesManager = appContainer.preferencesManager,
                            transactionRepository = appContainer.transactionRepository,
                            smsLogRepository = appContainer.smsLogRepository
                        ) as T
                    }
                }

                val inboxFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return InboxViewModel(
                            smsInboxDataSource = appContainer.smsInboxDataSource,
                            smsLogRepository = appContainer.smsLogRepository,
                            parseSmsUseCase = appContainer.parseSmsUseCase
                        ) as T
                    }
                }

                val dashboardViewModel: DashboardViewModel = viewModel(factory = dashboardFactory)
                val transactionsViewModel: TransactionsViewModel = viewModel(factory = transactionsFactory)
                val logsViewModel: LogsViewModel = viewModel(factory = logsFactory)
                val modelsViewModel: ModelsViewModel = viewModel(factory = modelsFactory)
                val monitoringViewModel: MonitoringViewModel = viewModel(factory = monitoringFactory)
                val drillDownViewModel: DrillDownViewModel = viewModel(factory = drillDownFactory)
                val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)
                val inboxViewModel: InboxViewModel = viewModel(factory = inboxFactory)

                SpentAnalyserApp(
                    dashboardViewModel = dashboardViewModel,
                    transactionsViewModel = transactionsViewModel,
                    logsViewModel = logsViewModel,
                    modelsViewModel = modelsViewModel,
                    monitoringViewModel = monitoringViewModel,
                    drillDownViewModel = drillDownViewModel,
                    settingsViewModel = settingsViewModel,
                    inboxViewModel = inboxViewModel,
                    preferencesManager = appContainer.preferencesManager
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpentAnalyserApp(
    dashboardViewModel: DashboardViewModel,
    transactionsViewModel: TransactionsViewModel,
    logsViewModel: LogsViewModel,
    modelsViewModel: ModelsViewModel,
    monitoringViewModel: MonitoringViewModel,
    drillDownViewModel: DrillDownViewModel,
    settingsViewModel: SettingsViewModel,
    inboxViewModel: InboxViewModel,
    preferencesManager: com.oracle.ee.spentanalyser.data.database.PreferencesManager
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                // Drawer header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = "Spend Analyzer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Settings & Management",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                // Drawer nav items
                drawerItems.forEach { screen ->
                    NavigationDrawerItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
            }
        },
    ) {
        // Determine if we're on a bottom-nav destination
        val isBottomNavRoute = bottomNavItems.any { screen ->
            currentDestination?.hierarchy?.any { it.route == screen.route } == true
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = if (isBottomNavRoute) {
                            bottomNavItems.find { screen ->
                                currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            }?.title ?: ""
                        } else {
                            drawerItems.find { screen ->
                                currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            }?.title ?: ""
                        }
                        Text(text = title, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        if (!isBottomNavRoute) {
                            // Back arrow on drawer screens → go home
                            IconButton(
                                onClick = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Dashboard"
                                )
                            }
                        } else {
                            // Hamburger on bottom-nav screens
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } }
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open navigation drawer"
                                )
                            }
                        }
                    },
                    actions = {
                        if (!isBottomNavRoute) {
                            // Also allow drawer access from drawer screens
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } }
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open navigation drawer"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (isBottomNavRoute) {
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
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Bottom Nav destinations
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onNavigateToDrillDown = { type, name ->
                            navController.navigate(Screen.DrillDown.createRoute(type.name, name))
                        }
                    )
                }
                composable(Screen.Transactions.route) {
                    TransactionsScreen(
                        viewModel = transactionsViewModel,
                        onNavigateToDrillDown = { type, name ->
                            navController.navigate(Screen.DrillDown.createRoute(type.name, name))
                        }
                    )
                }

                // Drawer destinations
                composable(Screen.Models.route) {
                    ModelsScreen(viewModel = modelsViewModel)
                }
                composable(Screen.Monitoring.route) {
                    MonitoringScreen(viewModel = monitoringViewModel)
                }
                composable(Screen.Logs.route) {
                    LogsScreen(viewModel = logsViewModel)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = settingsViewModel)
                }
                composable(Screen.Inbox.route) {
                    InboxScreen(viewModel = inboxViewModel)
                }

                // Dynamic destinations
                composable(Screen.DrillDown.route) { backStackEntry ->
                    val typeStr = backStackEntry.arguments?.getString("type") ?: "MERCHANT"
                    val name = backStackEntry.arguments?.getString("name") ?: ""
                    
                    val type = try {
                        EntityType.valueOf(typeStr)
                    } catch (e: Exception) {
                        EntityType.MERCHANT
                    }
                    
                    // Initialize the viewmodel only once per destination if possible, 
                    // or just init it on load (LaunchEffect in screen)
                    DrillDownScreen(
                        viewModel = drillDownViewModel,
                        entityType = type,
                        entityName = name,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}