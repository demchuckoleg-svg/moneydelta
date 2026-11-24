package com.moneydelta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class BillingCycleCalculatorTest {

    @Test
    fun `calendar month cycle handles leap year February`() {
        val range = BillingCycleCalculator.cycleForYearMonth(
            yearMonth = YearMonth.of(2024, 2),
            cycleStartDay = 1,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2024, 2, 1), range.startDate)
        assertEquals(LocalDate.of(2024, 2, 29), range.endDateInclusive)
        assertEquals(
            LocalDate.of(2024, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            range.endMillis
        )
    }

    @Test
    fun `custom cycle start day maps dates before and after boundary`() {
        val beforeBoundary = BillingCycleCalculator.currentCycleYearMonth(
            today = LocalDate.of(2026, 11, 24),
            cycleStartDay = 25
        )
        assertEquals(YearMonth.of(2026, 10), beforeBoundary)

        val onBoundary = BillingCycleCalculator.currentCycleYearMonth(
            today = LocalDate.of(2026, 11, 25),
            cycleStartDay = 25
        )
        assertEquals(YearMonth.of(2026, 11), onBoundary)
    }

    @Test
    fun `custom cycle range covers 25th to 24th of next month`() {
        val range = BillingCycleCalculator.cycleForYearMonth(
            yearMonth = YearMonth.of(2026, 10),
            cycleStartDay = 25,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2026, 10, 25), range.startDate)
        assertEquals(LocalDate.of(2026, 11, 24), range.endDateInclusive)
    }
}
