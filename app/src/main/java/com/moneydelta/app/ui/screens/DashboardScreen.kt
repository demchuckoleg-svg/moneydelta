package com.moneydelta.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneydelta.app.data.CyclePeriodData
import com.moneydelta.app.data.db.TransactionEntity
import com.moneydelta.app.domain.model.TransactionType
import com.moneydelta.app.ui.components.PeriodSummaryHeader
import com.moneydelta.app.ui.components.TransactionEditorDialog
import com.moneydelta.app.ui.components.TransactionListItem

@Composable
fun DashboardScreen(
    cycleData: CyclePeriodData?,
    hasSmsPermissions: Boolean,
    isSyncing: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleIgnored: (Long, Boolean) -> Unit,
    onAddManual: (amountCents: Long, type: TransactionType, description: String) -> Unit,
    onUpdateTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add manual transaction"
                )
            }
        }
    ) { innerPadding ->
        if (cycleData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val transactions = cycleData.transactions
        val symbol = cycleData.preferences.currencySymbol

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "permission_banner") {
                AnimatedVisibility(
                    visible = !hasSmsPermissions,
                    enter = fadeIn(tween(240, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing))
                ) {
                    SmsPermissionBanner(
                        onGrantClick = onRequestPermissions,
                        onOpenSettingsClick = onOpenAppSettings
                    )
                }
            }

            item(key = "summary_header") {
                PeriodSummaryHeader(
                    summary = cycleData.summary,
                    currencySymbol = symbol,
                    trailingAction = {
                        OutlinedButton(
                            onClick = onSyncNow,
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(18.dp)
                                )
                                Text("Sync")
                            }
                        }
                    }
                )
            }

            item(key = "tx_section_header") {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (transactions.isEmpty()) {
                item(key = "empty_tx") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No transactions for this period",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Enable a bank rule in Rules and tap Sync, or add a manual transaction with +.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = transactions,
                    key = { it.id }
                ) { tx ->
                    TransactionListItem(
                        transaction = tx,
                        currencySymbol = symbol,
                        onToggleIgnored = { ignored -> onToggleIgnored(tx.id, ignored) },
                        onEdit = { editingTransaction = tx },
                        onDelete = { onDeleteTransaction(tx.id) }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                }
            }

            item(key = "fab_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showAddDialog) {
        TransactionEditorDialog(
            initialTransaction = null,
            currencySymbol = cycleData?.preferences?.currencySymbol ?: "$",
            onDismiss = { showAddDialog = false },
            onSave = { amountCents, type, description ->
                onAddManual(amountCents, type, description)
                showAddDialog = false
            }
        )
    }

    editingTransaction?.let { current ->
        TransactionEditorDialog(
            initialTransaction = current,
            currencySymbol = cycleData?.preferences?.currencySymbol ?: "$",
            onDismiss = { editingTransaction = null },
            onSave = { amountCents, type, description ->
                onUpdateTransaction(
                    current.copy(
                        amountCents = amountCents,
                        type = type,
                        description = description.trim().ifEmpty { current.description }
                    )
                )
                editingTransaction = null
            }
        )
    }
}

@Composable
private fun SmsPermissionBanner(
    onGrantClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SMS Permission Needed",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Grant Read & Receive SMS to track bank alerts offline. On sideloaded Android 13+ installs, if the prompt is blocked, open App Info → ⋮ → Allow restricted settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrantClick) {
                    Text("Grant SMS Access")
                }
                TextButton(onClick = onOpenAppSettings) {
                    Text("App Info")
                }
            }
        }
    }
}
