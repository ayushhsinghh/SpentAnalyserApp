package com.oracle.ee.spentanalyser.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditDialog(
    initialAmount: String = "",
    initialMerchant: String = "",
    initialCategory: String = "Unknown",
    initialDate: String = "",
    initialType: String = "DEBIT",
    onDismiss: () -> Unit,
    onSave: (amount: Double, merchant: String, category: String, date: String, type: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var merchant by remember { mutableStateOf(initialMerchant) }
    var category by remember { mutableStateOf(initialCategory) }
    var date by remember { mutableStateOf(initialDate) }
    var type by remember { mutableStateOf(initialType) }

    var expandedTypeDropdown by remember { mutableStateOf(false) }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    val validCategories = remember {
        listOf("Food", "Grocery", "Shopping", "Travel", "Fuel", "Investment", "EMI", "Utilities", "Recharge", "Entertainment", "Insurance", "Subscription", "Health", "Education", "Income", "Credit Card", "Payment", "Unknown")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialAmount.isEmpty()) "Add Manual Transaction" else "Edit Transaction") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expandedCategoryDropdown,
                    onExpandedChange = { expandedCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategoryDropdown,
                        onDismissRequest = { expandedCategoryDropdown = false },
                        modifier = Modifier.heightIn(max = 240.dp) // Limits dropdown height visually
                    ) {
                        validCategories.forEach { selCat ->
                            DropdownMenuItem(
                                text = { Text(selCat) },
                                onClick = {
                                    category = selCat
                                    expandedCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedTypeDropdown,
                    onExpandedChange = { expandedTypeDropdown = it }
                ) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTypeDropdown,
                        onDismissRequest = { expandedTypeDropdown = false }
                    ) {
                        listOf("DEBIT", "CREDIT").forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    type = selectionOption
                                    expandedTypeDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull()
                    if (parsedAmount != null && merchant.isNotBlank() && date.isNotBlank()) {
                        onSave(parsedAmount, merchant.trim(), category, date.trim(), type)
                    }
                },
                enabled = amount.isNotBlank() && merchant.isNotBlank() && date.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
