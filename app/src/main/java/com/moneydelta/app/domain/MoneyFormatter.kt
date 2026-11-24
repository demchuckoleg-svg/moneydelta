package com.moneydelta.app.domain

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormatter {

    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    fun formatUnsigned(amountCents: Long, currencySymbol: String): String {
        val absDecimal = BigDecimal.valueOf(amountCents).abs().movePointLeft(2)
        val formatter = DecimalFormat("#,##0.00", symbols)
        val numberStr = formatter.format(absDecimal)
        return "${currencySymbol.trim()}$numberStr"
    }

    fun formatSigned(amountCents: Long, currencySymbol: String): String {
        val formatted = formatUnsigned(amountCents, currencySymbol)
        return when {
            amountCents > 0L -> "+$formatted"
            amountCents < 0L -> "-$formatted"
            else -> formatted
        }
    }
}
