package com.oracle.ee.spentanalyser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.data.database.ParseStatus
import com.oracle.ee.spentanalyser.data.database.SmsLogEntity
import com.oracle.ee.spentanalyser.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val logs by viewModel.smsLogs.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Success", "Failed", "Ignored")

    val filteredLogs = remember(logs, selectedTab) {
        when (selectedTab) {
            1 -> logs.filter { it.status == ParseStatus.SUCCESS.name }
            2 -> logs.filter { it.status == ParseStatus.ERROR.name || it.status == ParseStatus.MANUAL_REVIEW.name }
            3 -> logs.filter { it.status == ParseStatus.IGNORED.name }
            else -> logs
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Processing Hub") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (filteredLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No entries found.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(filteredLogs) { log ->
                    LogCard(log)
                }
            }
        }
    }
}

@Composable
fun LogCard(log: SmsLogEntity) {
    val status = ParseStatus.valueOf(log.status)
    
    val containerColor = when (status) {
        ParseStatus.SUCCESS -> MaterialTheme.colorScheme.surfaceVariant
        ParseStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        ParseStatus.MANUAL_REVIEW -> MaterialTheme.colorScheme.secondaryContainer
        ParseStatus.IGNORED -> MaterialTheme.colorScheme.surface
        ParseStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val icon = when (status) {
        ParseStatus.SUCCESS -> Icons.Default.CheckCircle
        ParseStatus.ERROR -> Icons.Default.Warning
        ParseStatus.MANUAL_REVIEW -> Icons.Default.Edit
        ParseStatus.IGNORED -> Icons.Default.Block
        ParseStatus.PENDING -> Icons.Default.HourglassEmpty
    }
    
    val iconTint = when (status) {
        ParseStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ParseStatus.ERROR -> MaterialTheme.colorScheme.error
        ParseStatus.MANUAL_REVIEW -> MaterialTheme.colorScheme.secondary
        ParseStatus.IGNORED -> MaterialTheme.colorScheme.outline
        ParseStatus.PENDING -> MaterialTheme.colorScheme.primary
    }

    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(log.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Status Icon",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.status,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text("Raw SMS (${log.sender}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(log.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            
            if (status == ParseStatus.ERROR && log.errorMessage != null) {
                Text("Error:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(log.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { /* TODO: Hook up Manual Edit Dialog */ },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Manual Edit")
                }
            }

            if (log.rawLlmOutput != null && status != ParseStatus.IGNORED) {
                Text("Gemma Output:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small)
                    .padding(8.dp)
                ) {
                    Text(
                        text = log.rawLlmOutput.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
