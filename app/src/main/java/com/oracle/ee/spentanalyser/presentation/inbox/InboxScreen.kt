package com.oracle.ee.spentanalyser.presentation.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracle.ee.spentanalyser.domain.model.ParseStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: InboxViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var isFiltersExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Inbox (30 Days)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Header Sticky Frame
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isFiltersExpanded = !isFiltersExpanded }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Search Filters",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (isFiltersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Filters",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    AnimatedVisibility(visible = isFiltersExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "By default, we only fetch known banks. Add extra keywords/senders below to discover unparsed messages in your inbox.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Sender Input Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = state.senderInput,
                                    onValueChange = viewModel::onSenderInputChanged,
                                    label = { Text("Add Sender (e.g. ZOMATO)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = viewModel::addCustomSender) {
                                            Icon(Icons.Default.Add, "Add Sender")
                                        }
                                    }
                                )
                            }

                            // Keyword Input Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = state.keywordInput,
                                    onValueChange = viewModel::onKeywordInputChanged,
                                    label = { Text("Add Keyword (e.g. Swiggy)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = viewModel::addCustomKeyword) {
                                            Icon(Icons.Default.Add, "Add Keyword")
                                        }
                                    }
                                )
                            }

                            // Active Chips Row (Senders)
                            if (state.customSenders.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.customSenders.forEach { sender ->
                                        InputChip(
                                            selected = true,
                                            onClick = { viewModel.removeCustomSender(sender) },
                                            label = { Text(sender) },
                                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", Modifier.size(16.dp)) }
                                        )
                                    }
                                }
                            }

                            // Active Chips Row (Keywords)
                            if (state.customKeywords.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.customKeywords.forEach { keyword ->
                                        InputChip(
                                            selected = true,
                                            onClick = { viewModel.removeCustomKeyword(keyword) },
                                            label = { Text(keyword) },
                                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", Modifier.size(16.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No messages found matching criteria.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.messages, key = { it.sms.uniqueHash }) { item ->
                        InboxSmsCard(item = item, onParseClick = { viewModel.parseMessage(item.sms.uniqueHash) })
                    }
                }
            }
        }
    }
}

@Composable
fun InboxSmsCard(item: InboxSmsItem, onParseClick: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()) }
    val timeString = formatter.format(Date(item.sms.timestamp))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.sms.sender,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Status Badge
                val (color, text) = when (item.status) {
                    ParseStatus.SUCCESS -> MaterialTheme.colorScheme.primary to "Parsed"
                    ParseStatus.MANUAL_REVIEW -> MaterialTheme.colorScheme.tertiary to "Review"
                    ParseStatus.ERROR -> MaterialTheme.colorScheme.error to "Failed"
                    ParseStatus.IGNORED -> MaterialTheme.colorScheme.secondary to "Ignored"
                    null -> MaterialTheme.colorScheme.outline to "Not Parsed"
                    ParseStatus.PENDING -> MaterialTheme.colorScheme.secondary to "Pending"
                }

                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = text,
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = timeString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = item.sms.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (item.status == null || item.status == ParseStatus.ERROR || item.status == ParseStatus.IGNORED) {
                Button(
                    onClick = onParseClick,
                    enabled = !item.isParsing,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (item.isParsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Parsing...")
                    } else {
                        Text("Force Parse Now")
                    }
                }
            }
        }
    }
}
