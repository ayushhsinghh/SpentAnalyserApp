package com.oracle.ee.spentanalyser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.ui.MainUiState
import com.oracle.ee.spentanalyser.ui.MainViewModel
import com.oracle.ee.spentanalyser.ui.AiModelState
import com.oracle.ee.spentanalyser.data.database.TransactionEntity
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    DashboardContent(
        uiState = uiState,
        transactions = transactions,
        selectedMonth = selectedMonth,
        selectedYear = selectedYear,
        onMonthChanged = { viewModel.updateSelectedMonth(it) },
        onYearChanged = { viewModel.updateSelectedYear(it) },
        modifier = modifier
    )
}

@Composable
fun DashboardContent(
    uiState: MainUiState,
    transactions: List<TransactionEntity>,
    selectedMonth: Int,
    selectedYear: Int,
    onMonthChanged: (Int) -> Unit,
    onYearChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { ChartEntryModelProducer() }
    
    LaunchedEffect(transactions) {
        if (transactions.isNotEmpty()) {
            val entries = transactions.mapIndexed { index, data ->
                FloatEntry(x = index.toFloat(), y = data.amount.toFloat())
            }
            modelProducer.setEntries(entries)
        } else {
            // Skeleton Data
            modelProducer.setEntries(listOf(
                FloatEntry(0f, 100f), FloatEntry(1f, 300f), FloatEntry(2f, 200f), FloatEntry(3f, 500f)
            ))
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dashboard", 
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    MonthYearPicker(
                        selectedMonth = selectedMonth,
                        selectedYear = selectedYear,
                        onMonthChanged = onMonthChanged,
                        onYearChanged = onYearChanged
                    )
                }
            }

            item {
                CashFlowHealthBar(transactions)
            }

            item {
                AnimatedVisibility(
                    visible = uiState.aiModelState != AiModelState.READY || uiState.isLoading,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    AiStatusCard(uiState)
                }
            }

            item {
                QuickStatsRow(transactions)
            }

            if (transactions.isNotEmpty()) {
                item {
                    DailyBurnRateCard(transactions, selectedMonth, selectedYear)
                }

                item {
                    TopDestinationsRow(transactions)
                }
            }

            item {
                Text(
                    text = "Spending Trend", 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Chart(
                        chart = lineChart(),
                        chartModelProducer = modelProducer,
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(),
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
            }

            if (transactions.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Transactions", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(transactions.take(10)) { tx ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = tx.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(text = tx.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "₹${"%.2f".format(tx.amount)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.type.equals("CREDIT", true)) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiStatusCard(uiState: MainUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (uiState.aiModelState) {
                    AiModelState.CHECKING -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Checking AI Engine...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    AiModelState.DOWNLOADING -> {
                        Text("Downloading Local Model: ${uiState.downloadProgress}%", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    AiModelState.ERROR -> {
                        Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    }
                    AiModelState.READY -> {
                        if (uiState.isLoading) {
                            Text("Parsing SMS via Gemma...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            if (uiState.aiModelState == AiModelState.DOWNLOADING) {
                val progress = (uiState.downloadProgress / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else if (uiState.aiModelState == AiModelState.READY && uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    }
}

@Composable
fun QuickStatsRow(transactions: List<TransactionEntity>) {
    val stats by remember(transactions) {
        derivedStateOf {
            val totalSpend = transactions.filter { it.type.equals("DEBIT", ignoreCase = true) }.sumOf { it.amount }
            val topMerchant = transactions
                .filter { it.type.equals("DEBIT", ignoreCase = true) }
                .groupBy { it.merchant }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .maxByOrNull { it.value }?.key ?: "N/A"
            totalSpend to topMerchant
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(
            title = "Total Spend",
            value = "₹${"%.2f".format(stats.first)}",
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Top Merchant",
            value = stats.second,
            icon = { Icon(Icons.Default.TrendingDown, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon()
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearPicker(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthChanged: (Int) -> Unit,
    onYearChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(text = "${months[selectedMonth]} $selectedYear")
        }
        
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Let the user select the last 6 months easily
            val calendar = java.util.Calendar.getInstance()
            for (i in 0..5) {
                val m = calendar.get(java.util.Calendar.MONTH)
                val y = calendar.get(java.util.Calendar.YEAR)
                DropdownMenuItem(
                    text = { Text("${months[m]} $y") },
                    onClick = {
                        onMonthChanged(m)
                        onYearChanged(y)
                        expanded = false
                    }
                )
                calendar.add(java.util.Calendar.MONTH, -1)
            }
        }
    }
}

@Composable
fun CashFlowHealthBar(transactions: List<TransactionEntity>, modifier: Modifier = Modifier) {
    if (transactions.isEmpty()) return

    val totalCredit = transactions.filter { it.type.equals("CREDIT", ignoreCase = true) }.sumOf { it.amount }
    val totalDebit = transactions.filter { it.type.equals("DEBIT", ignoreCase = true) }.sumOf { it.amount }
    val totalFlow = totalCredit + totalDebit
    
    val creditRatio = if (totalFlow > 0) (totalCredit / totalFlow).toFloat() else 0.5f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "In: ₹${"%.0f".format(totalCredit)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
            Text(text = "Out: ₹${"%.0f".format(totalDebit)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                LinearProgressIndicator(
            progress = { creditRatio },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Color(0xFF4CAF50), // Green Foreground (Credit)
            trackColor = MaterialTheme.colorScheme.error // Red Background (Debit)
        )
    }
}

@Composable
fun DailyBurnRateCard(transactions: List<TransactionEntity>, selectedMonth: Int, selectedYear: Int, modifier: Modifier = Modifier) {
    if (transactions.isEmpty()) return

    val totalDebit = transactions.filter { it.type.equals("DEBIT", ignoreCase = true) }.sumOf { it.amount }
    
    val calendar = java.util.Calendar.getInstance()
    val isCurrentMonth = calendar.get(java.util.Calendar.MONTH) == selectedMonth && calendar.get(java.util.Calendar.YEAR) == selectedYear
    
    val daysPassed = if (isCurrentMonth) {
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    } else {
        calendar.set(java.util.Calendar.YEAR, selectedYear)
        calendar.set(java.util.Calendar.MONTH, selectedMonth)
        calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }

    val dailyAverage = if (daysPassed > 0) totalDebit / daysPassed else 0.0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Daily Burn Rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    text = "₹${"%.2f".format(dailyAverage)} / day", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Icon(Icons.Default.TrendingDown, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
fun TopDestinationsRow(transactions: List<TransactionEntity>, modifier: Modifier = Modifier) {
    val topMerchants = transactions
        .filter { it.type.equals("DEBIT", ignoreCase = true) }
        .groupBy { it.merchant }
        .map { entry -> 
            val totalSpent = entry.value.sumOf { it.amount }
            Triple(entry.key, entry.value.size, totalSpent)
        }
        .sortedByDescending { it.third }
        .take(3)

    if (topMerchants.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Top Destinations", 
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            topMerchants.forEach { (merchant, count, spent) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = merchant, style = MaterialTheme.typography.labelMedium, maxLines = 1, fontWeight = FontWeight.Bold)
                        Text(text = "$count visits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "₹${"%.0f".format(spent)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
