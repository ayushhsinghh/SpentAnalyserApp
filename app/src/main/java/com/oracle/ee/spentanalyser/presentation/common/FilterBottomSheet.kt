package com.oracle.ee.spentanalyser.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.domain.model.SortOption
import com.oracle.ee.spentanalyser.domain.model.TransactionFilterQuery

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentQuery: TransactionFilterQuery,
    onApply: (TransactionFilterQuery) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var queryState by remember { mutableStateOf(currentQuery) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp) // extra padding for nav bar
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Filter Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // --- Type Segmented Button ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transaction Type", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = queryState.type == null,
                        onClick = { queryState = queryState.copy(type = null) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) { Text("All") }
                    SegmentedButton(
                        selected = queryState.type == "DEBIT",
                        onClick = { queryState = queryState.copy(type = "DEBIT") },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) { Text("Debit") }
                    SegmentedButton(
                        selected = queryState.type == "CREDIT",
                        onClick = { queryState = queryState.copy(type = "CREDIT") },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) { Text("Credit") }
                }
            }

            // --- Sort Options ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort By", style = MaterialTheme.typography.titleMedium)
                val sortOptions = listOf(
                    SortOption.DATE_DESC to "Newest First",
                    SortOption.DATE_ASC to "Oldest First",
                    SortOption.AMOUNT_DESC to "Amount (High - Low)",
                    SortOption.AMOUNT_ASC to "Amount (Low - High)"
                )

                sortOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { (option, label) ->
                            FilterChip(
                                selected = queryState.sortBy == option,
                                onClick = { queryState = queryState.copy(sortBy = option) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Action Buttons ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        queryState = TransactionFilterQuery() // Reset
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = {
                        onApply(queryState)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }
}
