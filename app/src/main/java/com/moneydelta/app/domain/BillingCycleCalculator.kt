package com.moneydelta.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CycleRange(
    val yearMonth: YearMonth,
    val startMillis: Long,
    val endMillis: Long,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate
)

object BillingCycleCalculator {

    private val calendarMonthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun clampCycleStartDay(day: Int): Int = day.coerceIn(1, 28)

    fun currentCycleYearMonth(
        today: LocalDate = LocalDate.now(),
        cycleStartDay: Int = 1
    ): YearMonth {
        val clampedDay = clampCycleStartDay(cycleStartDay)
        val currentYm = YearMonth.from(today)
        return if (clampedDay == 1 || today.dayOfMonth >= clampedDay) {
            currentYm
        } else {
            currentYm.minusMonths(1)
        }
    }

    fun cycleForYearMonth(
        yearMonth: YearMonth,
        cycleStartDay: Int = 1,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CycleRange {
        val clampedDay = clampCycleStartDay(cycleStartDay)
        val startDate = yearMonth.atDay(minOf(clampedDay, yearMonth.lengthOfMonth()))
        val nextMonth = yearMonth.plusMonths(1)
        val endDateExclusive = nextMonth.atDay(minOf(clampedDay, nextMonth.lengthOfMonth()))

        val startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()

        return CycleRange(
            yearMonth = yearMonth,
            startMillis = startMillis,
            endMillis = endMillis,
            startDate = startDate,
            endDateInclusive = endDateExclusive.minusDays(1)
        )
    }

    fun cycleContainingTimestamp(
        timestampMillis: Long,
        cycleStartDay: Int = 1,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): YearMonth {
        val date = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate()
        return currentCycleYearMonth(today = date, cycleStartDay = cycleStartDay)
    }

    fun formatCycleLabel(
        yearMonth: YearMonth,
        cycleStartDay: Int = 1
    ): String {
        val clampedDay = clampCycleStartDay(cycleStartDay)
        if (clampedDay == 1) {
            return yearMonth.format(calendarMonthFormatter)
        }
        val range = cycleForYearMonth(yearMonth, clampedDay)
        val startText = range.startDate.format(shortDateFormatter)
        val endText = range.endDateInclusive.format(shortDateFormatter)
        return "$startText – $endText"
    }
}
