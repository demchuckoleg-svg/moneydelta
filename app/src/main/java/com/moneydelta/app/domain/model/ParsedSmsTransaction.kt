package com.moneydelta.app.domain.model

data class ParsedSmsTransaction(
    val amountCents: Long,
    val type: TransactionType,
    val timestampMillis: Long,
    val sender: String,
    val description: String,
    val rawBody: String,
    val ruleId: Long,
    val ruleName: String,
    val sourceHash: String
)

sealed interface SmsParseOutcome {
    data class Matched(val transaction: ParsedSmsTransaction) : SmsParseOutcome
    data class Ignored(val ruleName: String, val keyword: String) : SmsParseOutcome
    data class CurrencyMismatch(val ruleName: String, val detectedCurrency: String) : SmsParseOutcome
    data object NoMatchingRule : SmsParseOutcome
    data class NoDirectionKeyword(val ruleName: String) : SmsParseOutcome
    data class NoAmountFound(val ruleName: String) : SmsParseOutcome
}
