package com.oracle.ee.spentanalyser.presentation.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.oracle.ee.spentanalyser.domain.model.MonthlySpendItem
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.presentation.common.TransactionDetailsDialogGroup
import com.oracle.ee.spentanalyser.presentation.transactions.TransactionCard
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillDownScreen(
    viewModel: DrillDownViewModel,
    entityType: EntityType,
    entityName: String,
    onBack: () -> Unit
) {
    LaunchedEffect(entityType, entityName) {
        viewModel.init(entityType, entityName)
    }

    val state by viewModel.state.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    
    val numberFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            maximumFractionDigits = 0
        }
    }

    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var selectedTransactionSms by remember { mutableStateOf<String?>(null) }
    var isFetchingSms by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(entityName, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (entityType == EntityType.MERCHANT) "Merchant Analytics" else "Category Analytics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // Hero Stats
                item {
                    HeroStatsGrid(state, numberFormat)
                }

                // Chart
                if (state.monthlySpendTrend.isNotEmpty()) {
                    item {
                        Text(
                            "Monthly Spend Trend", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SpendTrendChart(state.monthlySpendTrend)
                        }
                    }
                }

                item {
                    Text(
                        "Recent Transactions", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                items(transactions, key = { it.id }) { tx ->
                    TransactionCard(transaction = tx, onClick = {
                        selectedTransaction = tx
                        isFetchingSms = true
                        scope.launch {
                            val sms = viewModel.getSourceSms(tx.sourceSmsHash)
                            selectedTransactionSms = sms?.body
                            isFetchingSms = false
                        }
                    })
                }
            }
        }

        TransactionDetailsDialogGroup(
            selectedTransaction = selectedTransaction,
            selectedTransactionSms = selectedTransactionSms,
            isFetchingSms = isFetchingSms,
            onDismissDetails = {
                selectedTransaction = null
                selectedTransactionSms = null
            },
            onEditTransaction = { updatedTx, originalMerchant ->
                viewModel.updateExistingTransaction(updatedTx, originalMerchant)
            },
            onDeleteTransaction = { id ->
                viewModel.deleteTransaction(id)
                selectedTransaction = null
                selectedTransactionSms = null
            }
        )
    }
}

@Composable
fun HeroStatsGrid(state: DrillDownState, numberFormat: NumberFormat) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard(
            title = "Total Value",
            value = numberFormat.format(state.totalTransactionsAmount),
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "Avg Ticket",
            value = numberFormat.format(state.averageTicketSize),
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            title = "30D Freq",
            value = "${state.frequencyLast30Days}x",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SpendTrendChart(trendData: List<MonthlySpendItem>) {
    val modelProducer = remember(trendData) {
        val entries = trendData.mapIndexed { index, item ->
            FloatEntry(x = index.toFloat(), y = item.totalAmount.toFloat())
        }
        ChartEntryModelProducer(entries)
    }
    
    val bottomAxisFormatter = com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Horizontal.Bottom> { value, _ ->
        val index = value.toInt()
        if (index >= 0 && index < trendData.size) {
            val fullStr = trendData[index].monthYear // e.g. 2026-03
            val parts = fullStr.split("-")
            if (parts.size == 2) "${parts[1]}/${parts[0].takeLast(2)}" else fullStr
        } else {
            ""
        }
    }

    Chart(
        chart = lineChart(),
        chartModelProducer = modelProducer,
        startAxis = rememberStartAxis(),
        bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(16.dp)
    )
}
