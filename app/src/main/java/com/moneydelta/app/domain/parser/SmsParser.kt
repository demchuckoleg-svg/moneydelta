package com.moneydelta.app.domain.parser

import com.moneydelta.app.domain.model.BankRule
import com.moneydelta.app.domain.model.ParsedSmsTransaction
import com.moneydelta.app.domain.model.SmsParseOutcome
import com.moneydelta.app.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

object SmsParser {

    private val numberCandidateRegex = Regex(
        """(?<![\d.,])(\d{1,3}(?:[,\u00A0\u202F ]\d{3})+(?:[.,]\d{1,2})?|\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?![\d.,])"""
    )

    private val balancePrefixRegex = Regex(
        """(?i)(?:avail(?:able)?\s*bal(?:ance)?|bal(?:ance)?|remaining|остаток|баланс)\s*[:\-]?\s*[^\d\r\n]{0,6}$"""
    )

    private val cardOrRefPrefixRegex = Regex(
        """(?i)(?:card|acct|account|ending|ref|auth|code|otp|id|no\.?|№|карта)\s*[:\-#*]?\s*$"""
    )

    private val multiCurrencyPrefixPatterns: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)\b(?:CAD|C\$)\b|C\$""") to "CAD",
        Regex("""(?i)\b(?:AUD|A\$)\b|A\$""") to "AUD",
        Regex("""(?i)\b(?:NZD|NZ\$)\b|NZ\$""") to "NZD",
        Regex("""(?i)\b(?:HKD|HK\$)\b|HK\$""") to "HKD",
        Regex("""(?i)\b(?:SGD|S\$)\b|S\$""") to "SGD",
        Regex("""(?i)\b(?:BRL|R\$)\b|R\$""") to "BRL"
    )

    private val isoCurrencyRegex = Regex(
        """(?i)(?<![A-Z])(USD|EUR|GBP|RUB|JPY|CAD|AUD|CHF|CNY|INR|UAH|KZT|TRY|PLN|SEK|NOK|DKK|BRL|MXN|KRW|SGD|HKD|NZD|ZAR|AED|SAR)(?![A-Z])"""
    )

    private val rubWordRegex = Regex("""(?i)(?<![\p{L}])(?:руб\.?|р\.?)(?![\p{L}])""")

    private val symbolToCode: Map<String, String> = mapOf(
        "$" to "USD",
        "€" to "EUR",
        "£" to "GBP",
        "₽" to "RUB",
        "₹" to "INR",
        "₴" to "UAH",
        "₸" to "KZT",
        "₺" to "TRY",
        "₪" to "ILS",
        "₩" to "KRW",
        "₫" to "VND",
        "฿" to "THB",
        "¥" to "JPY",
        "zł" to "PLN"
    )

    fun parse(
        sender: String,
        body: String,
        timestampMillis: Long,
        rules: List<BankRule>,
        primaryCurrencyCode: String,
        primaryCurrencySymbol: String
    ): ParsedSmsTransaction? {
        return when (
            val outcome = parseDetailed(
                sender = sender,
                body = body,
                timestampMillis = timestampMillis,
                rules = rules,
                primaryCurrencyCode = primaryCurrencyCode,
                primaryCurrencySymbol = primaryCurrencySymbol
            )
        ) {
            is SmsParseOutcome.Matched -> outcome.transaction
            else -> null
        }
    }

    fun parseDetailed(
        sender: String,
        body: String,
        timestampMillis: Long,
        rules: List<BankRule>,
        primaryCurrencyCode: String,
        primaryCurrencySymbol: String
    ): SmsParseOutcome {
        val enabledRules = rules.filter { it.isEnabled }
        if (enabledRules.isEmpty()) return SmsParseOutcome.NoMatchingRule

        var fallbackOutcome: SmsParseOutcome = SmsParseOutcome.NoMatchingRule

        for (rule in enabledRules) {
            if (!matchesSender(sender, rule.senderPattern)) continue

            val ignoredKeyword = findMatchingKeyword(body, rule.ignoreKeywords)
            if (ignoredKeyword != null) {
                if (fallbackOutcome is SmsParseOutcome.NoMatchingRule) {
                    fallbackOutcome = SmsParseOutcome.Ignored(rule.name, ignoredKeyword)
                }
                continue
            }

            // Check income keywords before expense keywords so refunds/reversals referencing
            // a "purchase" are classified as INCOME.
            val transactionType = when {
                findMatchingKeyword(body, rule.incomeKeywords) != null -> TransactionType.INCOME
                findMatchingKeyword(body, rule.expenseKeywords) != null -> TransactionType.EXPENSE
                else -> {
                    if (fallbackOutcome is SmsParseOutcome.NoMatchingRule) {
                        fallbackOutcome = SmsParseOutcome.NoDirectionKeyword(rule.name)
                    }
                    continue
                }
            }

            val extraction = extractAmount(body, rule.amountRegex)
            if (extraction == null || extraction.amountCents <= 0L) {
                if (fallbackOutcome is SmsParseOutcome.NoMatchingRule ||
                    fallbackOutcome is SmsParseOutcome.NoDirectionKeyword
                ) {
                    fallbackOutcome = SmsParseOutcome.NoAmountFound(rule.name)
                }
                continue
            }

            val detectedCurrency = detectAdjacentCurrency(body, extraction.startIndex, extraction.endIndex)
            if (detectedCurrency != null && !currencyMatchesPrimary(
                    detected = detectedCurrency,
                    primaryCode = primaryCurrencyCode,
                    primarySymbol = primaryCurrencySymbol
                )
            ) {
                fallbackOutcome = SmsParseOutcome.CurrencyMismatch(rule.name, detectedCurrency.displayLabel)
                continue
            }

            val description = buildDescription(sender, body)
            val sourceHash = computeSourceHash(sender, timestampMillis, body)

            return SmsParseOutcome.Matched(
                ParsedSmsTransaction(
                    amountCents = extraction.amountCents,
                    type = transactionType,
                    timestampMillis = timestampMillis,
                    sender = sender.trim(),
                    description = description,
                    rawBody = body,
                    ruleId = rule.id,
                    ruleName = rule.name,
                    sourceHash = sourceHash
                )
            )
        }

        return fallbackOutcome
    }

    internal fun matchesSender(sender: String, senderPattern: String): Boolean {
        val trimmed = senderPattern.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == "*") return true

        val normalizedSender = sender.trim()
        if (normalizedSender.isEmpty()) return false

        val tokens = if (trimmed.contains('{')) {
            listOf(trimmed)
        } else {
            trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

        return tokens.any { token ->
            val hasRegexMeta = token.any { it in "|.*+?[](){}^$\\" }
            if (hasRegexMeta) {
                try {
                    Regex(token, RegexOption.IGNORE_CASE).containsMatchIn(normalizedSender)
                } catch (_: IllegalArgumentException) {
                    normalizedSender.contains(token, ignoreCase = true)
                }
            } else {
                normalizedSender.contains(token, ignoreCase = true)
            }
        }
    }

    private fun findMatchingKeyword(body: String, rawKeywords: String): String? {
        if (rawKeywords.isBlank()) return null
        return rawKeywords.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { keyword -> body.contains(keyword, ignoreCase = true) }
    }

    private data class AmountExtraction(
        val amountCents: Long,
        val startIndex: Int,
        val endIndex: Int
    )

    private fun extractAmount(body: String, customRegex: String): AmountExtraction? {
        if (customRegex.isNotBlank()) {
            val regex = try {
                Regex(customRegex, RegexOption.IGNORE_CASE)
            } catch (_: IllegalArgumentException) {
                return null
            }
            val match = regex.find(body) ?: return null
            val targetGroup = if (match.groups.size > 1 && match.groups[1] != null) {
                match.groups[1]!!
            } else {
                match.groups[0]!!
            }
            val cents = parseAmountToCents(targetGroup.value) ?: return null
            return AmountExtraction(
                amountCents = cents,
                startIndex = targetGroup.range.first,
                endIndex = targetGroup.range.last + 1
            )
        }

        data class Candidate(
            val extraction: AmountExtraction,
            val hasCurrency: Boolean,
            val hasDecimal: Boolean,
            val isBalance: Boolean
        )

        val candidates = mutableListOf<Candidate>()
        for (match in numberCandidateRegex.findAll(body)) {
            val raw = match.groupValues[1]
            val start = match.range.first
            val end = match.range.last + 1

            // Skip card masks (*4821, #4821, x4821), time/date fragments (10:30, 11/24, 2026-11-24)
            val prevChar = if (start > 0) body[start - 1] else null
            val nextChar = if (end < body.length) body[end] else null
            val isHyphenatedBefore = prevChar == '-' && start >= 2 && body[start - 2].isDigit()
            val isHyphenatedAfter = nextChar == '-' && end + 1 < body.length && body[end + 1].isDigit()
            if (prevChar in listOf('*', '#', 'x', 'X', ':', '/') ||
                nextChar in listOf(':', '/') ||
                isHyphenatedBefore ||
                isHyphenatedAfter
            ) {
                continue
            }

            val cents = parseAmountToCents(raw) ?: continue
            if (cents <= 0L) continue

            val prefixWindow = body.substring(maxOf(0, start - 28), start)
            val hasDecimal = raw.contains('.') || raw.contains(',')
            val hasCurrency = detectAdjacentCurrency(body, start, end) != null

            if (!hasCurrency && !hasDecimal && cardOrRefPrefixRegex.containsMatchIn(prefixWindow)) {
                continue
            }

            val isBalance = balancePrefixRegex.containsMatchIn(prefixWindow)
            candidates += Candidate(
                extraction = AmountExtraction(cents, start, end),
                hasCurrency = hasCurrency,
                hasDecimal = hasDecimal,
                isBalance = isBalance
            )
        }

        if (candidates.isEmpty()) return null

        val chosen = candidates.firstOrNull { !it.isBalance && it.hasCurrency }
            ?: candidates.firstOrNull { !it.isBalance && it.hasDecimal }
            ?: candidates.firstOrNull { !it.isBalance }
            ?: candidates.first()

        return chosen.extraction
    }

    internal fun parseAmountToCents(rawInput: String): Long? {
        val cleaned = rawInput
            .replace("\u00A0", "")
            .replace("\u202F", "")
            .replace(" ", "")
            .trim()
        if (cleaned.isEmpty()) return null

        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')

        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                if (lastDot > lastComma) {
                    // US style: 1,234.56
                    cleaned.replace(",", "")
                } else {
                    // EU style: 1.234,56
                    cleaned.replace(".", "").replace(",", ".")
                }
            }
            lastComma >= 0 -> {
                val digitsAfter = cleaned.length - lastComma - 1
                val commaCount = cleaned.count { it == ',' }
                if (commaCount == 1 && digitsAfter in 1..2) {
                    cleaned.replace(",", ".")
                } else {
                    cleaned.replace(",", "")
                }
            }
            lastDot >= 0 -> {
                val digitsAfter = cleaned.length - lastDot - 1
                val dotCount = cleaned.count { it == '.' }
                if (dotCount == 1 && digitsAfter in 1..2) {
                    cleaned
                } else if (dotCount > 1 || digitsAfter == 3) {
                    cleaned.replace(".", "")
                } else {
                    cleaned
                }
            }
            else -> cleaned
        }

        return try {
            BigDecimal(normalized)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    private data class DetectedCurrency(
        val code: String?,
        val symbol: String?,
        val displayLabel: String
    )

    private fun detectAdjacentCurrency(body: String, startIndex: Int, endIndex: Int): DetectedCurrency? {
        val windowStart = maxOf(0, startIndex - 6)
        val windowEnd = minOf(body.length, endIndex + 6)
        val window = body.substring(windowStart, windowEnd)

        for ((pattern, code) in multiCurrencyPrefixPatterns) {
            if (pattern.containsMatchIn(window)) {
                return DetectedCurrency(code = code, symbol = null, displayLabel = code)
            }
        }

        isoCurrencyRegex.find(window)?.let {
            val code = it.groupValues[1].uppercase()
            return DetectedCurrency(code = code, symbol = null, displayLabel = code)
        }

        if (rubWordRegex.containsMatchIn(window)) {
            return DetectedCurrency(code = "RUB", symbol = "₽", displayLabel = "RUB")
        }

        for ((symbol, code) in symbolToCode) {
            if (window.contains(symbol)) {
                return DetectedCurrency(code = code, symbol = symbol, displayLabel = symbol)
            }
        }

        return null
    }

    private fun currencyMatchesPrimary(
        detected: DetectedCurrency,
        primaryCode: String,
        primarySymbol: String
    ): Boolean {
        val normalizedPrimaryCode = primaryCode.trim().uppercase()
        val normalizedPrimarySymbol = primarySymbol.trim()

        // If an explicit ISO code (without a generic symbol) was detected, it must match primaryCode.
        if (detected.symbol == null && detected.code != null) {
            return detected.code == normalizedPrimaryCode
        }

        // If a symbol was detected (e.g. "$", "€", "₽"):
        if (detected.symbol != null) {
            if (normalizedPrimarySymbol.isNotEmpty() && detected.symbol == normalizedPrimarySymbol) {
                return true
            }
            if (detected.code == normalizedPrimaryCode) {
                return true
            }
            if (detected.symbol == "¥" && (normalizedPrimaryCode == "JPY" || normalizedPrimaryCode == "CNY")) {
                return true
            }
        }

        return false
    }

    internal fun buildDescription(sender: String, body: String): String {
        val singleLine = body.replace(Regex("\\s+"), " ").trim()
        return if (singleLine.length <= 72) {
            singleLine
        } else {
            singleLine.take(69) + "..."
        }
    }

    fun computeSourceHash(sender: String, timestampMillis: Long, body: String): String {
        val normalizedSender = sender.trim().lowercase()
        val timestampBucket = timestampMillis / 60_000L
        val normalizedBody = body.trim().replace(Regex("\\s+"), " ").lowercase()
        val payload = "$normalizedSender|$timestampBucket|$normalizedBody"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
