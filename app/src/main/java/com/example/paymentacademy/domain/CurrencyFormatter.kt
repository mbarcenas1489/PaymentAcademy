package com.example.paymentacademy.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {
  const val CURRENCY_NIO = "NIO"
  const val CURRENCY_USD = "USD"

  private val symbols = DecimalFormatSymbols(Locale.US)

  fun normalizeCurrency(currency: String?): String =
    if (currency?.trim()?.uppercase(Locale.ROOT) == CURRENCY_USD) CURRENCY_USD else CURRENCY_NIO

  fun currencySymbol(currency: String?): String =
    if (normalizeCurrency(currency) == CURRENCY_USD) "$" else "C$"

  fun formatCurrency(amount: Double, currency: String? = CURRENCY_NIO): String {
    val formatter = DecimalFormat("#,##0.##", symbols)
    return "${currencySymbol(currency)} ${formatter.format(amount)}"
  }
}

