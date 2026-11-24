package com.moneydelta.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.moneydelta.app.data.prefs.UserPreferences
import com.moneydelta.app.domain.BillingCycleCalculator
import com.moneydelta.app.ui.theme.tabular
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val syncTimestampFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.US)

private data class CurrencyPreset(val symbol: String, val code: String)

private val currencyPresets = listOf(
    CurrencyPreset("$", "USD"),
    CurrencyPreset("€", "EUR"),
    CurrencyPreset("£", "GBP"),
    CurrencyPreset("₽", "RUB")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    isSyncing: Boolean,
    onSetCurrency: (symbol: String, code: String) -> Unit,
    onSetCycleStartDay: (Int) -> Unit,
    onRescanLast12Months: () -> Unit,
    modifier: Modifier = Modifier
) {
    var symbolInput by remember(preferences.currencySymbol) {
        mutableStateOf(preferences.currencySymbol)
    }
    var codeInput by remember(preferences.currencyCode) {
        mutableStateOf(preferences.currencyCode)
    }

    val lastSyncLabel = remember(preferences.lastSyncMillis) {
        if (preferences.lastSyncMillis <= 0L) {
            "Not synced yet"
        } else {
            Instant.ofEpochMilli(preferences.lastSyncMillis)
                .atZone(ZoneId.systemDefault())
                .format(syncTimestampFormatter)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "settings_title") {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        item(key = "currency_section") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Primary Currency",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "Only SMS amounts in this currency are tracked; other currencies are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    currencyPresets.forEach { preset ->
                        val selected = preferences.currencyCode.equals(preset.code, ignoreCase = true) &&
                            preferences.currencySymbol == preset.symbol
                        FilterChip(
                            selected = selected,
                            onClick = { onSetCurrency(preset.symbol, preset.code) },
                            label = { Text("${preset.code} (${preset.symbol})") }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = symbolInput,
                        onValueChange = { symbolInput = it },
                        label = { Text("Symbol") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase().take(3) },
                        label = { Text("Code (ISO)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (symbolInput.isNotBlank() && codeInput.isNotBlank()) {
                                onSetCurrency(symbolInput, codeInput)
                            }
                        }
                    ) {
                        Text("Apply")
                    }
                }
            }
        }

        item(key = "divider_1") {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }

        item(key = "cycle_section") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Monthly Cycle Start Day",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "Default is 1st of each calendar month. Pick 1–28 to align with your payday.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val next = BillingCycleCalculator.clampCycleStartDay(
                                preferences.cycleStartDay - 1
                            )
                            onSetCycleStartDay(next)
                        },
                        enabled = preferences.cycleStartDay > 1
                    ) {
                        Text("-")
                    }
                    Text(
                        text = "Day ${preferences.cycleStartDay}",
                        style = MaterialTheme.typography.titleMedium.tabular().copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    OutlinedButton(
                        onClick = {
                            val next = BillingCycleCalculator.clampCycleStartDay(
                                preferences.cycleStartDay + 1
                            )
                            onSetCycleStartDay(next)
                        },
                        enabled = preferences.cycleStartDay < 28
                    ) {
                        Text("+")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (preferences.cycleStartDay != 1) {
                        TextButton(onClick = { onSetCycleStartDay(1) }) {
                            Text("Reset to 1st")
                        }
                    }
                }
            }
        }

        item(key = "divider_2") {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }

        item(key = "sync_section") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "SMS Inbox Sync",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "Last synced: $lastSyncLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRescanLast12Months,
                    enabled = !isSyncing
                ) {
                    Text(if (isSyncing) "Scanning SMS..." else "Re-scan Last 12 Months SMS")
                }
            }
        }

        item(key = "privacy_badge") {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "100% Offline Privacy",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "MoneyDelta declares zero INTERNET permission. All SMS messages, bank rules, and ledger data remain strictly on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
