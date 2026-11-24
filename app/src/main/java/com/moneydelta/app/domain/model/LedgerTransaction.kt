package com.moneydelta.app.domain.model

data class LedgerTransaction(
    val id: Long,
    val amountCents: Long,
    val type: TransactionType,
    val timestampMillis: Long,
    val sender: String,
    val isIgnored: Boolean
)
