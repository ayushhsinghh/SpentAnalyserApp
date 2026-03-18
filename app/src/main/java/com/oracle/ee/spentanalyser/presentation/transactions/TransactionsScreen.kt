package com.oracle.ee.spentanalyser.presentation.transactions

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.domain.model.TransactionFilterQuery
import com.oracle.ee.spentanalyser.presentation.common.EmptyStateView
import com.oracle.ee.spentanalyser.presentation.common.FilterBottomSheet
import com.oracle.ee.spentanalyser.presentation.common.TransactionDetailsDialogGroup
import com.oracle.ee.spentanalyser.ui.theme.SpentAnalyserThemeExtensions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.oracle.ee.spentanalyser.presentation.analytics.EntityType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel, 
    onNavigateToDrillDown: (EntityType, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.transactions.collectAsState()
    var isFilterSheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Transactions", fontWeight = FontWeight.Bold)
                        if (transactions.isNotEmpty()) {
                            Text(
                                "${transactions.size} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { isFilterSheetOpen = true }) {
                        Icon(imageVector = Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        val scope = rememberCoroutineScope()
        val filterQuery by viewModel.filterQuery.collectAsState()
        var isSearchActive by remember { mutableStateOf(false) }
        var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
        var selectedTransactionSms by remember { mutableStateOf<String?>(null) }
        var isFetchingSms by remember { mutableStateOf(false) }

        if (transactions.isEmpty()) {
            EmptyStateView(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "No transactions yet",
                subtitle = "Your parsed bank transactions will appear here.\nLong-press any item to edit.",
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                // Search Bar
                DockedSearchBar(
                    query = filterQuery.searchQuery ?: "",
                    onQueryChange = { 
                        viewModel.updateFilterQuery(filterQuery.copy(searchQuery = it)) 
                    },
                    onSearch = { isSearchActive = false },
                    active = isSearchActive,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text("Search merchants, categories...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { 
                        if (!filterQuery.searchQuery.isNullOrBlank()) {
                            IconButton(onClick = { 
                                viewModel.updateFilterQuery(filterQuery.copy(searchQuery = null)) 
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Search dropdown content if needed
                }

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 8.dp, bottom = 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        transactions,
                        key = { it.id }
                    ) { transaction ->
                        TransactionCard(
                            transaction = transaction,
                            onClick = {
                                selectedTransaction = transaction
                                isFetchingSms = true
                                scope.launch {
                                    val sms = viewModel.getSourceSms(transaction.sourceSmsHash)
                                    selectedTransactionSms = sms?.body
                                    isFetchingSms = false
                                }
                            },
                            onMerchantClick = { name -> onNavigateToDrillDown(EntityType.MERCHANT, name) },
                            onCategoryClick = { name -> onNavigateToDrillDown(EntityType.CATEGORY, name) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // Unified SMS Details & Actions Dialog
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

        // Filter Bottom Sheet
        if (isFilterSheetOpen) {
            FilterBottomSheet(
                currentQuery = filterQuery,
                onApply = { newQuery ->
                    viewModel.updateFilterQuery(newQuery)
                },
                onDismiss = { isFilterSheetOpen = false }
            )
        }
    }
}

@Composable
fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit = {},
    onMerchantClick: (String) -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val financeColors = SpentAnalyserThemeExtensions.financeColors
    val isCredit = transaction.type.equals("CREDIT", ignoreCase = true)
    val amountColor = if (isCredit) financeColors.credit else financeColors.debit
    val amountPrefix = if (isCredit) "+" else "−"

    val timeSdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = timeSdf.format(Date(transaction.timestamp))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth()
                .semantics {
                    contentDescription = "${transaction.merchant}: ${amountPrefix}₹${"%.2f".format(transaction.amount)}, ${transaction.date}"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type indicator icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCredit) financeColors.creditContainer else financeColors.debitContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = if (isCredit) "Credit" else "Debit",
                    tint = amountColor,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clickable { onMerchantClick(transaction.merchant) }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onCategoryClick(transaction.category) }
                    )
                    Text(
                        text = " · ${transaction.date} · $formattedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
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
}
