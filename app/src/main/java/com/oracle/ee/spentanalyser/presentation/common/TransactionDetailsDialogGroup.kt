package com.oracle.ee.spentanalyser.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.domain.model.Transaction

@Composable
fun TransactionDetailsDialogGroup(
    selectedTransaction: Transaction?,
    selectedTransactionSms: String?,
    isFetchingSms: Boolean,
    onDismissDetails: () -> Unit,
    onEditTransaction: (Transaction, String?) -> Unit,
    onDeleteTransaction: (Int) -> Unit
) {
    if (selectedTransaction == null) return

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var deletingTransaction by remember { mutableStateOf<Transaction?>(null) }

    // If we're fully in edit or delete mode, don't show the basic details
    val showDetails = editingTransaction == null && deletingTransaction == null

    if (showDetails) {
        if (isFetchingSms) {
            AlertDialog(
                onDismissRequest = onDismissDetails,
                title = { Text("Loading Details") },
                text = { CircularProgressIndicator() },
                confirmButton = {}
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismissDetails,
                icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                title = { Text("Transaction Details") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Source SMS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedTransactionSms ?: "SMS body not found.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Parsed Data",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Merchant: \n${selectedTransaction.merchant}", style = MaterialTheme.typography.bodySmall)
                            Text("Amount: \n₹${"%.2f".format(selectedTransaction.amount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Category: ${selectedTransaction.category}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        editingTransaction = selectedTransaction
                    }) {
                        Text("Edit")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { deletingTransaction = selectedTransaction },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
            )
        }
    }

    // Edit dialog
    editingTransaction?.let { tx ->
        TransactionEditDialog(
            initialAmount = tx.amount.toString(),
            initialMerchant = tx.merchant,
            initialCategory = tx.category,
            initialDate = tx.date,
            initialType = tx.type,
            onDismiss = { editingTransaction = null },
            onSave = { newAmount, newMerchant, newCategory, newDate, newType ->
                onEditTransaction(
                    tx.copy(
                        amount = newAmount, 
                        merchant = newMerchant, 
                        category = newCategory,
                        date = newDate, 
                        type = newType
                    ),
                    tx.merchant // Pass original merchant
                )
                editingTransaction = null
                onDismissDetails()
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
                        onDeleteTransaction(tx.id)
                        deletingTransaction = null
                        onDismissDetails()
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
