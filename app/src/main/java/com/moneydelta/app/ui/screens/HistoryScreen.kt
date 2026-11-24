package com.moneydelta.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.moneydelta.app.data.CycleSummary
import com.moneydelta.app.data.db.TransactionEntity
import com.moneydelta.app.domain.MoneyFormatter
import com.moneydelta.app.ui.components.PeriodSummaryHeader
import com.moneydelta.app.ui.components.TransactionEditorDialog
import com.moneydelta.app.ui.components.TransactionListItem
import com.moneydelta.app.ui.theme.LocalSemanticColors
import com.moneydelta.app.ui.theme.tabular
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    summaries: List<CycleSummary>,
    currencySymbol: String,
    selectedMonth: YearMonth?,
    selectedCycleData: CyclePeriodData?,
    onSelectMonth: (YearMonth?) -> Unit,
    onToggleIgnored: (Long, Boolean) -> Unit,
    onUpdateTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = selectedMonth != null) {
        onSelectMonth(null)
    }

    val detailData = if (selectedMonth != null) selectedCycleData else null

    Crossfade(
        targetState = detailData,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        modifier = modifier.fillMaxSize(),
        label = "history_detail_transition"
    ) { activeDetail ->
        if (activeDetail != null) {
            HistoryDetailView(
                cycleData = activeDetail,
                onBack = { onSelectMonth(null) },
                onToggleIgnored = onToggleIgnored,
                onUpdateTransaction = onUpdateTransaction,
                onDeleteTransaction = onDeleteTransaction
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
            ) {
                item(key = "history_title") {
                    Text(
                        text = "Past Cycles",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                items(
                    items = summaries,
                    key = { it.yearMonth.toString() }
                ) { summary ->
                    HistorySummaryRow(
                        summary = summary,
                        currencySymbol = currencySymbol,
                        onClick = { onSelectMonth(summary.yearMonth) }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryRow(
    summary: CycleSummary,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val deltaColor = when {
        summary.netDeltaCents > 0L -> semantic.positive
        summary.netDeltaCents < 0L -> semantic.negative
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "In +${MoneyFormatter.formatUnsigned(summary.incomeCents, currencySymbol)} · Out -${MoneyFormatter.formatUnsigned(summary.expenseCents, currencySymbol)} · ${summary.includedCount} tx",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = MoneyFormatter.formatSigned(summary.netDeltaCents, currencySymbol),
            style = MaterialTheme.typography.titleMedium.tabular().copy(fontWeight = FontWeight.SemiBold),
            color = deltaColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailView(
    cycleData: CyclePeriodData,
    onBack: () -> Unit,
    onToggleIgnored: (Long, Boolean) -> Unit,
    onUpdateTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (Long) -> Unit
) {
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    val symbol = cycleData.preferences.currencySymbol

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(cycleData.summary.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to history"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "detail_header") {
                PeriodSummaryHeader(
                    summary = cycleData.summary,
                    currencySymbol = symbol
                )
            }

            if (cycleData.transactions.isEmpty()) {
                item(key = "detail_empty") {
                    Text(
                        text = "No transactions in this cycle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            } else {
                items(
                    items = cycleData.transactions,
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
        }
    }

    editingTransaction?.let { current ->
        TransactionEditorDialog(
            initialTransaction = current,
            currencySymbol = symbol,
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
