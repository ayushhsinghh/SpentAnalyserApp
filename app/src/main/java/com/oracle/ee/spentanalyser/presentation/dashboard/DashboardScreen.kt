package com.oracle.ee.spentanalyser.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.presentation.common.EmptyStateView
import com.oracle.ee.spentanalyser.presentation.common.ShimmerChart
import com.oracle.ee.spentanalyser.presentation.common.ShimmerStatsRow
import com.oracle.ee.spentanalyser.presentation.common.ShimmerTransactionList
import com.oracle.ee.spentanalyser.ui.theme.SpentAnalyserThemeExtensions
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val filterPeriod by viewModel.filterPeriod.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Dashboard",
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.loadData() },
                icon = {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Analyze new SMS messages"
                    )
                },
                text = { Text("Analyze SMS") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        DashboardContent(
            uiState = uiState,
            transactions = transactions,
            selectedMonth = selectedMonth,
            selectedYear = selectedYear,
            filterPeriod = filterPeriod,
            onMonthChanged = { viewModel.updateSelectedMonth(it) },
            onYearChanged = { viewModel.updateSelectedYear(it) },
            onFilterChanged = { viewModel.updateFilterPeriod(it) },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    transactions: List<Transaction>,
    selectedMonth: Int,
    selectedYear: Int,
    filterPeriod: FilterPeriod,
    onMonthChanged: (Int) -> Unit,
    onYearChanged: (Int) -> Unit,
    onFilterChanged: (FilterPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val financeColors = SpentAnalyserThemeExtensions.financeColors
    val modelProducer = remember { ChartEntryModelProducer() }
    val isLoading = uiState.isLoading && transactions.isEmpty()

    LaunchedEffect(transactions) {
        if (transactions.isNotEmpty()) {
            val entries = transactions.mapIndexed { index, data ->
                FloatEntry(x = index.toFloat(), y = data.amount.toFloat())
            }
            modelProducer.setEntries(entries)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp), // Clear the FAB
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Filter Chips (thumb-friendly, bottom-accessible within scroll) ──
        item {
            FilterChipRow(
                selectedMonth = selectedMonth,
                selectedYear = selectedYear,
                filterPeriod = filterPeriod,
                onMonthChanged = onMonthChanged,
                onYearChanged = onYearChanged,
                onFilterChanged = onFilterChanged,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // ── AI / Worker Status Banners ──
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = uiState.aiModelState != AiModelState.READY || uiState.isLoading,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    AiStatusBanner(uiState)
                }

                AnimatedVisibility(
                    visible = uiState.backgroundWorkerState != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    WorkerStatusBanner(uiState)
                }
            }
        }

        // ── Cash Flow Health Bar ──
        if (transactions.isNotEmpty()) {
            item {
                CashFlowHealthBar(
                    transactions = transactions,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // ── Quick Stats ──
        item {
            if (isLoading) {
                ShimmerStatsRow(modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                QuickStatsRow(
                    transactions = transactions,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // ── Spending Trend Chart ──
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Spending Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (isLoading) {
                    ShimmerChart()
                } else if (transactions.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .semantics { contentDescription = "Spending trend chart showing ${transactions.size} transactions" },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Chart(
                            chart = lineChart(),
                            chartModelProducer = modelProducer,
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        // ── Daily Burn Rate + Top Destinations ──
        if (transactions.isNotEmpty()) {
            item {
                DailyBurnRateCard(
                    transactions = transactions,
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                TopDestinationsRow(
                    transactions = transactions,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // ── Recent Transactions ──
        item {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (isLoading) {
            item {
                ShimmerTransactionList(
                    count = 4,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else if (transactions.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.Receipt,
                    title = "No transactions yet",
                    subtitle = "Tap \"Analyze SMS\" to parse your bank messages"
                )
            }
        } else {
            items(
                transactions.take(8),
                key = { it.id }
            ) { tx ->
                RecentTransactionItem(
                    transaction = tx,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem()
                )
            }
        }
    }
}

// ─── Filter Chips ───
@Composable
fun FilterChipRow(
    selectedMonth: Int,
    selectedYear: Int,
    filterPeriod: FilterPeriod,
    onMonthChanged: (Int) -> Unit,
    onYearChanged: (Int) -> Unit,
    onFilterChanged: (FilterPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    data class ChipItem(val label: String, val period: FilterPeriod, val month: Int?, val year: Int?)

    val chips = buildList {
        add(ChipItem("Today", FilterPeriod.TODAY, null, null))
        add(ChipItem("7 Days", FilterPeriod.LAST_7_DAYS, null, null))
        val cal = java.util.Calendar.getInstance()
        for (i in 0..5) {
            val m = cal.get(java.util.Calendar.MONTH)
            val y = cal.get(java.util.Calendar.YEAR)
            add(ChipItem("${months[m]} $y", FilterPeriod.MONTH, m, y))
            cal.add(java.util.Calendar.MONTH, -1)
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        items(chips) { chip ->
            val isSelected = when (chip.period) {
                FilterPeriod.TODAY -> filterPeriod == FilterPeriod.TODAY
                FilterPeriod.LAST_7_DAYS -> filterPeriod == FilterPeriod.LAST_7_DAYS
                FilterPeriod.MONTH -> filterPeriod == FilterPeriod.MONTH
                        && chip.month == selectedMonth
                        && chip.year == selectedYear
            }

            FilterChip(
                selected = isSelected,
                onClick = {
                    when (chip.period) {
                        FilterPeriod.TODAY, FilterPeriod.LAST_7_DAYS -> onFilterChanged(chip.period)
                        FilterPeriod.MONTH -> {
                            chip.month?.let(onMonthChanged)
                            chip.year?.let(onYearChanged)
                        }
                    }
                },
                label = { Text(chip.label) },
                modifier = Modifier.height(48.dp), // 48dp minimum touch target
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected: ${chip.label}",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null
            )
        }
    }
}

// ─── AI Status Banner ───
@Composable
fun AiStatusBanner(uiState: DashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val statusIcon: @Composable () -> Unit
                val statusText: String

                when (uiState.aiModelState) {
                    AiModelState.CHECKING -> {
                        statusIcon = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "No model loaded",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        statusText = "No model active — go to Models to set one up"
                    }
                    AiModelState.ERROR -> {
                        statusIcon = {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = "AI engine error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        statusText = uiState.error ?: "Failed to load AI Engine"
                    }
                    AiModelState.READY -> {
                        statusIcon = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        statusText = "Parsing SMS via Gemma…"
                    }
                }

                statusIcon()
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (uiState.aiModelState == AiModelState.ERROR)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Progress bar (only when parsing)
            if (uiState.aiModelState == AiModelState.READY && uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    }
}

// ─── Worker Status Banner (no more unsafe casts) ───
@Composable
fun WorkerStatusBanner(uiState: DashboardUiState) {
    val state = uiState.backgroundWorkerState ?: return

    val statusText: String
    val barColor: androidx.compose.ui.graphics.Color
    val containerColor: androidx.compose.ui.graphics.Color
    val showProgress: Boolean

    when (state) {
        androidx.work.WorkInfo.State.RUNNING -> {
            statusText = "Parsing background SMS…"
            barColor = MaterialTheme.colorScheme.tertiary
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            showProgress = true
        }
        androidx.work.WorkInfo.State.ENQUEUED -> {
            statusText = "Background worker scheduled"
            barColor = MaterialTheme.colorScheme.secondary
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            showProgress = false
        }
        androidx.work.WorkInfo.State.FAILED -> {
            statusText = "Background worker failed"
            barColor = MaterialTheme.colorScheme.error
            containerColor = MaterialTheme.colorScheme.errorContainer
            showProgress = false
        }
        else -> {
            statusText = "Worker idle"
            barColor = MaterialTheme.colorScheme.outline
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            showProgress = false
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = barColor
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (showProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = barColor,
                    trackColor = containerColor
                )
            }
        }
    }
}

// ─── Cash Flow Health Bar ───
@Composable
fun CashFlowHealthBar(transactions: List<Transaction>, modifier: Modifier = Modifier) {
    if (transactions.isEmpty()) return

    val financeColors = SpentAnalyserThemeExtensions.financeColors
    val totalCredit = transactions.filter { it.type.equals("CREDIT", ignoreCase = true) }.sumOf { it.amount }
    val totalDebit = transactions.filter { it.type.equals("DEBIT", ignoreCase = true) }.sumOf { it.amount }
    val totalFlow = totalCredit + totalDebit
    val creditRatio = if (totalFlow > 0) (totalCredit / totalFlow).toFloat() else 0.5f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Income", tint = financeColors.credit, modifier = Modifier.size(14.dp))
                    Text(text = "+₹${"%.0f".format(totalCredit)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = financeColors.credit)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Expense", tint = financeColors.debit, modifier = Modifier.size(14.dp))
                    Text(text = "−₹${"%.0f".format(totalDebit)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = financeColors.debit)
                }
            }
            LinearProgressIndicator(
                progress = { creditRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics { contentDescription = "Cash flow: ${(creditRatio * 100).toInt()}% income vs expense" },
                color = financeColors.credit,
                trackColor = financeColors.debit
            )
        }
    }
}

// ─── Quick Stats Row ───
@Composable
fun QuickStatsRow(transactions: List<Transaction>, modifier: Modifier = Modifier) {
    val financeColors = SpentAnalyserThemeExtensions.financeColors
    val stats by remember(transactions) {
        derivedStateOf {
            val totalSpend = transactions.filter { it.type.equals("DEBIT", ignoreCase = true) }.sumOf { it.amount }
            val txCount = transactions.size
            totalSpend to txCount
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Total Spent",
            value = "₹${"%.0f".format(stats.first)}",
            icon = {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = "Total spent: ₹${"%.0f".format(stats.first)}",
                    tint = financeColors.debit
                )
            },
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Transactions",
            value = "${stats.second}",
            icon = {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = "${stats.second} transactions",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon()
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─── Daily Burn Rate Card ───
@Composable
fun DailyBurnRateCard(
    transactions: List<Transaction>,
    selectedMonth: Int,
    selectedYear: Int,
    modifier: Modifier = Modifier
) {
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Daily Burn Rate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${"%.0f".format(dailyAverage)} / day",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = "Daily burn rate: ₹${"%.0f".format(dailyAverage)} per day",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// ─── Top Destinations ───
@Composable
fun TopDestinationsRow(transactions: List<Transaction>, modifier: Modifier = Modifier) {
    val topMerchants = transactions
        .filter { it.type.equals("DEBIT", ignoreCase = true) }
        .groupBy { it.merchant }
        .map { entry ->
            Triple(entry.key, entry.value.size, entry.value.sumOf { it.amount })
        }
        .sortedByDescending { it.third }
        .take(3)

    if (topMerchants.isEmpty()) return

    val financeColors = SpentAnalyserThemeExtensions.financeColors

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Top Merchants",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topMerchants.forEach { (merchant, count, spent) ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = merchant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$count txns",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "−₹${"%.0f".format(spent)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = financeColors.debit
                        )
                    }
                }
            }
        }
    }
}

// ─── Recent Transaction Item ───
@Composable
fun RecentTransactionItem(transaction: Transaction, modifier: Modifier = Modifier) {
    val financeColors = SpentAnalyserThemeExtensions.financeColors
    val isCredit = transaction.type.equals("CREDIT", ignoreCase = true)
    val amountColor = if (isCredit) financeColors.credit else financeColors.debit
    val amountPrefix = if (isCredit) "+" else "−"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCredit) financeColors.creditContainer else financeColors.debitContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = if (isCredit) "Credit transaction" else "Debit transaction",
                    tint = amountColor,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = transaction.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "$amountPrefix₹${"%.2f".format(transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}
