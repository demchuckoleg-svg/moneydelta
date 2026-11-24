package com.moneydelta.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneydelta.app.sms.SmsPermissions
import com.moneydelta.app.ui.MainViewModel
import com.moneydelta.app.ui.screens.DashboardScreen
import com.moneydelta.app.ui.screens.HistoryScreen
import com.moneydelta.app.ui.screens.RulesScreen
import com.moneydelta.app.ui.screens.SettingsScreen
import com.moneydelta.app.ui.screens.StatsScreen
import com.moneydelta.app.ui.theme.MoneyDeltaTheme

private enum class AppTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Outlined.AccountBalanceWallet),
    HISTORY("History", Icons.Outlined.History),
    STATS("Stats", Icons.Outlined.BarChart),
    RULES("Rules", Icons.Outlined.Tune),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyDeltaTheme {
                MoneyDeltaAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun MoneyDeltaAppRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentCycleData by viewModel.currentCycleData.collectAsStateWithLifecycle()
    val historySummaries by viewModel.historySummaries.collectAsStateWithLifecycle()
    val selectedHistoryMonth by viewModel.selectedHistoryMonth.collectAsStateWithLifecycle()
    val selectedHistoryCycleData by viewModel.selectedHistoryCycleData.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val recentInboxSms by viewModel.recentInboxSms.collectAsStateWithLifecycle()
    val statsUiState by viewModel.statsUiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD) }
    var hasSmsPermissions by remember {
        mutableStateOf(SmsPermissions.hasAllSmsPermissions(context))
    }
    var hasReadSmsPermission by remember {
        mutableStateOf(SmsPermissions.hasReadSms(context))
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val grantedAll = SmsPermissions.hasAllSmsPermissions(context)
        val grantedRead = SmsPermissions.hasReadSms(context)
        val newlyGranted = !hasSmsPermissions && grantedAll
        hasSmsPermissions = grantedAll
        hasReadSmsPermission = grantedRead
        if (newlyGranted) {
            viewModel.syncLast12Months()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermissions = SmsPermissions.hasAllSmsPermissions(context)
                hasReadSmsPermission = SmsPermissions.hasReadSms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearStatusMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            if (tab != AppTab.HISTORY) {
                                viewModel.selectHistoryMonth(null)
                            }
                            selectedTab = tab
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                label = "main_tab_crossfade"
            ) { tab ->
                when (tab) {
                    AppTab.DASHBOARD -> {
                        DashboardScreen(
                            cycleData = currentCycleData,
                            hasSmsPermissions = hasSmsPermissions,
                            isSyncing = isSyncing,
                            onRequestPermissions = {
                                permissionLauncher.launch(SmsPermissions.requiredPermissions)
                            },
                            onOpenAppSettings = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            },
                            onSyncNow = { viewModel.syncLast12Months() },
                            onToggleIgnored = { id, ignored ->
                                viewModel.setTransactionIgnored(id, ignored)
                            },
                            onAddManual = { amountCents, type, description ->
                                viewModel.addManualTransaction(
                                    amountCents = amountCents,
                                    type = type,
                                    timestampMillis = System.currentTimeMillis(),
                                    description = description
                                )
                            },
                            onUpdateTransaction = { tx -> viewModel.updateTransaction(tx) },
                            onDeleteTransaction = { id -> viewModel.deleteTransaction(id) }
                        )
                    }

                    AppTab.HISTORY -> {
                        HistoryScreen(
                            summaries = historySummaries,
                            currencySymbol = preferences.currencySymbol,
                            selectedMonth = selectedHistoryMonth,
                            selectedCycleData = selectedHistoryCycleData,
                            onSelectMonth = { ym -> viewModel.selectHistoryMonth(ym) },
                            onToggleIgnored = { id, ignored ->
                                viewModel.setTransactionIgnored(id, ignored)
                            },
                            onUpdateTransaction = { tx -> viewModel.updateTransaction(tx) },
                            onDeleteTransaction = { id -> viewModel.deleteTransaction(id) }
                        )
                    }

                    AppTab.STATS -> {
                        StatsScreen(
                            uiState = statsUiState,
                            onSelectPreset = { preset -> viewModel.setStatsPreset(preset) },
                            onSelectCustomRange = { start, end ->
                                viewModel.setStatsCustomRange(start, end)
                            },
                            onSelectSender = { sender -> viewModel.setStatsSenderFilter(sender) }
                        )
                    }

                    AppTab.RULES -> {
                        RulesScreen(
                            rules = rules,
                            preferences = preferences,
                            hasReadSmsPermission = hasReadSmsPermission,
                            recentInboxSms = recentInboxSms,
                            onLoadRecentInbox = { viewModel.loadRecentInboxSms() },
                            onSaveRule = { rule -> viewModel.saveRule(rule) },
                            onToggleRule = { id, enabled -> viewModel.setRuleEnabled(id, enabled) },
                            onDeleteRule = { id -> viewModel.deleteRule(id) }
                        )
                    }

                    AppTab.SETTINGS -> {
                        SettingsScreen(
                            preferences = preferences,
                            isSyncing = isSyncing,
                            onSetCurrency = { symbol, code ->
                                viewModel.setCurrency(symbol, code)
                            },
                            onSetCycleStartDay = { day -> viewModel.setCycleStartDay(day) },
                            onRescanLast12Months = { viewModel.syncLast12Months() }
                        )
                    }
                }
            }
        }
    }
}
