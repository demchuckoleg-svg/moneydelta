package com.moneydelta.app.data

import com.moneydelta.app.data.db.BankRuleDao
import com.moneydelta.app.data.db.BankRuleEntity
import com.moneydelta.app.data.db.TransactionDao
import com.moneydelta.app.data.db.TransactionEntity
import com.moneydelta.app.data.prefs.UserPreferences
import com.moneydelta.app.data.prefs.UserPreferencesRepository
import com.moneydelta.app.domain.BillingCycleCalculator
import com.moneydelta.app.domain.model.BankRule
import com.moneydelta.app.domain.model.LedgerTransaction
import com.moneydelta.app.domain.model.ParsedSmsTransaction
import com.moneydelta.app.domain.model.TransactionType
import com.moneydelta.app.domain.parser.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

data class CycleSummary(
    val yearMonth: YearMonth,
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val netDeltaCents: Long,
    val includedCount: Int,
    val totalCount: Int
)

data class CyclePeriodData(
    val summary: CycleSummary,
    val transactions: List<TransactionEntity>,
    val preferences: UserPreferences
)

data class WidgetSummarySnapshot(
    val periodLabel: String,
    val currencySymbol: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val netDeltaCents: Long
)

class LedgerRepository(
    private val transactionDao: TransactionDao,
    private val bankRuleDao: BankRuleDao,
    private val preferencesRepository: UserPreferencesRepository,
    private var widgetRefreshDelegate: WidgetRefreshDelegate = WidgetRefreshDelegate { }
) {

    fun setWidgetRefreshDelegate(delegate: WidgetRefreshDelegate) {
        widgetRefreshDelegate = delegate
    }

    val preferencesFlow: Flow<UserPreferences> = preferencesRepository.preferencesFlow

    fun observeRules(): Flow<List<BankRule>> =
        bankRuleDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeAllDomainTransactions(): Flow<List<LedgerTransaction>> =
        transactionDao.observeAll().map { entities ->
            entities.map { entity ->
                LedgerTransaction(
                    id = entity.id,
                    amountCents = entity.amountCents,
                    type = entity.type,
                    timestampMillis = entity.timestampMillis,
                    sender = entity.sender,
                    isIgnored = entity.isIgnored
                )
            }
        }

    fun observeCurrentCycleData(): Flow<CyclePeriodData> =
        combine(
            transactionDao.observeAll(),
            preferencesRepository.preferencesFlow
        ) { allTransactions, prefs ->
            val currentYm = BillingCycleCalculator.currentCycleYearMonth(
                today = LocalDate.now(),
                cycleStartDay = prefs.cycleStartDay
            )
            buildCyclePeriodData(currentYm, allTransactions, prefs)
        }

    fun observeCycleData(yearMonth: YearMonth): Flow<CyclePeriodData> =
        combine(
            transactionDao.observeAll(),
            preferencesRepository.preferencesFlow
        ) { allTransactions, prefs ->
            buildCyclePeriodData(yearMonth, allTransactions, prefs)
        }

    fun observeHistorySummaries(): Flow<List<CycleSummary>> =
        combine(
            transactionDao.observeAll(),
            preferencesRepository.preferencesFlow
        ) { allTransactions, prefs ->
            val currentYm = BillingCycleCalculator.currentCycleYearMonth(
                today = LocalDate.now(),
                cycleStartDay = prefs.cycleStartDay
            )
            val grouped = allTransactions.groupBy { tx ->
                BillingCycleCalculator.cycleContainingTimestamp(
                    timestampMillis = tx.timestampMillis,
                    cycleStartDay = prefs.cycleStartDay
                )
            }.toMutableMap()

            if (!grouped.containsKey(currentYm)) {
                grouped[currentYm] = emptyList()
            }

            grouped.keys
                .sortedDescending()
                .map { ym ->
                    val txs = grouped[ym].orEmpty()
                    summarize(ym, prefs.cycleStartDay, txs)
                }
        }

    suspend fun getPreferencesOnce(): UserPreferences = preferencesRepository.getPreferencesOnce()

    suspend fun getEnabledBankRules(): List<BankRule> =
        bankRuleDao.getEnabledOnce().map { it.toDomain() }

    suspend fun getWidgetSnapshot(): WidgetSummarySnapshot {
        val prefs = preferencesRepository.getPreferencesOnce()
        val currentYm = BillingCycleCalculator.currentCycleYearMonth(
            today = LocalDate.now(),
            cycleStartDay = prefs.cycleStartDay
        )
        val range = BillingCycleCalculator.cycleForYearMonth(currentYm, prefs.cycleStartDay)
        val transactions = transactionDao.getInRangeOnce(range.startMillis, range.endMillis)
        val summary = summarize(currentYm, prefs.cycleStartDay, transactions)
        return WidgetSummarySnapshot(
            periodLabel = summary.label,
            currencySymbol = prefs.currencySymbol,
            incomeCents = summary.incomeCents,
            expenseCents = summary.expenseCents,
            netDeltaCents = summary.netDeltaCents
        )
    }

    suspend fun ingestParsedTransactions(
        parsed: List<ParsedSmsTransaction>,
        updateSyncTimestamp: Boolean = true
    ): Int {
        var insertedCount = 0
        if (parsed.isNotEmpty()) {
            val entities = parsed.map { item ->
                TransactionEntity(
                    amountCents = item.amountCents,
                    type = item.type,
                    timestampMillis = item.timestampMillis,
                    sender = item.sender,
                    description = item.description,
                    rawBody = item.rawBody,
                    isIgnored = false,
                    isManual = false,
                    ruleId = item.ruleId,
                    sourceHash = item.sourceHash
                )
            }
            val rowIds = transactionDao.insertAllIgnore(entities)
            insertedCount = rowIds.count { it != -1L }
        }

        if (updateSyncTimestamp) {
            preferencesRepository.setLastSyncMillis(System.currentTimeMillis())
        }

        if (insertedCount > 0) {
            widgetRefreshDelegate.refreshAllWidgets()
        }

        return insertedCount
    }

    suspend fun ingestSingleSms(
        sender: String,
        body: String,
        timestampMillis: Long
    ): Boolean {
        val prefs = preferencesRepository.getPreferencesOnce()
        val rules = getEnabledBankRules()
        val parsed = SmsParser.parse(
            sender = sender,
            body = body,
            timestampMillis = timestampMillis,
            rules = rules,
            primaryCurrencyCode = prefs.currencyCode,
            primaryCurrencySymbol = prefs.currencySymbol
        ) ?: return false

        return ingestParsedTransactions(listOf(parsed), updateSyncTimestamp = true) > 0
    }

    suspend fun addManualTransaction(
        amountCents: Long,
        type: TransactionType,
        timestampMillis: Long,
        description: String
    ) {
        if (amountCents <= 0L) return
        val cleanDescription = description.trim().ifEmpty {
            if (type == TransactionType.INCOME) "Manual income" else "Manual expense"
        }
        transactionDao.insertIgnore(
            TransactionEntity(
                amountCents = amountCents,
                type = type,
                timestampMillis = timestampMillis,
                sender = "Manual",
                description = cleanDescription,
                rawBody = null,
                isIgnored = false,
                isManual = true,
                ruleId = null,
                sourceHash = null
            )
        )
        widgetRefreshDelegate.refreshAllWidgets()
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.update(transaction)
        widgetRefreshDelegate.refreshAllWidgets()
    }

    suspend fun setTransactionIgnored(id: Long, ignored: Boolean) {
        transactionDao.setIgnored(id, ignored)
        widgetRefreshDelegate.refreshAllWidgets()
    }

    suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteById(id)
        widgetRefreshDelegate.refreshAllWidgets()
    }

    suspend fun saveRule(rule: BankRule) {
        bankRuleDao.upsert(BankRuleEntity.fromDomain(rule))
    }

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
        bankRuleDao.setEnabled(id, enabled)
    }

    suspend fun deleteRule(id: Long) {
        bankRuleDao.deleteById(id)
    }

    suspend fun setCurrency(symbol: String, code: String) {
        preferencesRepository.setCurrency(symbol, code)
        widgetRefreshDelegate.refreshAllWidgets()
    }

    suspend fun setCycleStartDay(day: Int) {
        preferencesRepository.setCycleStartDay(day)
        widgetRefreshDelegate.refreshAllWidgets()
    }

    private fun buildCyclePeriodData(
        yearMonth: YearMonth,
        allTransactions: List<TransactionEntity>,
        prefs: UserPreferences
    ): CyclePeriodData {
        val range = BillingCycleCalculator.cycleForYearMonth(yearMonth, prefs.cycleStartDay)
        val cycleTransactions = allTransactions.filter { tx ->
            tx.timestampMillis >= range.startMillis && tx.timestampMillis < range.endMillis
        }
        val summary = summarize(yearMonth, prefs.cycleStartDay, cycleTransactions)
        return CyclePeriodData(
            summary = summary,
            transactions = cycleTransactions,
            preferences = prefs
        )
    }

    private fun summarize(
        yearMonth: YearMonth,
        cycleStartDay: Int,
        transactions: List<TransactionEntity>
    ): CycleSummary {
        var incomeCents = 0L
        var expenseCents = 0L
        var includedCount = 0

        for (tx in transactions) {
            if (tx.isIgnored) continue
            includedCount++
            when (tx.type) {
                TransactionType.INCOME -> incomeCents += tx.amountCents
                TransactionType.EXPENSE -> expenseCents += tx.amountCents
            }
        }

        return CycleSummary(
            yearMonth = yearMonth,
            label = BillingCycleCalculator.formatCycleLabel(yearMonth, cycleStartDay),
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            netDeltaCents = incomeCents - expenseCents,
            includedCount = includedCount,
            totalCount = transactions.size
        )
    }
}
