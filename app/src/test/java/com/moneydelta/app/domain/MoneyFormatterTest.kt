package com.moneydelta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `formats positive negative and zero signed deltas`() {
        assertEquals("+$1,420.50", MoneyFormatter.formatSigned(142050L, "$"))
        assertEquals("-$310.00", MoneyFormatter.formatSigned(-31000L, "$"))
        assertEquals("$0.00", MoneyFormatter.formatSigned(0L, "$"))
    }

    @Test
    fun `formats unsigned values cleanly`() {
        assertEquals("€3,200.05", MoneyFormatter.formatUnsigned(320005L, "€"))
        assertEquals("₽500.00", MoneyFormatter.formatUnsigned(50000L, "₽"))
    }
}
