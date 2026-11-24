package com.moneydelta.app.domain.parser

import com.moneydelta.app.domain.model.BankRule

object DefaultBankRules {

    fun starterRules(): List<BankRule> = listOf(
        BankRule(
            id = 0L,
            name = "Generic English Bank",
            senderPattern = "Bank|BANK|Card|Alert",
            incomeKeywords = "credited, received, deposit, refund, cashback, salary, incoming",
            expenseKeywords = "debited, spent, purchase, paid, withdrawn, payment, charged",
            ignoreKeywords = "otp, verification code, one-time, passcode, declined, failed, insufficient",
            amountRegex = "",
            isEnabled = false
        ),
        BankRule(
            id = 0L,
            name = "Card & Transfer Template",
            senderPattern = "T-Bank|Chase|Revolut|Monzo",
            incomeKeywords = "incoming, transfer in, credited, refund, top-up, topped up",
            expenseKeywords = "purchase, card payment, spent, transfer out, debited, atm",
            ignoreKeywords = "otp, code, declined, rejected, canceled, cancelled",
            amountRegex = "",
            isEnabled = false
        )
    )
}
