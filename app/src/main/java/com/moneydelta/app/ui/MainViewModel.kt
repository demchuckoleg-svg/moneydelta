package com.moneydelta.app.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneydelta.app.data.CyclePeriodData
import com.moneydelta.app.data.CycleSummary
import com.moneydelta.app.data.db.TransactionEntity
import com.moneydelta.app.data.prefs.UserPreferences
import com.moneydelta.app.di.AppContainer
import com.moneydelta.app.domain.StatisticsCalculator
import com.moneydelta.app.domain.StatisticsSnapshot
import com.moneydelta.app.domain.StatsRange
import com.moneydelta.app.domain.StatsRangePreset
import com.moneydelta.app.domain.model.BankRule
import com.moneydelta.app.domain.model.TransactionType
import com.moneydelta.app.sms.RawInboxSms
import com.moneydelta.app.widget.MoneyDeltaWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class StatsUiState(
    val snapshot: StatisticsSnapshot,
    val currencySymbol: String,
    val selectedRange: StatsRange,
    val selectedSender: String?,
    val availableSenders: List<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get(application)
    private val repository = container.ledgerRepository
    private val scanner = container.smsInboxScanner

    val currentCycleData: StateFlow<CyclePeriodData?> =
        repository.observeCurrentCycleData()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val historySummaries: StateFlow<List<CycleSummary>> =
        repository.observeHistorySummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<BankRule>> =
        repository.observeRules()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preferences: StateFlow<UserPreferences> =
        repository.preferencesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _selectedHistoryMonth = MutableStateFlow<YearMonth?>(null)
    val selectedHistoryMonth: StateFlow<YearMonth?> = _selectedHistoryMonth.asStateFlow()

    val selectedHistoryCycleData: StateFlow<CyclePeriodData?> =
        _selectedHistoryMonth
            .flatMapLatest { ym ->
                if (ym == null) flowOf(null) else repository.observeCycleData(ym)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _recentInboxSms = MutableStateFlow<List<RawInboxSms>>(emptyList())
    val recentInboxSms: StateFlow<List<RawInboxSms>> = _recentInboxSms.asStateFlow()

    private val _statsRange = MutableStateFlow<StatsRange>(
        StatsRange.Preset(StatsRangePreset.THREE_MONTHS)
    )
    private val _statsSenderFilter = MutableStateFlow<String?>(null)

    val statsUiState: StateFlow<StatsUiState?> =
        combine(
            repository.observeAllDomainTransactions(),
            repository.preferencesFlow,
            _statsRange,
            _statsSenderFilter
        ) { transactions, prefs, range, senderFilter ->
            val availableSenders = transactions
                .asSequence()
                .filter { !it.isIgnored && it.sender.isNotBlank() }
                .map { it.sender }
                .distinctBy { it.lowercase() }
                .sorted()
                .toList()

            val effectiveSender = senderFilter?.takeIf { filter ->
                availableSenders.any { it.equals(filter, ignoreCase = true) }
            }

            val snapshot = StatisticsCalculator.compute(
                transactions = transactions,
                range = range,
                senderFilter = effectiveSender,
                cycleStartDay = prefs.cycleStartDay
            )

            StatsUiState(
                snapshot = snapshot,
                currencySymbol = prefs.currencySymbol,
                selectedRange = range,
                selectedSender = effectiveSender,
                availableSenders = availableSenders
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStatsPreset(preset: StatsRangePreset) {
        if (preset != StatsRangePreset.CUSTOM) {
            _statsRange.value = StatsRange.Preset(preset)
        }
    }

    fun setStatsCustomRange(startDate: LocalDate, endDateInclusive: LocalDate) {
        _statsRange.value = StatsRange.Custom(startDate, endDateInclusive)
    }

    fun setStatsSenderFilter(sender: String?) {
        _statsSenderFilter.value = sender
    }

    fun selectHistoryMonth(yearMonth: YearMonth?) {
        _selectedHistoryMonth.value = yearMonth
    }

    fun syncLast12Months() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = scanner.scanLast12Months()
                MoneyDeltaWidget().updateAll(getApplication())
                _statusMessage.value = when {
                    result.permissionMissing -> "SMS permission is required to scan inbox"
                    result.newlyImportedCount > 0 ->
                        "Scanned ${result.scannedMessages} SMS, imported ${result.newlyImportedCount} new"
                    else -> "Scanned ${result.scannedMessages} SMS, no new transactions"
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun loadRecentInboxSms() {
        viewModelScope.launch {
            _recentInboxSms.value = scanner.readRecentInboxMessages(limit = 25)
        }
    }

    fun setTransactionIgnored(id: Long, ignored: Boolean) {
        viewModelScope.launch {
            repository.setTransactionIgnored(id, ignored)
        }
    }

    fun addManualTransaction(
        amountCents: Long,
        type: TransactionType,
        timestampMillis: Long,
        description: String
    ) {
        viewModelScope.launch {
            repository.addManualTransaction(amountCents, type, timestampMillis, description)
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun saveRule(rule: BankRule) {
        viewModelScope.launch {
            repository.saveRule(rule)
        }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setRuleEnabled(id, enabled)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    fun setCurrency(symbol: String, code: String) {
        viewModelScope.launch {
            repository.setCurrency(symbol, code)
        }
    }

    fun setCycleStartDay(day: Int) {
        viewModelScope.launch {
            repository.setCycleStartDay(day)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
