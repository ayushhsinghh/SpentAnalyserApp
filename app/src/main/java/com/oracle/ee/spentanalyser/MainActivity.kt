package com.oracle.ee.spentanalyser

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.oracle.ee.spentanalyser.data.LlmInferenceRepository
import com.oracle.ee.spentanalyser.data.SmsRepository
import com.oracle.ee.spentanalyser.ui.MainViewModel
import com.oracle.ee.spentanalyser.ui.screens.DashboardScreen
import com.oracle.ee.spentanalyser.ui.theme.SpentAnalyserTheme
import timber.log.Timber

object Destinations {
    const val DASHBOARD = "dashboard"
}

class MainActivity : ComponentActivity() {

    // Simple manual DI for repositories
    private val smsRepository by lazy { SmsRepository(applicationContext) }
    private val llmInferenceRepository by lazy { LlmInferenceRepository(applicationContext) }

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

        // Request permissions right at launch for our personal app purposes
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            )
        )
        enableEdgeToEdge()
        setContent {
            SpentAnalyserTheme {
                val navController = rememberNavController()

                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MainViewModel(
                            smsRepository = smsRepository,
                            llmInferenceRepository = llmInferenceRepository
                        ) as T
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Destinations.DASHBOARD,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Destinations.DASHBOARD) {
                        val mainViewModel: MainViewModel = viewModel(factory = factory)
                        DashboardScreen(viewModel = mainViewModel)
                    }
                }
            }
        }
    }
}