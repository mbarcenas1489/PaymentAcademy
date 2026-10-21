package com.example.paymentacademy.ui

import com.example.paymentacademy.data.model.PaymentSummary

data class PaymentUiState(
  val isAuthenticated: Boolean = false,
  val isLoading: Boolean = false,
  val payments: List<PaymentSummary> = emptyList(),
  val message: String? = null,
) {
  val totalCollected: Double get() = payments.sumOf { it.amount }
}
