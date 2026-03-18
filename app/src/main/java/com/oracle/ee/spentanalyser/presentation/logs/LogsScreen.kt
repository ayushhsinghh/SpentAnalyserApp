package com.oracle.ee.spentanalyser.presentation.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import com.oracle.ee.spentanalyser.domain.model.SmsLog
import com.oracle.ee.spentanalyser.presentation.common.EmptyStateView
import com.oracle.ee.spentanalyser.presentation.common.TransactionEditDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: LogsViewModel, modifier: Modifier = Modifier) {
    val logs by viewModel.smsLogs.collectAsState()
    val isRetryingLogHash by viewModel.isRetrying.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Success", "Failed", "Ignored")

    val filteredLogs = remember(logs, selectedTab) {
        when (selectedTab) {
            1 -> logs.filter { it.status == ParseStatus.SUCCESS }
            2 -> logs.filter { it.status == ParseStatus.ERROR || it.status == ParseStatus.MANUAL_REVIEW }
            3 -> logs.filter { it.status == ParseStatus.IGNORED }
            else -> logs
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Processing Hub", fontWeight = FontWeight.Bold)
                            Text(
                                "${logs.size} messages processed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            modifier = Modifier.height(48.dp) // 48dp touch target
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        var editingLog by remember { mutableStateOf<SmsLog?>(null) }
        var retryingLog by remember { mutableStateOf<SmsLog?>(null) }

        if (filteredLogs.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Sms,
                title = "No entries found",
                subtitle = when (selectedTab) {
                    1 -> "No successfully parsed messages yet"
                    2 -> "No failed parses — everything is working!"
                    3 -> "No ignored messages"
                    else -> "SMS processing logs will appear here"
                },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 8.dp, bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(
                    filteredLogs,
                    key = { it.uniqueHash }
                ) { log ->
                    ExpandableLogCard(
                        log = log,
                        isRetrying = isRetryingLogHash == log.uniqueHash,
                        onManualEdit = { editingLog = log },
                        onRetry = { retryingLog = log },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        // Manual edit dialog
        editingLog?.let { log ->
            var initialAmount = ""
            var initialMerchant = ""
            var initialCategory = "Unknown"

            try {
                if (!log.rawLlmOutput.isNullOrBlank()) {
                    val raw = log.rawLlmOutput.trim()
                    Regex("\"amount\"\\s*:\\s*([0-9.]+)").find(raw)?.groupValues?.get(1)?.let { initialAmount = it }
                    Regex("\"merchant\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)?.let { initialMerchant = it }
                    Regex("\"category\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)?.let { initialCategory = it }
                }
            } catch (_: Exception) {}

            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = log.timestamp
            val initialDate = java.text.SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)

            TransactionEditDialog(
                initialAmount = initialAmount,
                initialMerchant = initialMerchant,
                initialCategory = initialCategory,
                initialDate = initialDate,
                onDismiss = { editingLog = null },
                onSave = { amount, merchant, category, date, type ->
                    viewModel.saveManualTransaction(
                        amount = amount,
                        merchant = merchant,
                        category = category,
                        date = date,
                        type = type,
                        sourceHash = log.uniqueHash
                    )
                    editingLog = null
                }
            )
        }

        retryingLog?.let { log ->
            RetryPromptDialog(
                onDismiss = { retryingLog = null },
                onRetry = { hint ->
                    viewModel.retryParsing(log, hint)
                    retryingLog = null
                    selectedTab = 0 // Move back to all to see status change
                }
            )
        }
    }
}

@Composable
fun RetryPromptDialog(
    onDismiss: () -> Unit,
    onRetry: (String) -> Unit
) {
    var promptHint by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Retry Parsing")
        },
        text = {
            Column {
                Text(
                    "You can provide hints to help the AI extract the correct data. Try telling it what you want:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = promptHint,
                    onValueChange = { promptHint = it },
                    label = { Text("Custom Prompt / Hint") },
                    placeholder = { Text("e.g. The amount is 500 and the merchant is Swiggy") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRetry(promptHint) }
            ) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ─── Expandable Log Card (Progressive Disclosure) ───
@Composable
fun ExpandableLogCard(
    log: SmsLog,
    isRetrying: Boolean = false,
    onManualEdit: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val status = log.status

    // Status visual properties
    val containerColor = when (status) {
        ParseStatus.SUCCESS -> MaterialTheme.colorScheme.surface
        ParseStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ParseStatus.MANUAL_REVIEW -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ParseStatus.IGNORED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ParseStatus.PENDING -> MaterialTheme.colorScheme.surface
    }

    val statusIcon = when (status) {
        ParseStatus.SUCCESS -> Icons.Default.CheckCircle
        ParseStatus.ERROR -> Icons.Default.Warning
        ParseStatus.MANUAL_REVIEW -> Icons.Default.Edit
        ParseStatus.IGNORED -> Icons.Default.Block
        ParseStatus.PENDING -> Icons.Default.HourglassEmpty
    }

    val statusLabel = when (status) {
        ParseStatus.SUCCESS -> "Parsed successfully"
        ParseStatus.ERROR -> "Parse failed"
        ParseStatus.MANUAL_REVIEW -> "Needs review"
        ParseStatus.IGNORED -> "Ignored (not a transaction)"
        ParseStatus.PENDING -> "Pending"
    }

    val iconTint = when (status) {
        ParseStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ParseStatus.ERROR -> MaterialTheme.colorScheme.error
        ParseStatus.MANUAL_REVIEW -> MaterialTheme.colorScheme.secondary
        ParseStatus.IGNORED -> MaterialTheme.colorScheme.outline
        ParseStatus.PENDING -> MaterialTheme.colorScheme.primary
    }

    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val dateStr = sdf.format(Date(log.timestamp))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // ── Header (always visible, clickable to expand) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp)
                    .semantics { contentDescription = "$statusLabel. From ${log.sender} on $dateStr. Tap to ${if (isExpanded) "collapse" else "expand"} details." },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusLabel,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${log.sender} · $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // ── Expanded detail (progressive disclosure) ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Raw SMS
                    Text(
                        text = "SMS Message",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = log.body,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    // Error message
                    if ((status == ParseStatus.ERROR || status == ParseStatus.MANUAL_REVIEW) && log.errorMessage != null) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = log.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (status == ParseStatus.ERROR || status == ParseStatus.MANUAL_REVIEW) {
                                TextButton(
                                    onClick = onRetry,
                                    enabled = !isRetrying,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    if (isRetrying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Retrying...")
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry with Hint")
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            FilledTonalButton(
                                onClick = onManualEdit,
                                modifier = Modifier.height(48.dp), // 48dp touch target
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (status == ParseStatus.MANUAL_REVIEW) "Edit Transaction" else "Manual Entry")
                            }
                        }
                    }

                    // LLM Output
                    if (log.rawLlmOutput != null && status != ParseStatus.IGNORED) {
                        Text(
                            text = "Gemma Output",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = log.rawLlmOutput.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
