package com.owlynights.scalablecapitalwidgets.widget

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Format {
    private val locale: Locale get() = Locale.getDefault()

    fun money(value: Double, currency: String = "EUR"): String =
        currencyFormat(currency).format(value)

    /** "+435,10 €" — explicit sign like the iOS widget. */
    fun signedMoney(value: Double, currency: String = "EUR"): String =
        (if (value > 0) "+" else "") + currencyFormat(currency).format(value)

    /** Like [signedMoney] but drops the cents from [wholeFrom] on, for tight summary rows. */
    fun compactSignedMoney(value: Double, currency: String = "EUR", wholeFrom: Double = 1000.0): String {
        val format = currencyFormat(currency).apply {
            if (kotlin.math.abs(value) >= wholeFrom) {
                minimumFractionDigits = 0
                maximumFractionDigits = 0
            }
        }
        return (if (value > 0) "+" else "") + format.format(value)
    }

    /** "+1,94 %" from a ratio of 0.0194. */
    fun signedPercent(ratio: Double): String {
        val format = NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return (if (ratio > 0) "+" else "") + format.format(ratio)
    }

    fun number(value: Double): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)

    fun quantity(value: Double): String = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 6
    }.format(value)

    /** Splits a price into whole and fractional digits, e.g. "346" and "45". */
    fun priceParts(value: Double): Pair<String, String> {
        val text = number(value)
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        val index = text.lastIndexOf(separator)
        return if (index < 0) text to "" else text.substring(0, index) to text.substring(index + 1)
    }

    private fun currencyFormat(currency: String) = NumberFormat.getCurrencyInstance(locale).apply {
        runCatching { this.currency = Currency.getInstance(currency) }
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
}
