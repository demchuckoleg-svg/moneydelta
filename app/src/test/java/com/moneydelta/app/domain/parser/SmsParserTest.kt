package com.moneydelta.app.domain.parser

import com.moneydelta.app.domain.model.BankRule
import com.moneydelta.app.domain.model.SmsParseOutcome
import com.moneydelta.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private val defaultRule = BankRule(
        id = 1L,
        name = "Test Bank",
        senderPattern = "Chase|T-Bank|Revolut",
        incomeKeywords = "credited, received, refund",
        expenseKeywords = "debited, spent, purchase, paid",
        ignoreKeywords = "otp, verification code, declined",
        amountRegex = "",
        isEnabled = true
    )

    @Test
    fun `parses US formatted expense and ignores balance and card last4`() {
        val result = SmsParser.parse(
            sender = "Chase",
            body = "Card *4821 purchase $1,234.56 at Whole Foods. Avail bal: $5,000.00",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "USD",
            primaryCurrencySymbol = "$"
        )

        assertNotNull(result)
        assertEquals(123456L, result!!.amountCents)
        assertEquals(TransactionType.EXPENSE, result.type)
    }

    @Test
    fun `parses European space and comma formatted amount`() {
        val result = SmsParser.parse(
            sender = "Revolut",
            body = "You spent 1 234,56 EUR at Cafe",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "EUR",
            primaryCurrencySymbol = "€"
        )

        assertNotNull(result)
        assertEquals(123456L, result!!.amountCents)
        assertEquals(TransactionType.EXPENSE, result.type)
    }

    @Test
    fun `parses integer RUB amount`() {
        val result = SmsParser.parse(
            sender = "T-Bank",
            body = "Account credited 500 RUB. Balance: 10 400 RUB",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "RUB",
            primaryCurrencySymbol = "₽"
        )

        assertNotNull(result)
        assertEquals(50000L, result!!.amountCents)
        assertEquals(TransactionType.INCOME, result.type)
    }

    @Test
    fun `classifies refund referencing purchase as income`() {
        val result = SmsParser.parse(
            sender = "Chase",
            body = "Refund for purchase at Amazon: $42.10 credited to your card",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "USD",
            primaryCurrencySymbol = "$"
        )

        assertNotNull(result)
        assertEquals(TransactionType.INCOME, result!!.type)
        assertEquals(4210L, result.amountCents)
    }

    @Test
    fun `skips ignored messages such as OTP and declined`() {
        val otpOutcome = SmsParser.parseDetailed(
            sender = "Chase",
            body = "Your OTP verification code is 482910 for purchase of $250.00",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "USD",
            primaryCurrencySymbol = "$"
        )
        assertTrue(otpOutcome is SmsParseOutcome.Ignored)

        val declined = SmsParser.parse(
            sender = "Chase",
            body = "Purchase $85.00 at Target declined",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "USD",
            primaryCurrencySymbol = "$"
        )
        assertNull(declined)
    }

    @Test
    fun `filters out non primary currency`() {
        val outcome = SmsParser.parseDetailed(
            sender = "Revolut",
            body = "Purchase €45.00 at Paris Bistro",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "USD",
            primaryCurrencySymbol = "$"
        )
        assertTrue(outcome is SmsParseOutcome.CurrencyMismatch)
    }

    @Test
    fun `rejects USD explicit code when primary is CAD with dollar symbol`() {
        val outcome = SmsParser.parseDetailed(
            sender = "Chase",
            body = "Purchase 45.00 USD at Store",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "CAD",
            primaryCurrencySymbol = "$"
        )
        assertTrue(outcome is SmsParseOutcome.CurrencyMismatch)
    }

    @Test
    fun `supports custom regex extraction group`() {
        val customRule = defaultRule.copy(
            amountRegex = """Amount:\s*([0-9.,]+)"""
        )
        val result = SmsParser.parse(
            sender = "Chase",
            body = "Order 9912 paid. Amount: 78.25 USD. Fee: 0.00",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(customRule),
            primaryCurrencyCode = "USD",
            primaryCurrencySymbol = "$"
        )

        assertNotNull(result)
        assertEquals(7825L, result!!.amountCents)
    }

    @Test
    fun `ignores ISO date numbers when extracting amount`() {
        val result = SmsParser.parse(
            sender = "Chase",
            body = "2026-11-24 Purchase paid 35 at Coffee",
            timestampMillis = 1_700_000_000_000L,
            rules = listOf(defaultRule),
            primaryCurrencyCode = "USD",
            primaryCurrencySymbol = "$"
        )

        assertNotNull(result)
        assertEquals(3500L, result!!.amountCents)
    }

    @Test
    fun `matches mixed comma and regex sender patterns`() {
        assertTrue(SmsParser.matchesSender("T-Bank", "Chase|CHASE, T-Bank"))
        assertTrue(SmsParser.matchesSender("CHASE", "Chase|CHASE, T-Bank"))
    }

    @Test
    fun `produces identical source hash for equivalent normalized payloads`() {
        val hash1 = SmsParser.computeSourceHash(
            sender = " Chase ",
            timestampMillis = 1_700_000_040_123L,
            body = "Purchase  $12.00  at Cafe"
        )
        val hash2 = SmsParser.computeSourceHash(
            sender = "chase",
            timestampMillis = 1_700_000_045_999L,
            body = "purchase $12.00 at cafe"
        )
        assertEquals(hash1, hash2)
    }
}
