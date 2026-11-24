package com.moneydelta.app.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneydelta.app.data.prefs.UserPreferences
import com.moneydelta.app.domain.MoneyFormatter
import com.moneydelta.app.domain.model.BankRule
import com.moneydelta.app.domain.model.SmsParseOutcome
import com.moneydelta.app.domain.model.TransactionType
import com.moneydelta.app.domain.parser.SmsParser
import com.moneydelta.app.sms.RawInboxSms
import com.moneydelta.app.ui.theme.LocalSemanticColors
import com.moneydelta.app.ui.theme.tabular

@Composable
fun RulesScreen(
    rules: List<BankRule>,
    preferences: UserPreferences,
    hasReadSmsPermission: Boolean,
    recentInboxSms: List<RawInboxSms>,
    onLoadRecentInbox: () -> Unit,
    onSaveRule: (BankRule) -> Unit,
    onToggleRule: (Long, Boolean) -> Unit,
    onDeleteRule: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingRule by remember { mutableStateOf<BankRule?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showInboxPicker by remember { mutableStateOf(false) }

    var testSender by rememberSaveable { mutableStateOf("") }
    var testBody by rememberSaveable { mutableStateOf("") }

    val testOutcome = remember(testSender, testBody, rules, preferences) {
        if (testSender.isBlank() && testBody.isBlank()) {
            null
        } else {
            SmsParser.parseDetailed(
                sender = testSender,
                body = testBody,
                timestampMillis = System.currentTimeMillis(),
                rules = rules,
                primaryCurrencyCode = preferences.currencyCode,
                primaryCurrencySymbol = preferences.currencySymbol
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    isCreatingNew = true
                    editingRule = BankRule(
                        id = 0L,
                        name = "",
                        senderPattern = "",
                        incomeKeywords = "credited, received, refund",
                        expenseKeywords = "debited, spent, purchase, paid",
                        ignoreKeywords = "otp, verification code, declined",
                        amountRegex = "",
                        isEnabled = true
                    )
                }
            ) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = "Add bank rule")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "tester_card") {
                SmsRuleTesterCard(
                    sender = testSender,
                    body = testBody,
                    outcome = testOutcome,
                    currencySymbol = preferences.currencySymbol,
                    canPickFromInbox = hasReadSmsPermission,
                    onSenderChange = { testSender = it },
                    onBodyChange = { testBody = it },
                    onPickRecentClick = {
                        onLoadRecentInbox()
                        showInboxPicker = true
                    }
                )
            }

            item(key = "rules_header") {
                Text(
                    text = "Bank Rules",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (rules.isEmpty()) {
                item(key = "no_rules") {
                    Text(
                        text = "No bank rules configured yet. Tap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(
                    items = rules,
                    key = { it.id }
                ) { rule ->
                    RuleRow(
                        rule = rule,
                        onClick = {
                            isCreatingNew = false
                            editingRule = rule
                        },
                        onToggle = { enabled -> onToggleRule(rule.id, enabled) },
                        onDelete = { onDeleteRule(rule.id) }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
            }

            item(key = "rules_bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    editingRule?.let { rule ->
        RuleEditorDialog(
            initialRule = rule,
            isNew = isCreatingNew,
            onDismiss = { editingRule = null },
            onSave = { updated ->
                onSaveRule(updated)
                editingRule = null
            }
        )
    }

    if (showInboxPicker) {
        RecentSmsPickerDialog(
            messages = recentInboxSms,
            onDismiss = { showInboxPicker = false },
            onSelect = { sms ->
                testSender = sms.sender
                testBody = sms.body
                showInboxPicker = false
            }
        )
    }
}

@Composable
private fun SmsRuleTesterCard(
    sender: String,
    body: String,
    outcome: SmsParseOutcome?,
    currencySymbol: String,
    canPickFromInbox: Boolean,
    onSenderChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onPickRecentClick: () -> Unit
) {
    val semantic = LocalSemanticColors.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SMS Rule Tester",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (canPickFromInbox) {
                    OutlinedButton(onClick = onPickRecentClick) {
                        Text("Pick SMS")
                    }
                }
            }

            OutlinedTextField(
                value = sender,
                onValueChange = onSenderChange,
                label = { Text("Sender ID (e.g. Chase, T-Bank)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                label = { Text("SMS text") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            if (outcome != null) {
                val (statusTitle, statusDetail, color) = when (outcome) {
                    is SmsParseOutcome.Matched -> {
                        val tx = outcome.transaction
                        val prefix = if (tx.type == TransactionType.INCOME) "+" else "-"
                        val label = if (tx.type == TransactionType.INCOME) "INCOME" else "EXPENSE"
                        Triple(
                            "Matched: ${tx.ruleName} ($label)",
                            "$prefix${MoneyFormatter.formatUnsigned(tx.amountCents, currencySymbol)}",
                            if (tx.type == TransactionType.INCOME) semantic.positive else semantic.negative
                        )
                    }
                    is SmsParseOutcome.Ignored -> Triple(
                        "Ignored by ${outcome.ruleName}",
                        "Contains ignore keyword: \"${outcome.keyword}\"",
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is SmsParseOutcome.CurrencyMismatch -> Triple(
                        "Currency mismatch (${outcome.ruleName})",
                        "Detected \"${outcome.detectedCurrency}\", skipped for primary currency",
                        semantic.negative
                    )
                    is SmsParseOutcome.NoDirectionKeyword -> Triple(
                        "Sender matched (${outcome.ruleName})",
                        "No income or expense keyword found",
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is SmsParseOutcome.NoAmountFound -> Triple(
                        "No valid amount found (${outcome.ruleName})",
                        "Check SMS text or custom amount regex",
                        semantic.negative
                    )
                    SmsParseOutcome.NoMatchingRule -> Triple(
                        "No enabled rule matched sender",
                        "Enable a rule or add the sender pattern",
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = color
                )
                Text(
                    text = statusDetail,
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: BankRule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Sender: ${rule.senderPattern}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Delete rule",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = rule.isEnabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun RuleEditorDialog(
    initialRule: BankRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (BankRule) -> Unit
) {
    var name by remember(initialRule) { mutableStateOf(initialRule.name) }
    var senderPattern by remember(initialRule) { mutableStateOf(initialRule.senderPattern) }
    var incomeKeywords by remember(initialRule) { mutableStateOf(initialRule.incomeKeywords) }
    var expenseKeywords by remember(initialRule) { mutableStateOf(initialRule.expenseKeywords) }
    var ignoreKeywords by remember(initialRule) { mutableStateOf(initialRule.ignoreKeywords) }
    var amountRegex by remember(initialRule) { mutableStateOf(initialRule.amountRegex) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Bank Rule" else "Edit Bank Rule") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Rule name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = senderPattern,
                    onValueChange = {
                        senderPattern = it
                        errorText = null
                    },
                    label = { Text("Sender pattern (regex or comma list)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = incomeKeywords,
                    onValueChange = { incomeKeywords = it },
                    label = { Text("Income keywords (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expenseKeywords,
                    onValueChange = { expenseKeywords = it },
                    label = { Text("Expense keywords (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ignoreKeywords,
                    onValueChange = { ignoreKeywords = it },
                    label = { Text("Ignore keywords (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountRegex,
                    onValueChange = { amountRegex = it },
                    label = { Text("Optional amount regex (group 1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank() || senderPattern.isBlank()) {
                        errorText = "Rule name and sender pattern are required"
                        return@TextButton
                    }
                    onSave(
                        initialRule.copy(
                            name = name.trim(),
                            senderPattern = senderPattern.trim(),
                            incomeKeywords = incomeKeywords.trim(),
                            expenseKeywords = expenseKeywords.trim(),
                            ignoreKeywords = ignoreKeywords.trim(),
                            amountRegex = amountRegex.trim()
                        )
                    )
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

@Composable
private fun RecentSmsPickerDialog(
    messages: List<RawInboxSms>,
    onDismiss: () -> Unit,
    onSelect: (RawInboxSms) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick Recent Inbox SMS") },
        text = {
            if (messages.isEmpty()) {
                Text("No SMS messages found in inbox.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(messages, key = { "${it.id}_${it.timestampMillis}" }) { sms ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(sms) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = sms.sender,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = sms.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
