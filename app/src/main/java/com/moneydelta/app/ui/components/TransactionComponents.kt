package com.moneydelta.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneydelta.app.data.CycleSummary
import com.moneydelta.app.data.db.TransactionEntity
import com.moneydelta.app.domain.MoneyFormatter
import com.moneydelta.app.domain.model.TransactionType
import com.moneydelta.app.domain.parser.SmsParser
import com.moneydelta.app.ui.theme.LocalSemanticColors
import com.moneydelta.app.ui.theme.tabular
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val txDateFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)

@Composable
fun PeriodSummaryHeader(
    summary: CycleSummary,
    currencySymbol: String,
    modifier: Modifier = Modifier,
    trailingAction: @Composable (() -> Unit)? = null
) {
    val semantic = LocalSemanticColors.current
    val deltaColor = when {
        summary.netDeltaCents > 0L -> semantic.positive
        summary.netDeltaCents < 0L -> semantic.negative
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = summary.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.includedCount} counted · ${summary.totalCount} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (trailingAction != null) {
                    trailingAction()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = MoneyFormatter.formatSigned(summary.netDeltaCents, currencySymbol),
                style = MaterialTheme.typography.headlineLarge.tabular().copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = deltaColor
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "+${MoneyFormatter.formatUnsigned(summary.incomeCents, currencySymbol)}",
                        style = MaterialTheme.typography.titleMedium.tabular().copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = semantic.positive
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "-${MoneyFormatter.formatUnsigned(summary.expenseCents, currencySymbol)}",
                        style = MaterialTheme.typography.titleMedium.tabular().copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = semantic.negative
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionListItem(
    transaction: TransactionEntity,
    currencySymbol: String,
    onToggleIgnored: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(transaction.id) { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current

    val contentAlpha by animateFloatAsState(
        targetValue = if (transaction.isIgnored) 0.45f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "tx_ignore_alpha"
    )

    val amountText = when (transaction.type) {
        TransactionType.INCOME -> "+${MoneyFormatter.formatUnsigned(transaction.amountCents, currencySymbol)}"
        TransactionType.EXPENSE -> "-${MoneyFormatter.formatUnsigned(transaction.amountCents, currencySymbol)}"
    }
    val amountColor = when {
        transaction.isIgnored -> MaterialTheme.colorScheme.onSurfaceVariant
        transaction.type == TransactionType.INCOME -> semantic.positive
        else -> semantic.negative
    }
    val dateLabel = remember(transaction.timestampMillis) {
        Instant.ofEpochMilli(transaction.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .format(txDateFormatter)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
            .clickable { expanded = !expanded }
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (transaction.isIgnored) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(transaction.sender)
                        append(" · ")
                        append(dateLabel)
                        if (transaction.isIgnored) append(" · Ignored")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium.tabular().copy(
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (transaction.isIgnored) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = amountColor
            )

            IconButton(onClick = { onToggleIgnored(!transaction.isIgnored) }) {
                Icon(
                    imageVector = if (transaction.isIgnored) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
                    contentDescription = if (transaction.isIgnored) "Include transaction" else "Ignore transaction",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                expandVertically(tween(240, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                shrinkVertically(tween(220, easing = FastOutSlowInEasing))
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                if (!transaction.rawBody.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = transaction.rawBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("Edit")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorDialog(
    initialTransaction: TransactionEntity?,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, type: TransactionType, description: String) -> Unit
) {
    val initialAmountText = remember(initialTransaction) {
        if (initialTransaction == null) {
            ""
        } else {
            BigDecimal.valueOf(initialTransaction.amountCents).movePointLeft(2).toPlainString()
        }
    }
    var amountInput by remember(initialTransaction) { mutableStateOf(initialAmountText) }
    var selectedType by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.type ?: TransactionType.EXPENSE)
    }
    var descriptionInput by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.description ?: "")
    }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialTransaction == null) "Add Transaction" else "Edit Transaction")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Expense")
                    }
                    SegmentedButton(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Income")
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        amountError = null
                    },
                    label = { Text("Amount ($currencySymbol)") },
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = SmsParser.parseAmountToCents(amountInput)
                    if (cents == null || cents <= 0L) {
                        amountError = "Enter a valid amount greater than 0"
                        return@TextButton
                    }
                    onSave(cents, selectedType, descriptionInput)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
