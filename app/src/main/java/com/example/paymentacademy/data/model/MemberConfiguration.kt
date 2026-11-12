package com.example.paymentacademy.data.model

import com.example.paymentacademy.domain.CurrencyFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberConfiguration(
  @SerialName("Id") val id: String? = null,
  @SerialName("MemberId") val memberId: String,
  @SerialName("SettingName") val settingName: String = "PlaymentSetting",
  val value: MemberTariffValue,
)

@Serializable
data class MemberTariffValue(
  val AmountMonthCordoba: Double? = null,
  val AmountMonthDollar: Double? = null,
) {
  fun monthlyTariffFor(currency: String?): Double? =
    if (CurrencyFormatter.normalizeCurrency(currency) == CurrencyFormatter.CURRENCY_USD) {
      AmountMonthDollar?.takeIf { it > 0.0 }
    } else {
      AmountMonthCordoba?.takeIf { it > 0.0 }
    }
}

