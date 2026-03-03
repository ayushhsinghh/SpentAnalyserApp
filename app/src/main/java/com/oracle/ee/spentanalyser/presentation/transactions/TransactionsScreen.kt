package com.oracle.ee.spentanalyser.presentation.transactions

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.domain.model.Transaction
import com.oracle.ee.spentanalyser.presentation.common.EmptyStateView
import com.oracle.ee.spentanalyser.presentation.common.TransactionEditDialog
import com.oracle.ee.spentanalyser.ui.theme.SpentAnalyserThemeExtensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel, modifier: Modifier = Modifier) {
    val transactions by viewModel.transactions.collectAsState()

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
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
        var deletingTransaction by remember { mutableStateOf<Transaction?>(null) }

        if (transactions.isEmpty()) {
            EmptyStateView(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "No transactions yet",
                subtitle = "Your parsed bank transactions will appear here.\nLong-press any item to edit.",
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 8.dp, bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(
                    transactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        onEditClick = { editingTransaction = transaction },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        // Edit dialog
        editingTransaction?.let { tx ->
            TransactionEditDialog(
                initialAmount = tx.amount.toString(),
                initialMerchant = tx.merchant,
                initialDate = tx.date,
                initialType = tx.type,
                onDismiss = { editingTransaction = null },
                onSave = { newAmount, newMerchant, newDate, newType ->
                    viewModel.updateExistingTransaction(
                        tx.copy(amount = newAmount, merchant = newMerchant, date = newDate, type = newType)
                    )
                    editingTransaction = null
                },
                onDelete = {
                    deletingTransaction = tx
                    editingTransaction = null
                }
            )
        }

        // Delete confirmation
        deletingTransaction?.let { tx ->
            AlertDialog(
                onDismissRequest = { deletingTransaction = null },
                icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete Transaction") },
                text = {
                    Text("Permanently delete this ₹${"%.2f".format(tx.amount)} transaction with ${tx.merchant}?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteTransaction(tx.id)
                            deletingTransaction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingTransaction = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionCard(
    transaction: Transaction,
    onEditClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val financeColors = SpentAnalyserThemeExtensions.financeColors
    val isCredit = transaction.type.equals("CREDIT", ignoreCase = true)
    val amountColor = if (isCredit) financeColors.credit else financeColors.debit
    val amountPrefix = if (isCredit) "+" else "−"

    val timeSdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = timeSdf.format(Date(transaction.timestamp))

    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEditClick()
                }
            )
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${transaction.date} · $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix₹${"%.2f".format(transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                // Visual hint for long-press edit
                Text(
                    text = "Hold to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}
