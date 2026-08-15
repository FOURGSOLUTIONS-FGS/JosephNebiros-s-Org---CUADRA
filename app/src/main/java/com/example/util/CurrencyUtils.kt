package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyUtils {
    private val symbols = DecimalFormatSymbols(Locale("es", "CO")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val copFormat = DecimalFormat("$ #,##0", symbols)
    private val copFormatWithDecimals = DecimalFormat("$ #,##0.00", symbols)

    fun format(amount: Double, includeDecimals: Boolean = false): String {
        return if (includeDecimals && amount % 1.0 != 0.0) {
            copFormatWithDecimals.format(amount)
        } else {
            copFormat.format(amount)
        }
    }

    fun formatNumber(amount: Double): String {
        val formatOnlyNumbers = DecimalFormat("#,##0", symbols)
        return formatOnlyNumbers.format(amount)
    }
}
