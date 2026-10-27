package com.example.paymentacademy.ui

import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.data.model.PaymentSummary
import java.time.LocalDate

data class PaymentUiState(
  val isAuthenticated: Boolean = false,
  val isLoading: Boolean = false,
  val members: List<MemberPaymentSummary> = emptyList(),
  val movements: List<PaymentSummary> = emptyList(),
  val monthlyTotal: Double = 0.0,
  val fromDate: LocalDate = currentMonthStart(),
  val toDate: LocalDate = currentMonthEnd(),
  val message: String? = null,
) {
  companion object {
    private fun currentMonthStart(): LocalDate = LocalDate.now().withDayOfMonth(1)
    private fun currentMonthEnd(): LocalDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
  }
}
