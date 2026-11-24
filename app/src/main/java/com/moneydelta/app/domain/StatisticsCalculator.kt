package com.moneydelta.app.domain

import com.moneydelta.app.domain.model.LedgerTransaction
import com.moneydelta.app.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

enum class StatsRangePreset(val label: String) {
    THIS_CYCLE("This Cycle"),
    THREE_MONTHS("3M"),
    SIX_MONTHS("6M"),
    TWELVE_MONTHS("12M"),
    ALL("All"),
    CUSTOM("Custom")
}

sealed interface StatsRange {
    data class Preset(val preset: StatsRangePreset) : StatsRange
    data class Custom(val startDate: LocalDate, val endDateInclusive: LocalDate) : StatsRange
}

data class TrendPoint(
    val label: String,
    val valueCents: Long,
    val detailTitle: String
)

data class PeriodBarGroup(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val detailTitle: String
) {
    val netDeltaCents: Long get() = incomeCents - expenseCents
}

data class StatisticsSnapshot(
    val periodSubtitle: String,
    val totalIncomeCents: Long,
    val totalExpenseCents: Long,
    val netDeltaCents: Long,
    val avgMonthlyIncomeCents: Long,
    val avgMonthlyExpenseCents: Long,
    val avgMonthlyNetCents: Long,
    val dailySpendCents: Long,
    val savingsRatePercent: Int?,
    val activeCycleCount: Int,
    val elapsedDays: Int,
    val transactionCount: Int,
    val isCumulativeDailyTrend: Boolean,
    val deltaTrendPoints: List<TrendPoint>,
    val barGroups: List<PeriodBarGroup>
)

object StatisticsCalculator {

    private val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
    private val shortMonthYearFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.US)
    private val shortDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val dayNumberFormatter = DateTimeFormatter.ofPattern("d", Locale.US)

    fun compute(
        transactions: List<LedgerTransaction>,
        range: StatsRange,
        senderFilter: String?,
        cycleStartDay: Int,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StatisticsSnapshot {
        val clampedDay = BillingCycleCalculator.clampCycleStartDay(cycleStartDay)
        val includedAll = transactions.filter { tx ->
            !tx.isIgnored &&
                (senderFilter.isNullOrBlank() || tx.sender.equals(senderFilter, ignoreCase = true))
        }

        val currentYm = BillingCycleCalculator.currentCycleYearMonth(today, clampedDay)
        val currentCycle = BillingCycleCalculator.cycleForYearMonth(currentYm, clampedDay, zoneId)

        val window = resolveWindow(
            includedTransactions = includedAll,
            range = range,
            currentYm = currentYm,
            currentCycle = currentCycle,
            cycleStartDay = clampedDay,
            today = today,
            zoneId = zoneId
        )

        val scopedTransactions = includedAll.filter { tx ->
            tx.timestampMillis >= window.startMillis && tx.timestampMillis < window.endMillis
        }

        var totalIncomeCents = 0L
        var totalExpenseCents = 0L
        for (tx in scopedTransactions) {
            when (tx.type) {
                TransactionType.INCOME -> totalIncomeCents += tx.amountCents
                TransactionType.EXPENSE -> totalExpenseCents += tx.amountCents
            }
        }
        val netDeltaCents = totalIncomeCents - totalExpenseCents

        val activeCycleCount = scopedTransactions
            .map { tx ->
                BillingCycleCalculator.cycleContainingTimestamp(
                    timestampMillis = tx.timestampMillis,
                    cycleStartDay = clampedDay,
                    zoneId = zoneId
                )
            }
            .distinct()
            .size
            .coerceAtLeast(1)

        val avgMonthlyIncomeCents = totalIncomeCents / activeCycleCount
        val avgMonthlyExpenseCents = totalExpenseCents / activeCycleCount
        val avgMonthlyNetCents = netDeltaCents / activeCycleCount
        val dailySpendCents = totalExpenseCents / window.elapsedDays.coerceAtLeast(1)
        val savingsRatePercent = if (totalIncomeCents <= 0L) {
            null
        } else {
            ((netDeltaCents.toDouble() / totalIncomeCents.toDouble()) * 100.0).roundToInt()
        }

        val (trendPoints, barGroups) = if (window.useShortGranularity) {
            buildShortGranularityCharts(
                scopedTransactions = scopedTransactions,
                startDate = window.startDate,
                endDateInclusive = window.endDateInclusive,
                today = today,
                zoneId = zoneId
            )
        } else {
            buildCycleCharts(
                scopedTransactions = scopedTransactions,
                startYm = window.startCycleYm,
                endYm = window.endCycleYm,
                cycleStartDay = clampedDay,
                zoneId = zoneId
            )
        }

        return StatisticsSnapshot(
            periodSubtitle = window.subtitle,
            totalIncomeCents = totalIncomeCents,
            totalExpenseCents = totalExpenseCents,
            netDeltaCents = netDeltaCents,
            avgMonthlyIncomeCents = avgMonthlyIncomeCents,
            avgMonthlyExpenseCents = avgMonthlyExpenseCents,
            avgMonthlyNetCents = avgMonthlyNetCents,
            dailySpendCents = dailySpendCents,
            savingsRatePercent = savingsRatePercent,
            activeCycleCount = activeCycleCount,
            elapsedDays = window.elapsedDays,
            transactionCount = scopedTransactions.size,
            isCumulativeDailyTrend = window.useShortGranularity,
            deltaTrendPoints = trendPoints,
            barGroups = barGroups
        )
    }

    private data class ResolvedWindow(
        val startMillis: Long,
        val endMillis: Long,
        val startDate: LocalDate,
        val endDateInclusive: LocalDate,
        val startCycleYm: YearMonth,
        val endCycleYm: YearMonth,
        val elapsedDays: Int,
        val useShortGranularity: Boolean,
        val subtitle: String
    )

    private fun resolveWindow(
        includedTransactions: List<LedgerTransaction>,
        range: StatsRange,
        currentYm: YearMonth,
        currentCycle: CycleRange,
        cycleStartDay: Int,
        today: LocalDate,
        zoneId: ZoneId
    ): ResolvedWindow {
        return when (range) {
            is StatsRange.Custom -> {
                val start = minOf(range.startDate, range.endDateInclusive)
                val end = maxOf(range.startDate, range.endDateInclusive)
                val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endMillis = end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val effectiveEnd = minOf(today, end)
                val elapsedDays = if (effectiveEnd.isBefore(start)) {
                    1
                } else {
                    ChronoUnit.DAYS.between(start, effectiveEnd).toInt() + 1
                }
                val totalSpanDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
                val startCycleYm = BillingCycleCalculator.currentCycleYearMonth(start, cycleStartDay)
                val endCycleYm = BillingCycleCalculator.currentCycleYearMonth(end, cycleStartDay)
                ResolvedWindow(
                    startMillis = startMillis,
                    endMillis = endMillis,
                    startDate = start,
                    endDateInclusive = end,
                    startCycleYm = startCycleYm,
                    endCycleYm = endCycleYm,
                    elapsedDays = elapsedDays.coerceAtLeast(1),
                    useShortGranularity = totalSpanDays <= 45,
                    subtitle = "${start.format(shortDayFormatter)} – ${end.format(shortDayFormatter)}"
                )
            }

            is StatsRange.Preset -> when (range.preset) {
                StatsRangePreset.THIS_CYCLE -> {
                    val effectiveEnd = minOf(today, currentCycle.endDateInclusive)
                    val elapsed = if (effectiveEnd.isBefore(currentCycle.startDate)) {
                        1
                    } else {
                        ChronoUnit.DAYS.between(currentCycle.startDate, effectiveEnd).toInt() + 1
                    }
                    ResolvedWindow(
                        startMillis = currentCycle.startMillis,
                        endMillis = currentCycle.endMillis,
                        startDate = currentCycle.startDate,
                        endDateInclusive = currentCycle.endDateInclusive,
                        startCycleYm = currentYm,
                        endCycleYm = currentYm,
                        elapsedDays = elapsed.coerceAtLeast(1),
                        useShortGranularity = true,
                        subtitle = BillingCycleCalculator.formatCycleLabel(currentYm, cycleStartDay)
                    )
                }

                StatsRangePreset.THREE_MONTHS,
                StatsRangePreset.SIX_MONTHS,
                StatsRangePreset.TWELVE_MONTHS -> {
                    val months = when (range.preset) {
                        StatsRangePreset.THREE_MONTHS -> 3L
                        StatsRangePreset.SIX_MONTHS -> 6L
                        else -> 12L
                    }
                    val startYm = currentYm.minusMonths(months - 1L)
                    val startCycle = BillingCycleCalculator.cycleForYearMonth(startYm, cycleStartDay, zoneId)
                    val effectiveEnd = minOf(today, currentCycle.endDateInclusive)
                    val elapsed = ChronoUnit.DAYS.between(startCycle.startDate, effectiveEnd).toInt() + 1
                    ResolvedWindow(
                        startMillis = startCycle.startMillis,
                        endMillis = currentCycle.endMillis,
                        startDate = startCycle.startDate,
                        endDateInclusive = currentCycle.endDateInclusive,
                        startCycleYm = startYm,
                        endCycleYm = currentYm,
                        elapsedDays = elapsed.coerceAtLeast(1),
                        useShortGranularity = false,
                        subtitle = "Last $months cycles"
                    )
                }

                StatsRangePreset.ALL,
                StatsRangePreset.CUSTOM -> {
                    val earliestYm = includedTransactions
                        .minOfOrNull { it.timestampMillis }
                        ?.let {
                            BillingCycleCalculator.cycleContainingTimestamp(
                                timestampMillis = it,
                                cycleStartDay = cycleStartDay,
                                zoneId = zoneId
                            )
                        }
                        ?.let { minOf(it, currentYm) }
                        ?: currentYm
                    val startCycle = BillingCycleCalculator.cycleForYearMonth(earliestYm, cycleStartDay, zoneId)
                    val effectiveEnd = minOf(today, currentCycle.endDateInclusive)
                    val elapsed = ChronoUnit.DAYS.between(startCycle.startDate, effectiveEnd).toInt() + 1
                    val singleCycle = earliestYm == currentYm
                    ResolvedWindow(
                        startMillis = startCycle.startMillis,
                        endMillis = currentCycle.endMillis,
                        startDate = startCycle.startDate,
                        endDateInclusive = currentCycle.endDateInclusive,
                        startCycleYm = earliestYm,
                        endCycleYm = currentYm,
                        elapsedDays = elapsed.coerceAtLeast(1),
                        useShortGranularity = singleCycle,
                        subtitle = if (singleCycle) {
                            BillingCycleCalculator.formatCycleLabel(currentYm, cycleStartDay)
                        } else {
                            "All synced cycles"
                        }
                    )
                }
            }
        }
    }

    private fun buildShortGranularityCharts(
        scopedTransactions: List<LedgerTransaction>,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        today: LocalDate,
        zoneId: ZoneId
    ): Pair<List<TrendPoint>, List<PeriodBarGroup>> {
        val trendEnd = minOf(today, endDateInclusive).let {
            if (it.isBefore(startDate)) startDate else it
        }

        val dailyNetByDate = mutableMapOf<LocalDate, Long>()
        for (tx in scopedTransactions) {
            val date = Instant.ofEpochMilli(tx.timestampMillis).atZone(zoneId).toLocalDate()
            val signed = if (tx.type == TransactionType.INCOME) tx.amountCents else -tx.amountCents
            dailyNetByDate[date] = (dailyNetByDate[date] ?: 0L) + signed
        }

        val trendPoints = mutableListOf<TrendPoint>()
        var cumulativeCents = 0L
        var cursor = startDate
        while (!cursor.isAfter(trendEnd)) {
            cumulativeCents += dailyNetByDate[cursor] ?: 0L
            trendPoints += TrendPoint(
                label = cursor.format(dayNumberFormatter),
                valueCents = cumulativeCents,
                detailTitle = cursor.format(shortDayFormatter)
            )
            cursor = cursor.plusDays(1)
        }

        val barGroups = mutableListOf<PeriodBarGroup>()
        var bucketStart = startDate
        while (!bucketStart.isAfter(endDateInclusive)) {
            val bucketEnd = minOf(bucketStart.plusDays(6), endDateInclusive)
            val bucketStartMillis = bucketStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val bucketEndMillis = bucketEnd.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            var income = 0L
            var expense = 0L
            for (tx in scopedTransactions) {
                if (tx.timestampMillis in bucketStartMillis until bucketEndMillis) {
                    if (tx.type == TransactionType.INCOME) income += tx.amountCents
                    else expense += tx.amountCents
                }
            }

            val label = if (bucketStart == bucketEnd) {
                bucketStart.format(dayNumberFormatter)
            } else {
                "${bucketStart.dayOfMonth}–${bucketEnd.dayOfMonth}"
            }
            val detailTitle = if (bucketStart == bucketEnd) {
                bucketStart.format(shortDayFormatter)
            } else {
                "${bucketStart.format(shortDayFormatter)} – ${bucketEnd.format(shortDayFormatter)}"
            }

            barGroups += PeriodBarGroup(
                label = label,
                incomeCents = income,
                expenseCents = expense,
                detailTitle = detailTitle
            )
            bucketStart = bucketEnd.plusDays(1)
        }

        return trendPoints to barGroups
    }

    private fun buildCycleCharts(
        scopedTransactions: List<LedgerTransaction>,
        startYm: YearMonth,
        endYm: YearMonth,
        cycleStartDay: Int,
        zoneId: ZoneId
    ): Pair<List<TrendPoint>, List<PeriodBarGroup>> {
        val grouped = scopedTransactions.groupBy { tx ->
            BillingCycleCalculator.cycleContainingTimestamp(
                timestampMillis = tx.timestampMillis,
                cycleStartDay = cycleStartDay,
                zoneId = zoneId
            )
        }

        val crossesYears = startYm.year != endYm.year
        val trendPoints = mutableListOf<TrendPoint>()
        val barGroups = mutableListOf<PeriodBarGroup>()

        var ymCursor = startYm
        while (!ymCursor.isAfter(endYm)) {
            val txs = grouped[ymCursor].orEmpty()
            var income = 0L
            var expense = 0L
            for (tx in txs) {
                if (tx.type == TransactionType.INCOME) income += tx.amountCents
                else expense += tx.amountCents
            }
            val shortLabel = if (crossesYears) {
                ymCursor.format(shortMonthYearFormatter)
            } else {
                ymCursor.format(shortMonthFormatter)
            }
            val detailTitle = BillingCycleCalculator.formatCycleLabel(ymCursor, cycleStartDay)

            trendPoints += TrendPoint(
                label = shortLabel,
                valueCents = income - expense,
                detailTitle = detailTitle
            )
            barGroups += PeriodBarGroup(
                label = shortLabel,
                incomeCents = income,
                expenseCents = expense,
                detailTitle = detailTitle
            )
            ymCursor = ymCursor.plusMonths(1)
        }

        return trendPoints to barGroups
    }
}
