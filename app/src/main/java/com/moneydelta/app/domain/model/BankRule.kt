package com.moneydelta.app.domain.model

data class BankRule(
    val id: Long = 0L,
    val name: String,
    val senderPattern: String,
    val incomeKeywords: String,
    val expenseKeywords: String,
    val ignoreKeywords: String,
    val amountRegex: String = "",
    val isEnabled: Boolean = true
)
