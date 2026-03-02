package com.oracle.ee.spentanalyser.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.oracle.ee.spentanalyser.ui.MainUiState
import com.oracle.ee.spentanalyser.ui.MainViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import com.oracle.ee.spentanalyser.ui.AiModelState
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    DashboardContent(
        uiState = uiState,
        modifier = modifier
    )
}

@Composable
fun DashboardContent(
    uiState: MainUiState,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { ChartEntryModelProducer() }
    
    LaunchedEffect(uiState.transactions) {
        if (uiState.transactions.isNotEmpty()) {
            val entries = uiState.transactions.mapIndexed { index, data ->
                FloatEntry(x = index.toFloat(), y = data.amount.toFloat())
            }
            modelProducer.setEntries(entries)
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (uiState.aiModelState) {
                AiModelState.CHECKING -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking AI Model Availability...")
                }
                AiModelState.DOWNLOADING -> {
                    // Safe progress calculation
                    val progress = (uiState.downloadProgress / 100f).coerceIn(0f, 1f)
                    CircularProgressIndicator(progress = progress)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Downloading AI Model... ${uiState.downloadProgress}%")
                }
                AiModelState.ERROR -> {
                    Text(
                        text = "Error: ${uiState.error}", 
                        color = MaterialTheme.colorScheme.error
                    )
                }
                AiModelState.READY -> {
                    if (uiState.isLoading) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Parsing SMS transactions with Gemma...")
                    } else if (uiState.transactions.isEmpty()) {
                        Text(text = "No transactions found")
                    } else {
                        Text(
                            text = "Spend Analysis", 
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Chart(
                            chart = columnChart(),
                            chartModelProducer = modelProducer,
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
