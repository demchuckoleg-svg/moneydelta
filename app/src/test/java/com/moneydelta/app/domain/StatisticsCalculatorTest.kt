package com.moneydelta.app.domain

import com.moneydelta.app.domain.model.LedgerTransaction
import com.moneydelta.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatisticsCalculatorTest {

    private fun tx(
        id: Long,
        amountCents: Long,
        type: TransactionType,
        date: LocalDate,
        sender: String = "Chase",
        isIgnored: Boolean = false
    ): LedgerTransaction {
        val millis = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return LedgerTransaction(
            id = id,
            amountCents = amountCents,
            type = type,
            timestampMillis = millis,
            sender = sender,
            isIgnored = isIgnored
        )
    }

    @Test
    fun `computes averages and savings rate over active cycles excluding ignored transactions`() {
        val today = LocalDate.of(2026, 11, 10)
        val transactions = listOf(
            // Oct 2026: +2000, -1000 => net +1000
            tx(1, 200_000L, TransactionType.INCOME, LocalDate.of(2026, 10, 5)),
            tx(2, 100_000L, TransactionType.EXPENSE, LocalDate.of(2026, 10, 15)),
            // Ignored transfer should not affect stats
            tx(3, 500_000L, TransactionType.EXPENSE, LocalDate.of(2026, 10, 20), isIgnored = true),
            // Nov 2026: +4000, -2000 => net +2000
            tx(4, 400_000L, TransactionType.INCOME, LocalDate.of(2026, 11, 1)),
            tx(5, 200_000L, TransactionType.EXPENSE, LocalDate.of(2026, 11, 8))
        )

        val stats = StatisticsCalculator.compute(
            transactions = transactions,
            range = StatsRange.Preset(StatsRangePreset.THREE_MONTHS),
            senderFilter = null,
            cycleStartDay = 1,
            today = today,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(600_000L, stats.totalIncomeCents)
        assertEquals(300_000L, stats.totalExpenseCents)
        assertEquals(300_000L, stats.netDeltaCents)
        assertEquals(2, stats.activeCycleCount)
        assertEquals(300_000L, stats.avgMonthlyIncomeCents)
        assertEquals(150_000L, stats.avgMonthlyExpenseCents)
        assertEquals(150_000L, stats.avgMonthlyNetCents)
        assertEquals(50, stats.savingsRatePercent)
        assertFalse(stats.isCumulativeDailyTrend)
        assertEquals(3, stats.barGroups.size) // Sep, Oct, Nov
        assertEquals(3, stats.deltaTrendPoints.size)
    }

    @Test
    fun `this cycle uses daily cumulative trend and weekly bar buckets`() {
        val today = LocalDate.of(2026, 11, 10)
        val transactions = listOf(
            tx(1, 100_000L, TransactionType.INCOME, LocalDate.of(2026, 11, 1)),
            tx(2, 20_000L, TransactionType.EXPENSE, LocalDate.of(2026, 11, 3)),
            tx(3, 30_000L, TransactionType.EXPENSE, LocalDate.of(2026, 11, 9))
        )

        val stats = StatisticsCalculator.compute(
            transactions = transactions,
            range = StatsRange.Preset(StatsRangePreset.THIS_CYCLE),
            senderFilter = null,
            cycleStartDay = 1,
            today = today,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(stats.isCumulativeDailyTrend)
        assertEquals(10, stats.elapsedDays)
        assertEquals(5_000L, stats.dailySpendCents) // 50_000 / 10 days
        assertEquals(10, stats.deltaTrendPoints.size) // Nov 1..10
        assertEquals(100_000L, stats.deltaTrendPoints.first().valueCents)
        assertEquals(80_000L, stats.deltaTrendPoints[2].valueCents)
        assertEquals(50_000L, stats.deltaTrendPoints.last().valueCents)
        assertEquals(5, stats.barGroups.size) // 1-7, 8-14, 15-21, 22-28, 29-30
    }

    @Test
    fun `filters by sender and handles zero income savings rate safely`() {
        val today = LocalDate.of(2026, 11, 10)
        val transactions = listOf(
            tx(1, 100_000L, TransactionType.INCOME, LocalDate.of(2026, 11, 1), sender = "T-Bank"),
            tx(2, 25_000L, TransactionType.EXPENSE, LocalDate.of(2026, 11, 2), sender = "Chase")
        )

        val stats = StatisticsCalculator.compute(
            transactions = transactions,
            range = StatsRange.Preset(StatsRangePreset.THIS_CYCLE),
            senderFilter = "Chase",
            cycleStartDay = 1,
            today = today,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(0L, stats.totalIncomeCents)
        assertEquals(25_000L, stats.totalExpenseCents)
        assertEquals(-25_000L, stats.netDeltaCents)
        assertNull(stats.savingsRatePercent)
    }

    @Test
    fun `custom date range respects boundaries and handles empty data`() {
        val today = LocalDate.of(2026, 11, 24)
        val transactions = listOf(
            tx(1, 10_000L, TransactionType.EXPENSE, LocalDate.of(2026, 11, 1)),
            tx(2, 20_000L, TransactionType.EXPENSE, LocalDate.of(2026, 11, 15)),
            tx(3, 40_000L, TransactionType.EXPENSE, LocalDate.of(2026, 11, 25))
        )

        val stats = StatisticsCalculator.compute(
            transactions = transactions,
            range = StatsRange.Custom(
                startDate = LocalDate.of(2026, 11, 10),
                endDateInclusive = LocalDate.of(2026, 11, 20)
            ),
            senderFilter = null,
            cycleStartDay = 1,
            today = today,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(20_000L, stats.totalExpenseCents)
        assertEquals(1, stats.transactionCount)
        assertEquals(11, stats.elapsedDays)
        assertTrue(stats.isCumulativeDailyTrend)
    }
}
